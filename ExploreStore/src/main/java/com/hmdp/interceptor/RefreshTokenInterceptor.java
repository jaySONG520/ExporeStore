package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

//用来刷新token
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {

            return true;
        }
        String key =LOGIN_USER_KEY + token;
        //2.获取redis中的token中的user
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        //3.判断存在
        if (userMap.isEmpty()) {

            return true;
        }
        //5.将HASH数据转换位UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //6.存在保存用户到threadlocal
        UserHolder.saveUser((UserDTO) userDTO);
        //7.刷新有效期token
        stringRedisTemplate.expire(key,LOGIN_USER_TTL, TimeUnit.HOURS);
        //8.放行
        return true;
    }

}






















