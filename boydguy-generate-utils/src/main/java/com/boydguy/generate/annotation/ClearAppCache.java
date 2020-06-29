package com.boydguy.generate.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = {ElementType.METHOD})
@Retention(value = RetentionPolicy.RUNTIME)
public @interface ClearAppCache {

    /**
     * 表示通过哪个方法确认数据筛选范围
     */
    String methodName() default "";

    /**
     * 被查找方法的参数类型列表
     */
    String[] paramTypes() default {};

    /**
     * 表示通过哪个参数确认数据筛选范围
     */
    int[] index() default {0};

    /**
     * 根据指定的通配符匹配到符合条件的cache key，清除缓存
     */
    String[] patterns() default {};

}
