package com.boydguy.generate.utils;

import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.springframework.core.io.ClassPathResource;

@Slf4j
public class EhcacheUtils {

    private static CacheManager cacheManager;

    static {
        cacheManager = SpringContextUtils.getBean(CacheManager.class);
    }

    public static void put(String cacheName, Object key, Object cacheValue) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            cacheManager.addCache(cacheName);
            cache = cacheManager.getCache(cacheName);
        }
        Element element = new Element(key, cacheValue);
        cache.put(element);
    }

    public static Object get(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            cacheManager.addCache(cacheName);
            cache = cacheManager.getCache(cacheName);
        }
        Element element = cache.get(key);
        if (element == null) {
            return null;
        }
        return element.getObjectValue();
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

    private static CacheManager initCacheManager() {
        try {
            ClassPathResource ehcacheResource = new ClassPathResource("/ehcache.xml");
            if (ehcacheResource.exists()) {
                return CacheManager.newInstance(ehcacheResource.getInputStream());
            }
            return CacheManager.newInstance();
        } catch (Exception ex) {
            log.warn("init cache manager failed!!!", ComUtils.printException(ex));
            return null;
        }
    }

    public static Cache initCache(String cacheName, int maxElementsInMemory, boolean overflowToDisk, boolean eternal, long timeToLiveSeconds, long timeToIdleSeconds) throws Exception {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                cache = new Cache(cacheName, maxElementsInMemory, overflowToDisk, eternal, timeToLiveSeconds, timeToIdleSeconds);
                cacheManager.addCache(cache);
                cache = cacheManager.getCache(cacheName);
            }
            return cache;
        } catch (Exception e) {
            log.error("init cache " + cacheName + " failed!!!", ComUtils.printException(e));
            return null;
        }
    }

}
