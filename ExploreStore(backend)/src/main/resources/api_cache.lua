local redis = require "resty.redis"
local cjson = require "cjson.safe"

-- 1. 本地缓存查找
local cache = ngx.shared.local_cache
local cache_key = ngx.var.request_uri
local local_data = cache:get(cache_key)
if local_data then
    ngx.say(local_data)
    return
end

-- 2. Redis查找
local red = redis:new()
red:set_timeout(1000)
local ok, err = red:connect("127.0.0.1", 6379)
if not ok then
    ngx.log(ngx.ERR, "failed to connect to redis: ", err)
else
    local redis_data, err = red:get(cache_key)
    if redis_data and redis_data ~= ngx.null and redis_data ~= "null" then
        -- 写入本地缓存
        cache:set(cache_key, redis_data, 60)  -- 本地缓存60秒
        ngx.say(redis_data)
        return
    end
end

-- 3. 反向代理到Tomcat
local res = ngx.location.capture("/tomcat" .. cache_key)
if res.status == 200 then
    -- 写入本地缓存和Redis
    cache:set(cache_key, res.body, 60)
    if red then
        red:set(cache_key, res.body)
        red:expire(cache_key, 300)  -- Redis缓存5分钟
    end
    ngx.say(res.body)
    return
else
    ngx.status = 502
    ngx.say(cjson.encode({code=502, msg="Tomcat未启动或无响应"}))
    return
end 