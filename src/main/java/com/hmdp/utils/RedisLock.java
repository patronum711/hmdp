package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RedisLock implements Ilock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PRE = "lock:";
    private static final String VALUE_PRE = UUID.randomUUID().toString();
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    public RedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    /**
     * 获得锁
     * @param timeOutSec
     * @return
     */
    @Override
    public boolean lock(long timeOutSec) {
        String key = KEY_PRE + name;
        String threadId = String.valueOf(Thread.currentThread().getId());
        String value = VALUE_PRE + threadId;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, timeOutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PRE + name),
                VALUE_PRE + Thread.currentThread().getId());
    }
}
