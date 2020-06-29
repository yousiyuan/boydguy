package com.boydguy.generate.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注意，一级缓存的过期时间比二级缓存的过期时间要短
 * 如何解决二级缓存(redis)与一级缓存(ehcache)不同步的问题：①定时Job ②mq通知
 */
@Target(value = {ElementType.METHOD})
@Retention(value = RetentionPolicy.RUNTIME)
public @interface ApplyAppCache {

    /**
     * ehcache缓存超时时间默认10秒，作为一级缓存，应用程序优先读取本地缓存
     */
    int ehcacheTimeout() default 299;

    /**
     * redis缓存超时时间默认15秒，作为二级缓存，应用程序在本地缓存中没有时，查找二级缓存
     */
    int redisTimeout() default 300;

}
