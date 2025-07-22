package com.hmdp.config;

import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.entity.Blog;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.service.impl.BlogServiceImpl;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class CachePreheatRunner {
    @Bean
    public ApplicationRunner preheatCache(ShopServiceImpl shopService, UserServiceImpl userService,
            BlogServiceImpl blogService) {
        return args -> {
            // 预热热门店铺
            List<Shop> hotShops = shopService.query().orderByDesc("sold").last("limit 10").list();
            for (Shop shop : hotShops) {
                shopService.getShopByIdFromCache(shop.getId());
            }
            // 预热热门用户
            List<User> hotUsers = userService.query().orderByDesc("id").last("limit 10").list();
            for (User user : hotUsers) {
                userService.getUserByIdFromCache(user.getId());
            }
            // 预热热门博客
            List<Blog> hotBlogs = blogService.query().orderByDesc("liked").last("limit 10").list();
            for (Blog blog : hotBlogs) {
                blogService.getBlogByIdFromCache(blog.getId());
            }
        };
    }
}