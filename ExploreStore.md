# Dianping+ 高并发本地生活服务平台

🚀 一个支持高并发、具备完整业务闭环的本地生活服务系统，涵盖用户登录、商铺浏览、探店笔记、优惠券秒杀等核心模块，并通过多级缓存与实时同步机制实现高性能和高可用性。

---

## 📌 项目简介

**Dianping+** 是一个面向高并发场景构建的本地生活平台系统。项目聚焦于性能优化与系统稳定性，采用 Nginx + Caffeine + Redis 多级缓存架构，结合 Canal 实现数据库变更的准实时同步，并通过异步线程池与分布式锁支撑秒杀场景下的并发流量，有效缓解系统压力，提升用户体验。

---

## 🧩 项目功能

- 用户注册、登录、短信验证码
- 用户签到、关注/粉丝、共同关注查询
- 商铺管理（按分类、地理位置、关键词查询）
- 探店笔记发布、评论、点赞与热门推荐
- 优惠券领取、限时秒杀、下单、支付
- 文件上传、封面管理
- 点赞用户列表与个人中心
- 多级缓存 + 缓存预热 + 降级容灾

---

## ⚙️ 技术架构

| 模块       | 技术栈 |
|------------|--------|
| 后端       | Spring Boot、MyBatis-Plus、Spring MVC |
| 缓存       | Redis、Caffeine、Nginx/OpenResty + Lua |
| 中间件     | Canal、Redisson |
| 数据库     | MySQL |
| 工具与框架 | Lombok、Hutool、Maven、Git |
| 部署环境   | Linux、Nginx |

---

## 🧠 核心亮点

### ✅ 多级缓存架构
- 本地 JVM 缓存：Caffeine，支持自动淘汰策略
- 分布式缓存：Redis，支持热点数据和逻辑过期
- 前置缓存：Nginx + Lua，实现后端宕机降级容灾
- 缓存预热机制：ApplicationRunner 启动时加载热点数据
- 缓存一致性：Canal 监听 binlog 实时同步数据变更

### ⚡ 高并发秒杀设计
- Redis 原子扣减库存，防止超卖
- Redisson 分布式锁控制一人一单
- 使用阻塞队列 + 异步线程池实现削峰填谷
- 异步日志与统一异常处理，保障接口稳定

---

## 📊 项目效果图

>（可选，添加系统截图或动图展示效果）
> 
> ![首页](https://your-image-link.com/index.png)
> ![秒杀模块](https://your-image-link.com/seckill.png)

---

## 🛠 使用方式

### 环境要求

- JDK 1.8+
- Maven 3.6+
- MySQL 5.7+
- Redis 6.0+
- Canal Server
- Nginx / OpenResty

### 启动步骤

1. 克隆仓库：
   ```bash
   git clone https://github.com/你的用户名/dianping-plus.git
