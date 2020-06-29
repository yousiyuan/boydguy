package com.boydguy.backend.dao.aspect;

import com.boydguy.generate.annotation.ApplyAppCache;
import com.boydguy.generate.annotation.ClearAppCache;
import com.boydguy.generate.utils.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.github.pagehelper.PageInfo;
import com.googlecode.gentyref.GenericTypeReflector;
import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 通过AOP切面替换Ehcache注解的使用
 */
@Slf4j
@Aspect
@Component
public class DaoAspect extends KeyExpirationEventMessageListener {

    private static final ConcurrentHashMap<String, MethodDescription> APP_CACHE_KEY_MAP = new ConcurrentHashMap<>();
    private static final String APP_CACHE_KEY = "daoCacheKeyMap";
    private static final String EHCACHE_APP_NAME = "daoAspectCache";
    private ObjectMapper objectMapper;

    @Autowired
    public DaoAspect(RedisMessageListenerContainer redisMessageListenerContainer,
                     StringRedisTemplate stringRedisTemplate,
                     ObjectMapper objectMapper) throws IOException {
        super(redisMessageListenerContainer);

        this.objectMapper = objectMapper;
        ObjectMapper jacksonMapper = objectMapper.copy();
        jacksonMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        jacksonMapper.setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
        initAppCacheKeyMap(stringRedisTemplate, jacksonMapper);
    }

    /**
     * 拦截含有注解@ApplicationCache的方法
     */
    @Pointcut("@annotation(com.boydguy.generate.annotation.ApplyAppCache)")
    public void applyAppCache() {
    }

    @Pointcut("@annotation(com.boydguy.generate.annotation.ClearAppCache)")
    public void clearAppCache() {
    }

    /**
     * 环绕通知：添加缓存
     */
    @Around(value = "applyAppCache()")
    public Object selectAdvice(ProceedingJoinPoint joinPoint) {
        Object result = null;
        String packagePathName = "";//被代理的类完整限定名
        String methodName = "";//被代理的方法名称
        Object[] args = joinPoint.getArgs();//代理的方法参数列表

        try {
            //TODO：前置通知...
            Object source = joinPoint.getTarget();//被代理的类实例对象
            Method method = AspectUtils.getSourceMethod(joinPoint);//获取被代理的方法对象
            Type exactReturnType = GenericTypeReflector.getExactReturnType(method, source.getClass());
            String methodReturnType = exactReturnType.getTypeName();//获取方法的返回值类型，包括泛型

            packagePathName = source.getClass().getGenericSuperclass().getTypeName();//被代理的类完整限定名，包括泛型
            methodName = method.getName();//被代理的方法名称

            MethodParams[] methodParams = new MethodParams[args.length];
            Type[] exactParameterTypes = GenericTypeReflector.getExactParameterTypes(method, source.getClass());
            for (int i = 0; i < exactParameterTypes.length; i++) {
                MethodParams methodParam = new MethodParams();
                methodParam.setParamType(exactParameterTypes[i].getTypeName());
                methodParam.setParamValue(args[i]);
                methodParams[i] = methodParam;
            }

            MethodDescription methodDesc = new MethodDescription();
            methodDesc.setModifier(Modifier.toString(method.getModifiers()));
            methodDesc.setClassFullPath(packagePathName);
            methodDesc.setMethodName(methodName);
            methodDesc.setReturnType(methodReturnType);
            methodDesc.setMethodParams(Arrays.asList(methodParams));
            String plainKey = methodDesc.getObjectString();
            log.info("用于缓存的明文key：{}", plainKey);
            String cacheKey = DigestUtils.md5DigestAsHex(plainKey.getBytes(StandardCharsets.UTF_8));
            log.info("用于缓存的秘文key：{}", cacheKey);

            ApplyAppCache applyAppCacheAnnotation = method.getAnnotation(ApplyAppCache.class);
            int ehcacheTimeout = applyAppCacheAnnotation.ehcacheTimeout();
            int redisExpireTime = applyAppCacheAnnotation.redisTimeout();

            //TODO：获取一级缓存Ehcache
            Cache ehcache = EhcacheDynamicUtils.getOrAddCache(EHCACHE_APP_NAME, ehcacheTimeout);
            Element ehcacheElement = ehcache.get(cacheKey);
            if (ehcacheElement != null) {
                Object ehcacheValue = ehcacheElement.getObjectValue();
                //TODO：一级缓存Ehcache中有数据不再执行二级缓存Redis查询
                log.info("返回了一级缓存Ehcache的数据 cache key：{}", cacheKey);

                //TODO：防止Redis穿透
                if ("${null}".equals(String.valueOf(ehcacheValue))) {
                    return null;
                }

                // 通过Jackson的TypeFactory动态构建反序列化的类型（类似于TypeReference<T>）
                return stringValueCastToTargetType(String.valueOf(ehcacheValue), exactReturnType);
            }

            //TODO：获取二级缓存Redis
            String redisValue = RedisUtils.getValue(cacheKey);
            if (redisValue != null) {
                //TODO：防止Redis穿透
                if ("${null}".equals(redisValue)) {
                    return null;
                }

                //TODO：二级缓存Redis中有数据不再执行DB查询
                ehcache.put(new Element(cacheKey, redisValue));//如果二级缓存Redis中有数据需要再次保存到一级缓存ehcache中
                log.info("返回了二级缓存Redis的数据 cache key：{}", cacheKey);

                // 通过Jackson的TypeFactory动态构建反序列化的类型（类似于TypeReference<T>）
                return stringValueCastToTargetType(redisValue, exactReturnType);
            }

            //TODO：执行被代理的方法
            result = joinPoint.proceed(args);

            //TODO：后置通知...
            if (result != null) {
                String resultJsonValue = JsonUtils.to(result);
                //TODO：返回通知...
                RedisUtils.putValue(cacheKey, resultJsonValue);//存放二级缓存
                RedisUtils.setExpire(redisExpireTime, cacheKey);

                ehcache.put(new Element(cacheKey, resultJsonValue));//存放一级缓存
                log.info("缓存数据：{} - {}", cacheKey, resultJsonValue);

                RedisUtils.putHash(APP_CACHE_KEY, cacheKey, methodDesc);//保存cacheKey在redis中
                APP_CACHE_KEY_MAP.put(cacheKey, methodDesc);//保存cacheKey在内存中
                log.info("已同步缓存Key {}", cacheKey);
            } else {
                //TODO：防止Redis穿透
                RedisUtils.putValue(cacheKey, "${null}");//存放二级缓存
                RedisUtils.setExpire(redisExpireTime, cacheKey);
                ehcache.put(new Element(cacheKey, "${null}"));//存放一级缓存

                RedisUtils.putHash(APP_CACHE_KEY, cacheKey, methodDesc);//同步cacheKey在redis中
                APP_CACHE_KEY_MAP.put(cacheKey, methodDesc);//保存cacheKey在内存中
            }
        } catch (Throwable throwable) {
            //TODO：异常通知...
            log.error("执行 {}.{} 时发生异常：{}", packagePathName, methodName, ComUtils.printException(throwable));
        }

        return result;
    }

