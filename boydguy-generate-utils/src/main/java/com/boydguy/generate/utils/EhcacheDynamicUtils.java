package com.boydguy.generate.utils;

import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;
import net.sf.ehcache.config.PersistenceConfiguration;
import org.springframework.util.Assert;

@Slf4j
public class EhcacheDynamicUtils {

    private static CacheManager cacheManager;

    static {
        //Configuration config = new Configuration();
        //config.setDynamicConfig(true);
        //setDiskStoreConfiguration(config);
        //setCacheConfiguration(config);
        //cacheManager = CacheManager.create(config);
        cacheManager = CacheManager.create();
    }

    public static Cache getOrAddCache(String cacheName, int expire) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            synchronized (EhcacheDynamicUtils.class) {
                cache = cacheManager.getCache(cacheName);
                if (cache == null) {
                    cache = new Cache(cacheName, 10000, true, false, expire, expire);
                    cacheManager.addCache(cache);
                    cache = cacheManager.getCache(cacheName);
                }
            }
        }
        return cache;
    }

    /**
     * 判断ehcache缓存cacheName分区中是否存在key
     */
    public static boolean exists(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        return cache.isKeyInCache(key) && cache.getQuiet(key) != null;
    }

    public static void put(String cacheName, Object key, Object cacheValue) {
        Cache cache = cacheManager.getCache(cacheName);
        Assert.notNull(cache, cacheName + "不存在");
        Element element = new Element(key, cacheValue);
        cache.put(element);
    }

    public static Object get(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        Assert.notNull(cache, cacheName + "不存在");
        Element element = cache.get(key);
        return element == null ? null : element.getObjectValue();
    }

    public static void removeAllCacheElement(String cacheName) {
        cacheManager.removeCache(cacheName);
    }

    public static void removeOneCacheElement(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.remove(key);
        }
    }

    private static void setDiskStoreConfiguration(Configuration config) {
        DiskStoreConfiguration dsc = new DiskStoreConfiguration();
        dsc.setPath("/swap");
        config.addDiskStore(dsc);
    }

    private static void setCacheConfiguration(Configuration config) {
        CacheConfiguration cacheCFG = new CacheConfiguration();
        cacheCFG.setName("defaultEhcache");
        cacheCFG.setMaxEntriesLocalHeap(10000);
        cacheCFG.setEternal(false);
        cacheCFG.setTimeToIdleSeconds(120);
        cacheCFG.setTimeToLiveSeconds(120);
        cacheCFG.setMaxEntriesLocalDisk(10000000);
        cacheCFG.setDiskExpiryThreadIntervalSeconds(60);
        cacheCFG.setMemoryStoreEvictionPolicy("LRU");
        setPersistenceConfiguration(cacheCFG);
        setCacheEventListenerFactory(cacheCFG);
        config.addCache(cacheCFG);
    }

    private static void setPersistenceConfiguration(CacheConfiguration config) {
        PersistenceConfiguration pc = new PersistenceConfiguration();
        pc.setStrategy("localTempSwap");
        config.addPersistence(pc);
    }

    private static void setCacheEventListenerFactory(CacheConfiguration config) {
        CacheConfiguration.CacheEventListenerFactoryConfiguration listenerFactoryConfiguration = new CacheConfiguration.CacheEventListenerFactoryConfiguration();
        listenerFactoryConfiguration.setClass("com.boydguy.generate.utils.MyCacheEventListenerFactory");
        listenerFactoryConfiguration.setProperties("cache=myDefCache");
        config.addCacheEventListenerFactory(listenerFactoryConfiguration);
    }

}
