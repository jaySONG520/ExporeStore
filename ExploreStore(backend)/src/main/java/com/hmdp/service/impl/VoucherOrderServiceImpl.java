package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    public static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //创建线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    //创建线程任务
    private class VoucherOrderHandler implements Runnable {
        String queueName ="stream.orders";
        @Override
        public void run() {
        while (true) {
            try {
                //获取消息队列中订单信息 Xreadgroup group g1 c1 count 1 block 2000 streams streams.order
                //查看pengding-list的消息
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(queueName, ReadOffset.lastConsumed())
                );
                //判断消息获取是否成功
                if (list == null||list.isEmpty()) {
                    //获取失败，没有消息，继续循环
                   Thread.sleep(200);
                    continue;
                }
                //解析消息（从list里面取出订单消息）
                MapRecord<String, Object, Object> record = list.get(0);
                Map<Object, Object> values = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                //获取成功，下单
                handleVoucherOrder(voucherOrder);
                //ACK确认 SACK stream.order g1 id
                stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
            } catch (Exception e) {
                log.error("处理订单异常",e);
                handlePendList();
            }
        }
        }

        private void handlePendList() {
            while (true) {
                try {
                    //获取pending-list中订单信息 Xreadgroup group g1 c1 count 1 STREAMS streams.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //判断消pengding-list是否有消息
                    if (list == null || list.isEmpty()) {
                        //获取失败，没有消息， 结束循环
                        Thread.sleep(200);
                        break;
                    }
                    //解析消息（从list里面取出订单消息）
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //获取成功，下单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认 SACK stream.order g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理pending-list异常", e);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ex) {
                       Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
//    //创建线程任务
//    private class VoucherOrderHandler implements Runnable {
//
//        @Override
//        public void run() {
//        while (true) {
//            //获取队列中订单信息
//            try {
//                VoucherOrder voucherOrder= orderTasks.take();
//                handleVoucherOrder(voucherOrder);
//            } catch (Exception e) {
//                log.error("处理订单异常",e);
//            }
//        }
//        }
//    }
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否锁成功
        if (!isLock) {
          //获取锁失败，返回错误或重试
            log.error("不允许重复下单");
          return ;
        }
        try {

            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
    }
    private IVoucherOrderService proxy;
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //2.订单id
        Long orderId = redisIdWorker.nextId("order");
        //1。执行lua
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),String.valueOf(orderId)
        );
        //2.判断结果是否为0
        int r = result.intValue();
        if (r != 0) {
            //2.1不为0没资格
        return Result.fail(r==1?"库存不足":"不能重复下单");
        }
         proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok();
    }
//    Long userId = UserHolder.getUser().getId();
    //        //1。执行lua
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString()
//        );
//        //2.判断结果是否为0
//        int r = result.intValue();
//        if (r != 0) {
//            //2.1不为0没资格
//        return Result.fail(r==1?"库存不足":"不能重复下单");
//        }
//
//        //2.2为0，有资格，把下单信息保存到阻塞队列
//        //创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //2.订单id
//        Long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //1.用户id
//        voucherOrder.setUserId(userId);
//        //3.优惠卷id
//        voucherOrder.setVoucherId(voucherId);
//        //创建阻塞队列
//        orderTasks.add(voucherOrder);
//        //获取代理对象
//         proxy = (IVoucherOrderService) AopContext.currentProxy();
//        return Result.ok();
    @Transactional(rollbackFor = Exception.class)
    public  void  createVoucherOrder(VoucherOrder voucherOrder) {
        //一人一单
        Long userId=voucherOrder.getUserId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count>0){
            log.error("该用户已经购买过一次");
        }

        //扣减库存(利用CAS防止超卖)
        boolean success = seckillVoucherService.update().setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId()).
                gt("stock",0).update();
        if (!success) {
            log.error("库存不足");
            return ;
        }
//    //创建订单
//    VoucherOrder voucherOrder = new VoucherOrder();
//    //2.订单id
//    Long orderId = redisIdWorker.nextId("order");
//    voucherOrder.setId(orderId);
//
//    //1.用户id
//    voucherOrder.setUserId(userId);
//
//    //3.优惠卷id
//    voucherOrder.setVoucherId(voucherOrder);
        save(voucherOrder);

//    //返回订单id
//    return Result.ok(orderId);
    }
}




//    public Result seckillVoucher(Long voucherId) {
//        //查询用户id
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //判断秒杀是否开始和结束
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//        //判断库存是否充足
//        if (voucher.getStock()<1) {
//            return Result.fail("库存不足!");
//        }
//        Long userId=UserHolder.getUser().getId();
//        //创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //获取锁
//        boolean isLock = lock.tryLock();
//        //判断是否锁成功
//        if (!isLock) {
//          //获取锁失败，返回错误或重试
//          return Result.fail("不允许重复下单");
//        }
//        try {
//            //获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            //释放锁
//            lock.unlock();
//        }
//    }
//



