    /**
     * 环绕通知：清空缓存
     */
    @Around(value = "clearAppCache()")
    public Object modifyAdvice(ProceedingJoinPoint joinPoint) {
        Object result = null;

        String methodName = "";//被代理的方法名称
        String packagePathName = "";//被代理的类完整限定名

        try {
            //TODO：前置通知...
            Object[] args = joinPoint.getArgs();
            Object source = joinPoint.getTarget();//获取被代理的类实例对象
            Method method = AspectUtils.getSourceMethod(joinPoint);//获取被代理的方法对象
            methodName = method.getName();//被代理的方法名称
            packagePathName = GenericTypeReflector.erase(source.getClass()).getGenericSuperclass().getTypeName();//被代理的类完整限定名
            //packagePathName = joinPoint.getSignature().getDeclaringTypeName();
            ClearAppCache clearAppCacheAnnotation = method.getAnnotation(ClearAppCache.class);//获取被代理方法的注解对象

            // ①筛选cacheKey，缩小比对范围
            CopyOnWriteArrayList<MethodDescription> filterPlainKeyList = new CopyOnWriteArrayList<>();
            for (MethodDescription item : APP_CACHE_KEY_MAP.values()) {
                if (!packagePathName.equals(item.getClassFullPath())) {
                    continue;
                }
                filterPlainKeyList.add(item);
            }
            // ②获取需要被清除缓存的cacheKey
            CopyOnWriteArrayList<String> cacheKeyList = getCacheKeyByResult(source, args, clearAppCacheAnnotation, filterPlainKeyList);

            //TODO：执行被代理的方法
            result = joinPoint.proceed(args);

            //TODO：后置通知...
            // ③将最终确认的cache key从缓存中清除
            // Ehcache作为一级缓存，在本地内存中将它删除
            for (String key : cacheKeyList) {
                EhcacheDynamicUtils.removeOneCacheElement(EHCACHE_APP_NAME, key);
                RedisUtils.removeCache(Collections.singletonList(key));

                RedisUtils.removeHashEntry(APP_CACHE_KEY, key);
                APP_CACHE_KEY_MAP.remove(key);
                log.info("缓存cache key：{}已被删除并同步到本地", key);
            }

            // ④根据通配符规则清除缓存
            String[] patterns = clearAppCacheAnnotation.patterns();
            for (String pattern : patterns) {
                //根据通配符进行筛选，符合条件的直接从缓存中清除
                log.info("符合通配符 {} 规则的cache kay已被从缓存中清除", pattern);
            }

            if (result != null) {
                //TODO：返回通知...
                log.info("{} 返回值：{}", methodName, JsonUtils.to(result));
            }
        } catch (Throwable throwable) {
            //TODO：异常通知...
            log.info("执行 {}.{} 时发生异常：{}", packagePathName, methodName, ComUtils.printException(throwable));
        }

        return result;
    }

