package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private IShopService shopService;

    @Autowired
    private IShopTypeService shopTypeService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisIdWorker redisIdWorker;

    private Long usedShopId;
    private Long usedMissingShopId;

    @AfterEach
    void cleanup() {
        if (usedShopId != null) {
            stringRedisTemplate.delete(CACHE_SHOP_KEY + usedShopId);
        }
        if (usedMissingShopId != null) {
            stringRedisTemplate.delete(CACHE_SHOP_KEY + usedMissingShopId);
        }
        stringRedisTemplate.delete(CACHE_SHOP_TYPE_KEY);
    }

    @Test
    void queryById_firstCall_loadsDb_andSecondCall_hitsCache() {
        Shop existingShop = shopService.getOne(new QueryWrapper<Shop>().last("limit 1"));
        Assertions.assertNotNull(existingShop, "数据库没有店铺数据，无法执行该集成测试");
        usedShopId = existingShop.getId();
        String key = CACHE_SHOP_KEY + usedShopId;

        stringRedisTemplate.delete(key);

        Result firstResult = shopService.queryById(usedShopId);
        Assertions.assertTrue(firstResult.getSuccess());
        Assertions.assertTrue(firstResult.getData() instanceof Shop);
        String cachedJson = stringRedisTemplate.opsForValue().get(key);
        Assertions.assertNotNull(cachedJson);
        Assertions.assertFalse(cachedJson.isEmpty());

        Shop fakeShop = new Shop();
        fakeShop.setId(usedShopId);
        fakeShop.setName("cache-hit-shop");
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(fakeShop));

        Result secondResult = shopService.queryById(usedShopId);
        Assertions.assertTrue(secondResult.getSuccess());
        Assertions.assertTrue(secondResult.getData() instanceof Shop);
        Shop secondShop = (Shop) secondResult.getData();
        Assertions.assertEquals("cache-hit-shop", secondShop.getName());
    }

    @Test
    void queryById_nonExistingId_writesEmptyPlaceholder() {
        Shop latestShop = shopService.getOne(new QueryWrapper<Shop>().orderByDesc("id").last("limit 1"));
        long missingId = latestShop == null ? 1_000_000L : latestShop.getId() + 1_000_000L;
        usedMissingShopId = missingId;
        String key = CACHE_SHOP_KEY + missingId;

        stringRedisTemplate.delete(key);

        Result firstResult = shopService.queryById(missingId);
        Assertions.assertTrue(firstResult.getSuccess());
        Assertions.assertNull(firstResult.getData());
        String placeholder = stringRedisTemplate.opsForValue().get(key);
        Assertions.assertEquals("", placeholder);

        Result secondResult = shopService.queryById(missingId);
        Assertions.assertTrue(secondResult.getSuccess());
        Assertions.assertNull(secondResult.getData());
    }

    @Test
    void queryTypeList_firstCall_loadsDb_andSecondCall_hitsCache() {
        stringRedisTemplate.delete(CACHE_SHOP_TYPE_KEY);

        Result firstResult = shopTypeService.queryTypeList();
        Assertions.assertTrue(firstResult.getSuccess());
        Assertions.assertTrue(firstResult.getData() instanceof List);
        String cachedJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        Assertions.assertNotNull(cachedJson);
        Assertions.assertFalse(cachedJson.isEmpty());

        ShopType fakeType = new ShopType();
        fakeType.setId(-1L);
        fakeType.setName("cache-hit-type");
        fakeType.setSort(1);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(Collections.singletonList(fakeType)));

        Result secondResult = shopTypeService.queryTypeList();
        Assertions.assertTrue(secondResult.getSuccess());
        Assertions.assertTrue(secondResult.getData() instanceof List);
        List<?> secondList = (List<?>) secondResult.getData();
        Assertions.assertFalse(secondList.isEmpty());
        Assertions.assertTrue(secondList.get(0) instanceof ShopType);
        ShopType cachedType = (ShopType) secondList.get(0);
        Assertions.assertEquals("cache-hit-type", cachedType.getName());
    }

    @Test
    void testNextIdUnique() {
        Set<Long> idSet = new HashSet<>();

        for (int i = 0; i < 100000; i++) {
            long id = redisIdWorker.nextId("order");
            //System.out.println(id);
            idSet.add(id);
        }

        // 校验是否有重复
        Assertions.assertEquals(100000, idSet.size());
    }

    @Test
    void testIdIncrease() {
        long prev = redisIdWorker.nextId("order");

        for (int i = 0; i < 1000000; i++) {
            long current = redisIdWorker.nextId("order");
            Assertions.assertTrue(current > prev);
            prev = current;
        }
    }

    @Test
    void loadShopDataToRedis() {
        List<Shop> shops = shopService.list();
        Assertions.assertNotNull(shops);

        Map<Long, List<RedisGeoCommands.GeoLocation<String>>> typeMap = new HashMap<>();
        for (Shop shop : shops) {
            if (shop.getTypeId() == null || shop.getX() == null || shop.getY() == null || shop.getId() == null) {
                continue;
            }
            typeMap.computeIfAbsent(shop.getTypeId(), k -> new ArrayList<>())
                    .add(new RedisGeoCommands.GeoLocation<>(
                            shop.getId().toString(),
                            new Point(shop.getX(), shop.getY())
                    ));
        }

        typeMap.forEach((typeId, locations) ->
                stringRedisTemplate.opsForGeo().add(SHOP_GEO_KEY + typeId, locations)
        );
    }

}
