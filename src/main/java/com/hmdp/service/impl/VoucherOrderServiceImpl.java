package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.RedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Autowired
    private VoucherOrderServiceImpl selfProxy;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀优惠券
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询优惠券秒杀时间是否开始
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }

        // 查询优惠券的库存
        if (seckillVoucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }

        Long userId = UserHolder.getUser().getId();
        // 创建锁
        RedisLock redisLock = new RedisLock(stringRedisTemplate, "order:" + userId);
        // 获得锁
        boolean success = redisLock.lock(1200);
        if (!success) {
            // 这里不需要再去重试获得锁，只要锁有别的线程获取，说明就已有线程将执行够买，可以提前返回
            return Result.fail("您已经购买过此优惠券");
        }
        // 获得锁成功的逻辑
        try{
            return selfProxy.createVoucherOrder(voucherId);
        }finally {
            redisLock.unlock(); // 最后一定会执行finally中的逻辑
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 查询用户是否以及购买过此优惠券
        Long userId = UserHolder.getUser().getId();
        VoucherOrder voucherOrder = query().eq("user_id", userId).eq("voucher_id", voucherId).one();
        if (voucherOrder != null) {
            return Result.fail("您已经购买过此优惠券！");
        }

        // 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock= stock -1")
                .eq("voucher_id", voucherId).gt("stock",0).update();
        if (!success) {
            //扣减库存
            return Result.fail("库存不足！");
        }

        // 创建订单
        voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);

    }
}
