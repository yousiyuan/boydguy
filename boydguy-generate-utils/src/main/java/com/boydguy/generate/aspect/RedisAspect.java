package com.boydguy.generate.aspect;

import com.boydguy.generate.annotation.RedisTransaction;
import com.boydguy.generate.utils.AspectUtils;
import com.boydguy.generate.utils.ComUtils;
import com.boydguy.generate.utils.JsonUtils;
import com.boydguy.generate.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class RedisAspect implements Ordered {

    /**
     * 拦截含有注解@RedisTransaction的方法||拦截含有注解@RedisTransaction的类下面的所有方法
     */
    @Pointcut("@annotation(com.boydguy.generate.annotation.RedisTransaction)||@within(com.boydguy.generate.annotation.RedisTransaction)")
    public void redisTransaction() {
    }

    @Around("redisTransaction()")
    public Object executeTransaction(ProceedingJoinPoint joinPoint) {
        Object result = null;

        //TODO：通过反射获取被拦截方法的注解
        Method method = AspectUtils.getSourceMethod(joinPoint);
        String methodName = method.getName();

        RedisTransaction redisTransaction = method.getAnnotation(RedisTransaction.class);
        boolean enableTransactionSupport = redisTransaction.enableTransactionSupport();

        try {
            //TODO：前置通知 - 判断是否 使用Redis事务
            if (enableTransactionSupport) {
                RedisUtils.enableTransactionSupport();// 启用Redis事务支持
                RedisUtils.multi();// 开启Redis事务
                log.info("{}-开启Redis事务", methodName);
            }
            result = joinPoint.proceed(joinPoint.getArgs());

            //TODO：后置通知 - 判断是否 提交Redis事务
            if (enableTransactionSupport) {
                RedisUtils.exec();// 提交Redis事务
                log.info("{}-提交Redis事务", methodName);
            }

            if (result != null) {
                //TODO：返回通知
                log.info("{}-返回：{}", methodName, JsonUtils.to(result));
            }
        } catch (Throwable throwable) {
            // TODO：异常通知 - 判断是否 回滚Redis事务
            if (enableTransactionSupport) {
                RedisUtils.discard();// 回滚Redis事务
                log.info("{}-回滚Redis事务", methodName);
            }

            log.error(ComUtils.printException(throwable));
        }

        return result;
    }

    /**
     * 多个AOP切面的执行顺序设置：order越小越是最先执行，但更重要的是最先执行的最后结束【理解同心圆概念】。
     */
    @Override
    public int getOrder() {
        return 9011;
    }

}
