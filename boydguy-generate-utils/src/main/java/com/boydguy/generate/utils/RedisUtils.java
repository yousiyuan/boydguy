package com.boydguy.generate.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.*;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RedisUtils {

    private static RedisTemplate<Object, Object> redisTemplate;
    /**
     * 使用RedisTemplate存放的对象(Object)在Redis中为非文本形式的二进制数据，读取到的仍为Object对象。
     * 通常习惯将对象序列化为json存放到redis中，所以StringRedisTemplate更常用。
     */
    private static StringRedisTemplate stringRedisTemplate;
    private static ObjectMapper jacksonMapper;
    /**
     * 通过setnx命令在redis中存放的全局唯一的锁名称
     */
    private static final String REDIS_LOCK = "GLOBAL_LOCK";

    @Autowired
    public RedisUtils(RedisTemplate<Object, Object> redisTemplate,
                      StringRedisTemplate stringRedisTemplate,
                      ObjectMapper objectMapper) {
        RedisUtils.redisTemplate = redisTemplate;
        RedisUtils.stringRedisTemplate = stringRedisTemplate;
        jacksonMapper = objectMapper.copy();
        jacksonMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        jacksonMapper.setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
    }

    /**
     * 启用Redis事务权限
     */
    public static void enableTransactionSupport() {
        stringRedisTemplate.setEnableTransactionSupport(true);
    }

    /**
     * 开启Redis事务
     * 【注意】
     * MULTI is currently not supported in cluster mode.(cluster mode不支持MULTI)
     */
    public static void multi() {
        stringRedisTemplate.multi();
    }

    /**
     * 提交Redis事务
     */
    public static void exec() {
        stringRedisTemplate.exec();
    }

    /**
     * 回滚Redis事务
     */
    public static void discard() {
        stringRedisTemplate.discard();
    }

    /**
     * 使用SessionCallback执行redis事务
     * 【注意】
     * MULTI is currently not supported in cluster mode.(cluster mode不支持MULTI)
     */
    @SuppressWarnings("unchecked")
    public static void executeTransaction(Consumer<RedisOperations> consumer) {
        try {
            stringRedisTemplate.setEnableTransactionSupport(true);
            stringRedisTemplate.execute(new SessionCallback<List<Object>>() {
                public List<Object> execute(@NonNull RedisOperations redisOperations) throws DataAccessException {
                    redisOperations.multi();
                    consumer.accept(redisOperations);
                    return redisOperations.exec();
                }
            });
        } catch (Exception ex) {
            log.error(ComUtils.printException(ex));
        }
    }

    /**
     * Redis实现分布式锁核心方法：获取锁
     *
     * @param acquireTimeout 在获取锁之前的超时时间--在尝试获取锁的时候，如果在规定的时间内还没有获取到锁，直接放弃（请求超时）；单位：毫秒
     * @param expireTimeout  在获取锁之后的超时时间--当获取锁成功之后，有对应key的有效期，对应的key在规定时间内进行失效；单位：毫秒
     */
    public static String gainRedisLock(long acquireTimeout, long expireTimeout) {
        try {
            AtomicInteger counter = new AtomicInteger(0);

            final String uniqueValue = DigestUtils.md5DigestAsHex(
                    String.format("%s_%s_%s", UUID.randomUUID().toString(), String.valueOf(System.currentTimeMillis()), String.valueOf(Math.random()))
                            .getBytes(StandardCharsets.UTF_8));
            byte[] lockKey = REDIS_LOCK.getBytes(StandardCharsets.UTF_8);
            byte[] lockValue = uniqueValue.getBytes(StandardCharsets.UTF_8);

            // 使用循环机制，保证重复进行尝试获取锁（乐观锁）
            long endTime = System.currentTimeMillis() + acquireTimeout;
            while (System.currentTimeMillis() < endTime) {
                // 获取锁：使用setnx命令插入REDIS_LOCK，返回1表示成功获取锁。
                Object value = redisTemplate.execute((RedisConnection redisConnection) -> {
                    counter.incrementAndGet();
                    Boolean isSuccess = redisConnection.stringCommands().setNX(lockKey, lockValue);
                    if (isSuccess != null && isSuccess) {
                        redisConnection.expire(lockKey, expireTimeout / 1000);//超时时间，单位：秒
                        log.info("线程{} 拿到分布式锁 尝试次数：{}", Thread.currentThread().getName(), counter.get());
                    }

                    if (redisConnection.isClosed()) {
                        redisConnection.close();
                    }
                    return isSuccess != null && isSuccess ? uniqueValue : null;
                });

                if (value != null) {
                    return String.valueOf(value);
                }
            }
            log.error("线程{} 获取分布式锁超时 尝试次数：{}", Thread.currentThread().getName(), counter.get());
        } catch (Exception ex) {
            log.error(ComUtils.printException(ex));
        }
        return null;
    }

    /**
     * Redis实现分布式锁核心方法：释放锁
     * 1、key在redis中超时失效
     * 2、业务执行完毕，删除key
     */
    public static void releaseRedisLock(String redisLockValue) {
        Assert.notNull(redisLockValue, "分布式锁的ID为空");
        try {
            redisTemplate.execute((RedisConnection redisConnection) -> {
                byte[] lockKey = REDIS_LOCK.getBytes(StandardCharsets.UTF_8);
                byte[] lockValue = redisConnection.stringCommands().get(lockKey);

                String strLockValue = lockValue == null ? "" : new String(lockValue, StandardCharsets.UTF_8);
                if (redisLockValue.equals(strLockValue)) {
                    redisConnection.del(lockKey);
                    log.info("线程{} 释放分布式锁", Thread.currentThread().getName());
                } else {
                    log.error("线程{} 被释放的分布式锁 {} 不存在", Thread.currentThread().getName(), redisLockValue);
                }

                if (redisConnection.isClosed()) {
                    redisConnection.close();
                }
                return true;
            });
        } catch (Exception ex) {
            log.error(ComUtils.printException(ex));
        }
    }

    /**
     * 设置过期时间
     */
    public static void setExpire(long seconds, String... keys) {
        try {
            if (seconds > 0) {
                Arrays.asList(keys).forEach(key -> stringRedisTemplate.expire(key, seconds, TimeUnit.SECONDS));
            }
        } catch (Exception ex) {
            log.error(ComUtils.printException(ex));
        }
    }

    /**
     * 获取过期时间
     */
    public static Long getExpire(String key) {
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 判断缓存是否存在
     */
    public static Boolean exists(String key) {
        try {
            return stringRedisTemplate.hasKey(key);
        } catch (Exception ex) {
            log.error(ComUtils.printException(ex));
            return false;
        }
    }

    /**
     * 删除缓存
     */
    public static void removeCache(List<?> keys) {
        for (Object key : keys) {
            stringRedisTemplate.delete(str(key));
        }
    }

    /**
     * 缓存String值
     */
    public static void putValue(String key, Object value) {
        stringRedisTemplate.opsForValue().set(key, str(value));
    }

    /**
     * 读取缓存String
     */
    public static String getValue(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 缓存Set集合
     */
    public static void putSet(String key, Set<?> set) {
        String[] arrays = set.stream().map(RedisUtils::str).toArray(String[]::new);
        stringRedisTemplate.opsForSet().add(key, arrays);
    }

    /**
     * 在Set集合中添加元素
     */
    public static void putSet(String key, Object... values) {
        String[] arrays = Arrays.stream(values).map(RedisUtils::str).toArray(String[]::new);
        stringRedisTemplate.opsForSet().add(key, arrays);
    }

    /**
     * 判断Set集合是否包含value
     */
    public static Boolean confirmSetElement(String key, Object value) {
        return stringRedisTemplate.opsForSet().isMember(key, str(value));
    }

    /**
     * 读取缓存Set
     */
    public static Set<String> getSet(String key) {
        return stringRedisTemplate.opsForSet().members(key);
    }

    /**
     * 缓存List列表
     */
    public static void putList(String key, List<?> list) {
        String[] arrays = list.stream().map(RedisUtils::str).toArray(String[]::new);
        stringRedisTemplate.opsForList().rightPushAll(key, arrays);
    }

    /**
     * 在List列表中添加元素
     */
    public static void putList(String key, Object value) {
        stringRedisTemplate.opsForList().rightPush(key, str(value));
    }

    /**
     * 删除List集合中值等于value的元素
     *
     * @param count index=0, 删除所有值等于value的元素;
     *              count>0, 从头部开始删除count个值等于value的元素;
     *              count<0, 从尾部开始删除count个值等于value的元素;
     */
    public static void removeListElement(String key, Object value, Long count) {
        stringRedisTemplate.opsForList().remove(key, count, str(value));
    }

    /**
     * 读取List列表
     */
    public static List<String> getList(String key) {
        return stringRedisTemplate.opsForList().range(key, 0, -1);
    }

    /**
     * 读取List列表长度
     */
    public static Long getListSize(String key) {
        return stringRedisTemplate.opsForList().size(key);
    }

    /**
     * 缓存Hash表
     */
    public static void putHash(String key, Map<?, ?> map) {
        Map<String, String> hashMap = map.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> str(entry.getKey()),
                        entry -> str(entry.getValue()),
                        (oldValue, newValue) -> newValue
                        )
                );
        stringRedisTemplate.opsForHash().putAll(key, hashMap);
    }

    /**
     * 缓存Hash表的键值对
     */
    public static void putHash(String key, Object field, Object value) {
        stringRedisTemplate.opsForHash().put(key, str(field), str(value));
    }

    /**
     * 读取Hash表
     */
    public static Map<String, String> getHash(String key) {
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        return map.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> str(entry.getKey()),
                        entry -> str(entry.getValue()),
                        (oldValue, newValue) -> newValue
                        )
                );
    }

    /**
     * 读取Hash表中键的值
     */
    public static Object getHashValue(String key, Object field) {
        return stringRedisTemplate.opsForHash().get(key, str(field));
    }

    /**
     * 删除Hash表的键值对
     */
    public static void removeHashEntry(String key, Object... field) {
        Object[] arrays = Arrays.stream(field).map(RedisUtils::str).toArray();
        stringRedisTemplate.opsForHash().delete(key, arrays);
    }

    /**
     * 缓存ZSet有序集合：如果元素存在，会用新的score来替换原来的，返回0；如果元素不存在，则会新增一个
     */
    public static void putZSet(String key, Object value, double score) {
        stringRedisTemplate.opsForZSet().add(key, str(value), score);
    }

    /**
     * 修改Score：元素已存在，对score进行加/减；当元素不存在时，则会新插入一个
     */
    public static void updateZSetScore(String key, Object value, double score) {
        stringRedisTemplate.opsForZSet().incrementScore(key, str(value), score);
    }

    /**
     * 删除ZSet元素
     */
    public static void removeZSetElement(String key, Object... values) {
        Object[] arrays = Arrays.stream(values).map(RedisUtils::str).toArray();
        stringRedisTemplate.opsForZSet().remove(key, arrays);
    }

    /**
     * 获取ZSet有序集合中指定顺序的值，范围是：闭区间[start,end]；（设置参数：start=0, end=-1 表示获取全部的集合内容）
     */
    public static Set<String> getZSet(String key) {
        return stringRedisTemplate.opsForZSet().range(key, 0, -1);
    }

    /**
     * 获取ZSet有序集合中指定顺序的值和score，范围是：闭区间[start,end]；（设置参数：start=0, end=-1 表示获取全部的集合内容）
     */
    public static Set<ZSetOperations.TypedTuple<String>> getZSetByIndex(String key, int start, int end) {
        return stringRedisTemplate.opsForZSet().rangeWithScores(key, start, end);
    }

    /**
     * 根据score的值，来获取满足条件的集合，范围是：闭区间[minScore,maxScore]；
     */
    public static Set<String> getZSetElementByScoreRange(String key, double minScore, double maxScore) {
        return stringRedisTemplate.opsForZSet().rangeByScore(key, minScore, maxScore);
    }

    /**
     * 获取value对应的score
     */
    public static Double getZSetElementScore(String key, Object value) {
        return stringRedisTemplate.opsForZSet().score(key, str(value));
    }

    /**
     * 获取value在ZSet中的排名
     */
    public static Long getZSetElementIndex(String key, Object value) {
        return stringRedisTemplate.opsForZSet().rank(key, str(value));
    }

    /**
     * 获取ZSet集合的长度
     */
    public static Long getZSetSize(String key) {
        return stringRedisTemplate.opsForZSet().zCard(key);
    }

    /**
     * 将Object转为String
     */
    public static String str(Object obj) {
        if (obj instanceof String) {
            return String.valueOf(obj);
        }
        try {
            return jacksonMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            log.error(ComUtils.printException(ex));
            throw new RuntimeException(ex);
        }
    }

}
