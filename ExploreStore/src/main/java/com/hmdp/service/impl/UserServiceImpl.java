package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.service.LocalCacheService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
//import net.bytebuddy.asm.Advice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private LocalCacheService localCacheService;

    @Override
    public Result send(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4.保存验证码到session
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.HOURS);
        // 5.发送验证码
        log.debug("发送验证码成功，验证码{}", code);
        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 3.从redis获取校验验证码
        Object cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cachecode == null || !cachecode.equals(code)) {
            // 3.不一致，报错
            return Result.fail("验证码错误");
        }

        // 4.一致，数据库查询用户
        User user = query().eq("phone", phone).one();
        // 5.判断数据库数据是否存在
        if (user == null) {
            // 6.不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }
        // 7.存在，登入，并保存用户进入session
        // 7.1生成token
        String token = UUID.randomUUID().toString(true);
        // 7.2将User转化为HASHMAP
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        // 7.4设置有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_CODE_TTL, TimeUnit.HOURS);
        // 8.返回token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // 获取用户信息
        Long userId = UserHolder.getUser().getId();
        // 获取日期
        LocalDateTime now = LocalDateTime.now();
        // 封装成key
        String key = USER_SIGN_KEY + userId + now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // 获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        // 写入redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 获取用户信息
        Long userId = UserHolder.getUser().getId();
        // 获取日期
        LocalDateTime now = LocalDateTime.now();
        // 封装成key
        String key = USER_SIGN_KEY + userId + now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // 获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        // 获取今天为止所有签到记录,返回10进制数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)

        );
        if (result.isEmpty() || result == null) {
            return Result.ok(Collections.emptyList());
        }
        // 获取到10进制数
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(Collections.emptyList());
        }
        int count = 0;
        while (true) {

            // 循环遍历，与1与运算，得到最后一个bit位
            if ((num & 1) == 0) {
                // 若为0，为签到，结束
                break;
            } else {
                // 不为0，已签到，计数器+1总数字右移一位，抛弃最后一个bit位，继续下一个bit位
                count++;
                // 抛弃最后一个bit位,把数字负值给num
                num >>>= 1;
            }
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        // 保存到本地缓存
        localCacheService.put("user:" + user.getId(), user);
        return user;
    }

    public User getUserByIdFromCache(Long id) {
        User user = localCacheService.get("user:" + id, User.class);
        if (user == null) {
            user = getById(id);
            if (user != null) {
                localCacheService.put("user:" + id, user);
            }
        }
        return user;
    }
}
