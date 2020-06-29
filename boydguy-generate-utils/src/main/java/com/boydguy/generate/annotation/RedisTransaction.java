package com.boydguy.generate.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 使用了该注解的方法将支持Redis事务
 */
@Target(value = {ElementType.METHOD, ElementType.TYPE})
@Retention(value = RetentionPolicy.RUNTIME)
public @interface RedisTransaction {

    /**
     * 设置默认：启用Redis事务的支持
     */
    boolean enableTransactionSupport() default true;

}
