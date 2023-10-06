package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void contextLoads() throws InterruptedException {
        System.out.println("hello");
    }

    @Test
    void loadShopData() {
        //查询店铺的消息
        List<Shop> list = shopService.list();
        //把店铺分组,按照typeId分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeID = entry.getKey();
            String key = SHOP_GEO_KEY + typeID;
            List<Shop> value = entry.getValue();
            //获取同类型的店铺集合
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());

            //写入到Redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())));

            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    void Hyloglog() {
        /**
         * 测试百万数据的统计
         */
        String[] values = new String[1000];

        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999) {
                //发送到redis

                stringRedisTemplate.opsForHyperLogLog().add("A1"
                        , values);
            }
        }
        //统计数量
        Long count = stringRedisTemplate.opsForHyperLogLog().size("A1");
        System.out.println("count=" + count);
    }
}



