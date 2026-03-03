package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LOGIC_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        while (true) {
            // 读取缓存，命中直接返回
            String shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson)) {
                Shop shop = JSONUtil.toBean(shopJson, Shop.class);
                return Result.ok(shop);
            }
            // 命中空值占位（防穿透）
            if (shopJson != null) {
                return Result.ok();
            }

            // 缓存未命中，尝试获取互斥锁
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Result.fail("查询繁忙，请稍后重试");
                }
                continue;
            }

            try {
                // Double Check：拿到锁后再次读缓存
                shopJson = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(shopJson)) {
                    Shop shop = JSONUtil.toBean(shopJson, Shop.class);
                    return Result.ok(shop);
                }
                if (shopJson != null) {
                    return Result.ok();
                }

                // 回源数据库并回填缓存
                Shop shop = getById(id);
                if (shop == null) {
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return Result.ok();
                }
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return Result.ok(shop);
            } finally {
                unlock(lockKey);
            }
        }
    }

    @Override
    public Result queryByIdWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_LOGIC_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        while (true) {
            String cacheJson = stringRedisTemplate.opsForValue().get(key);
            // 数据为空时分别判定
            if (StrUtil.isBlank(cacheJson)) {
                // 数据为空字符串占位
                if (cacheJson != null) {
                    return Result.ok();
                }
                // 冷启动未命中：抢锁后回源并回填，避免并发全部打到DB
                boolean isLock = tryLock(lockKey);
                if (!isLock) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return Result.fail("查询繁忙，请稍后重试");
                    }
                    //尝试重新获取锁
                    continue;
                }
                try {
                    //二次查询验证
                    cacheJson = stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(cacheJson)) {
                        RedisData redisData = JSONUtil.toBean(cacheJson, RedisData.class);
                        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
                        return Result.ok(shop);
                    }
                    if (cacheJson != null) {
                        return Result.ok();
                    }
                    Shop shop = getById(id);
                    if (shop == null) {
                        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                        return Result.ok();
                    }
                    saveShop2RedisWithLogicalExpire(id, CACHE_SHOP_TTL);
                    return Result.ok(shop);
                } finally {
                    unlock(lockKey);
                }
            }

            RedisData redisData = JSONUtil.toBean(cacheJson, RedisData.class);
            if (redisData == null || redisData.getData() == null || redisData.getExpireTime() == null) {
                stringRedisTemplate.delete(key);
                continue;
            }
            Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
            LocalDateTime expireTime = redisData.getExpireTime();
            // 数据逻辑时效性没过期，直接返回
            if (expireTime.isAfter(LocalDateTime.now())) {
                return Result.ok(shop);
            }
            // 数据逻辑过期，抢锁成功则提交异步重建任务
            boolean isLock = tryLock(lockKey);
            if (isLock) {
                log.debug("进入独立线程");
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        saveShop2RedisWithLogicalExpire(id, CACHE_SHOP_TTL);
                    } finally {
                        unlock(lockKey);
                    }
                });
            }
            // 抢锁失败，直接返回过期数据
            return Result.ok(shop);
        }
    }

    private void saveShop2RedisWithLogicalExpire(Long id, Long expireMinutes) {
        Shop shop = getById(id);
        String key = CACHE_SHOP_LOGIC_KEY + id;
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return;
        }
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(expireMinutes));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    private boolean tryLock(String key) {
        Boolean isSuccess = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(isSuccess);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        stringRedisTemplate.delete(CACHE_SHOP_LOGIC_KEY + id);
        return Result.ok();
    }
}