    /**
     * 初始化AppCacheKeyMap
     */
    private void initAppCacheKeyMap(StringRedisTemplate stringRedisTemplate, ObjectMapper jacksonMapper) throws IOException {
        if (APP_CACHE_KEY_MAP.size() > 0) {
            return;
        }
        Map<Object, Object> cacheKeyMap = stringRedisTemplate.opsForHash().entries(APP_CACHE_KEY);
        if (cacheKeyMap.size() > 0) {
            for (Map.Entry<Object, Object> entry : cacheKeyMap.entrySet()) {
                Boolean keyExists = stringRedisTemplate.hasKey(String.valueOf(entry.getKey()));
                if (keyExists != null && keyExists) {
                    MethodDescription value = jacksonMapper.readValue(String.valueOf(entry.getValue()), MethodDescription.class);
                    APP_CACHE_KEY_MAP.put(RedisUtils.str(entry.getKey()), value);
                    continue;
                }
                // 工程启动时，Ehcache缓存中是空的，此处只需要判断工程未启动时，过期的Redis缓存，从daoCacheKeyMap中移除
                stringRedisTemplate.opsForHash().delete(APP_CACHE_KEY, RedisUtils.str(entry.getKey()));
                log.info("Redis中cache key={} 的缓存已过期", entry.getKey());
            }
        }
    }

    private Object stringValueCastToTargetType(String value, Type exactReturnType) throws IOException {
        // 判断方法的返回类型是否为泛型
        if (exactReturnType instanceof ParameterizedType) {
            // 通过Jackson的TypeFactory动态构建反序列化的类型（类似于TypeReference<T>）
            Class<?> rawClass = objectMapper.getTypeFactory()
                    .constructType(((ParameterizedType) exactReturnType).getRawType())
                    .getRawClass();
            Class[] classes = Arrays.stream(((ParameterizedType) exactReturnType).getActualTypeArguments())
                    .map(type -> objectMapper.getTypeFactory().constructType(type).getRawClass())
                    .toArray(Class[]::new);
            JavaType javaType = objectMapper.getTypeFactory().constructParametricType(rawClass, classes);
            return objectMapper.readValue(value, javaType);
        }
        // 方法的返回类型是JavaType
        Class<?> rawClass = objectMapper.getTypeFactory().constructType(exactReturnType).getRawClass();
        return objectMapper.readValue(value, rawClass);
    }

