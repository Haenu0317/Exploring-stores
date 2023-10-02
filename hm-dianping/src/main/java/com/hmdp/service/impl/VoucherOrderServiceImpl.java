package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.RedissonConfig;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_HANDLE_EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public Result seckillVoucher(Long voucherId) {

        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString());
        //判断结构是为否0
        if (result != 0) {
            //2.1不为0,又分两种情况
            return Result.fail(result==1 ? "优惠券已售罄":"请勿重复下单");
        }

        //2.2为0，有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        //生成订单信息
        long orderId = redisIdWorker.nextId("order");
        //TODO 保存到阻塞队列
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        orderTasks.add(voucherOrder);



        //返回订单消息
        return Result.ok(0);












        //SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
        //if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
        //    return Result.fail("秒杀尚未开始!");
        //}
        //
        //if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
        //    return Result.fail("秒杀已经结束!");
        //}
        //
        //if (seckillVoucher.getStock() < 1) {
        //    return Result.fail("库存不足!");
        //}
        //
        //Long userId = UserHolder.getUser().getId();
        //
        ////创建锁对象
        //
        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock("lock:" + userId, stringRedisTemplate);
        //
        //RLock lock = redissonClient.getLock("order" + userId);
        //boolean isLock = lock.tryLock();
        //
        //if (!isLock){
        //    return Result.fail("禁止重复下单!");
        //}
        //
        //try {
        //    /*我们是对当前的createVoucherOrder加上了事务,并没有对seckillVoucher加事务如果是用this调用createVoucherOrder，
        //    是用的当前这个VoucherOrderServicelmp这个对象进行调用,事务的生效是应为spring对当前的这个类做了动态代理，拿到的是他的代理对象然后对他进行事务处理，但是他本身是没有事务功能的
        //    所以我们要拿到他的代理对象使用一个API--->AopContext拿到当前的代理对象,这样事务才会生效
        //    知识点
        //    aop代理对象，事务失效，synchronized
        //    */
        //    IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        //    return proxy.createVoucherOrder(voucherId);
        //} finally {
        //    simpleRedisLock.unLock();
        //}


    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("该用户已经购买过一次!");
        }
        boolean success = iSeckillVoucherService
                .update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!success) {
            return Result.fail("库存不足!");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);


        return Result.ok(orderId);
    }

}
