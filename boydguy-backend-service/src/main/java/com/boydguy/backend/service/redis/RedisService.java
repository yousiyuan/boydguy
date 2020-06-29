package com.boydguy.backend.service.redis;

import com.boydguy.backend.pojo.Product;
import com.boydguy.generate.annotation.RedisTransaction;
import com.boydguy.generate.utils.RedisUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    @RedisTransaction
    public void cacheProductData() {
        Product product = new Product();
        product.setCategoryId(123);
        product.setDiscontinued((byte) 1);
        product.setProductId(90);
        product.setProductName("普数贷");
        product.setQuantityPerUnit("家");
        product.setReorderLevel(2);
        product.setSupplierId(336);
        product.setUnitPrice(50000000D);
        product.setUnitsInStock(999999999);
        product.setUnitsOnOrder(21);

        Map<String, Object> map = new HashMap<>();
        Field[] declaredFields = Product.class.getDeclaredFields();
        for (Field field : declaredFields) {
            field.setAccessible(true);
            Object fieldValue = null;
            try {
                fieldValue = field.get(product);
            } catch (IllegalAccessException ignored) {
            }
            map.put(field.getName(), fieldValue);
        }
        RedisUtils.putValue("glp-product", product);

        RedisUtils.putHash("glp-product2", map);

        RedisUtils.setExpire(60, "glp-product", "glp-product2");

        //throw new RuntimeException("模拟程序异常Redis缓存失败");
    }

    @RedisTransaction
    public void cacheCustomerData() {
        RedisUtils.putZSet("stu001", 89, 3);
        RedisUtils.setExpire(60, "stu001");
    }

    /**
     * 通过SessionCallback执行Redis事务
     */
    @SuppressWarnings("unchecked")
    public void redisDistributedCluster() {
        RedisUtils.executeTransaction(redisOperations -> {
            redisOperations.opsForZSet().add("stu001", String.valueOf(89), 3);
            redisOperations.opsForZSet().add("stu002", String.valueOf(95), 1);
            redisOperations.opsForZSet().add("stu003", String.valueOf(93), 2);
            redisOperations.opsForValue().set("stu004", String.valueOf(86));
            Arrays.asList("stu001", "stu002", "stu003", "stu004").forEach(key -> redisOperations.expire(key, 30, TimeUnit.SECONDS));
            //throw new RuntimeException("模拟程序异常Redis缓存失败");
        });
    }

}