    private CopyOnWriteArrayList<String> getCacheKeyByResult(Object source, Object[] args, ClearAppCache clearAppCacheAnnotation,
                                                             CopyOnWriteArrayList<MethodDescription> filterPlainKeyList) throws Exception {
        //①获取将要被更新或删除的数据
        String confirmMethod = clearAppCacheAnnotation.methodName();
        String[] typeStringArray = clearAppCacheAnnotation.paramTypes();
        Class<?>[] classArray = new Class<?>[typeStringArray.length];
        for (int i = 0; i < typeStringArray.length; i++) {
            String typeString = typeStringArray[i];
            if ("T".equals(typeString)) {
                classArray[i] = ResolvableType.forInstance(source).getSuperType().getGeneric(0).resolve();
                continue;
            }
            classArray[i] = Class.forName(typeString);
        }

        Method targetMethod = AspectUtils.getReflectMethod(source.getClass(), confirmMethod, classArray);
        Assert.notNull(targetMethod, "未查找到目标方法：" + confirmMethod);

        int i = 0;
        Object[] targetParamArray = new Object[classArray.length];
        for (int index : clearAppCacheAnnotation.index()) {
            targetParamArray[i++] = args[index];
        }
        Object modifyData = targetMethod.invoke(source, targetParamArray);
        List<String> castStringList = new ArrayList<>();
        if (Arrays.asList("select", "selectByIds", "selectByExample").contains(confirmMethod)) {
            String stringResult = JsonUtils.to(modifyData);
            JsonNode jsonNode = objectMapper.readTree(stringResult);
            for (int idx = 0; idx < jsonNode.size(); idx++) {
                castStringList.add(objectMapper.writeValueAsString(jsonNode.get(idx)));
            }
        }
        if ("selectByPrimaryKey".equals(confirmMethod)) {
            castStringList.add(objectMapper.writeValueAsString(modifyData));
        }

        //②根据参数列表精确匹配
        CopyOnWriteArrayList<String> cacheKeyList = new CopyOnWriteArrayList<>();
        for (MethodDescription item : filterPlainKeyList) {
            String cacheKey = DigestUtils.md5DigestAsHex(item.getObjectString().getBytes(StandardCharsets.UTF_8));
            if (!EhcacheDynamicUtils.exists(EHCACHE_APP_NAME, cacheKey)) {
                continue;
            }
            String jsonCacheValue = String.valueOf(EhcacheDynamicUtils.get(EHCACHE_APP_NAME, cacheKey));
            List<String> cacheStringList;
            // 针对dao层，缓存中的json数据一般是T、List、PageInfo序列化后的结果
            JsonNode jsonNode = objectMapper.readTree(jsonCacheValue);
            if (JsonNodeType.ARRAY.equals(jsonNode.getNodeType())) {
                cacheStringList = new ArrayList<>();
                for (int idx = 0; idx < jsonNode.size(); idx++) {
                    cacheStringList.add(objectMapper.writeValueAsString(jsonNode.get(idx)));
                }
                // 确认castStringList与cacheStringList两个集合中的json数据是否有交集：
                // 如果有交集，当前cacheKey需要被从缓存中移除
                cacheStringList.retainAll(castStringList);
                if (cacheStringList.size() > 0) {//存在交集
                    cacheKeyList.add(cacheKey);
                }
                continue;
            }

            // PageInfo对象被序列化后有标志性字段
            cacheStringList = tryCastPageInfo(jsonCacheValue);
            if (!"false".equals(cacheStringList.get(0))) {
                cacheStringList.retainAll(castStringList);
                if (cacheStringList.size() > 0) {//存在交集
                    cacheKeyList.add(cacheKey);
                }
                continue;
            }

            // 执行到这一步，被序列化的肯定就是泛型<T>的对象
            cacheStringList = new ArrayList<>();
            cacheStringList.add(jsonCacheValue);
            cacheStringList.retainAll(castStringList);
            if (cacheStringList.size() > 0) {//存在交集
                cacheKeyList.add(cacheKey);
            }
        }

        return cacheKeyList;
    }

    private List<String> tryCastPageInfo(String json) {
        Map<String, Object> castMap = JsonUtils.from(json, new TypeReference<Map<String, Object>>() {
        });
        if (castMap == null) {
            return Collections.singletonList("false");
        }
        List<Field> fieldList = new ArrayList<>();
        Class<?> clazz = PageInfo.class;
        do {
            fieldList.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        } while (clazz != null);
        List<String> pageInfoFieldNameList = fieldList.stream().map(Field::getName).collect(Collectors.toList());
        if (pageInfoFieldNameList.containsAll(castMap.keySet())) {
            PageInfo pageInfo = JsonUtils.from(json, PageInfo.class);
            Assert.notNull(pageInfo, "反序列化PageInfo失败");
            List<String> list = new ArrayList<>();
            for (Object obj : pageInfo.getList()) {
                list.add(JsonUtils.to(obj));
            }
            return list;
        }
        return Collections.singletonList("false");
    }

    /**
     * 监听redis缓存中的过期key过期事件
     * 保证一级缓存与二级缓存同步
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        log.info("redis缓存的Key {} 已过期", expiredKey);

        // Ehcache作为一级缓存，在本地内存中将它删除
        EhcacheDynamicUtils.removeOneCacheElement(EHCACHE_APP_NAME, expiredKey);
        RedisUtils.removeHashEntry(APP_CACHE_KEY, expiredKey);

        // 应用程序中对应的Key从缓存中删除
        APP_CACHE_KEY_MAP.remove(expiredKey);
        log.info("已过期的缓存Key {} 成功同步到本地", expiredKey);
    }

}
