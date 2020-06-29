package com.boydguy.generate.utils;

import com.googlecode.gentyref.GenericTypeReflector;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.framework.AdvisedSupport;
import org.springframework.aop.framework.AopProxy;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AspectUtils {

    /**
     * 获取被拦截的方法
     */
    public static Method getSourceMethod(ProceedingJoinPoint joinPoint) {
        Method sourceMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        if (sourceMethod != null) {
            return sourceMethod;
        }

        String methodName = joinPoint.getSignature().getName();
        Class<?>[] paramTypes = Arrays.stream(joinPoint.getArgs())
                .map(arg -> {
                    if (arg.getClass().isAnonymousClass()) {
                        // 如果参数arg是(内部)匿名类对象，那么返回它的父类
                        return arg.getClass().getSuperclass();
                    } else {
                        // 非匿名类对象，直接放回参数类型
                        return arg.getClass();
                    }
                })
                .toArray(Class<?>[]::new);
        return getReflectMethod(joinPoint.getTarget().getClass(), methodName, paramTypes);
    }

    /**
     * 查找泛型方法
     */
    public static Method getReflectMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        Method targetMethod = ReflectionUtils.findMethod(clazz, methodName, paramTypes);
        if (targetMethod != null) {
            return targetMethod;
        }

        // 获取并筛选对象可以访问的所有方法
        Method[] methods = clazz.getMethods();
        for (Method func : methods) {
            if (!methodName.equals(func.getName()) || Modifier.isPrivate(func.getModifiers())) {
                // 排除非重载方法与私有方法
                continue;
            }
            if (func.getParameterCount() != paramTypes.length) {
                // 排除参数列表长度不同的方法
                continue;
            }
            // 针对泛型方法，需要拿到它在运行时的实际参数类型，然后对比参数列表中每个对应参数的类型是否相同
            Type[] exactParameterTypes = GenericTypeReflector.getExactParameterTypes(func, clazz);
            if (Arrays.equals(paramTypes, exactParameterTypes) || Arrays.deepEquals(paramTypes, exactParameterTypes)) {
                return func;
            }
        }
        // 如果拿不到被拦截的方法，果断抛出异常，而程序员本应该在开发阶段解决程序缺陷的问题
        throw new RuntimeException("被拦截的方法" + methodName + "不存在");
    }

    /**
     * 获取被代理的对象
     */
    public static Object getSourceObject(Object proxy) throws Exception {
        if (!AopUtils.isAopProxy(proxy)) {
            //不是代理对象
            return proxy;
        }
        if (AopUtils.isJdkDynamicProxy(proxy)) {
            return getJdkDynamicProxyTargetObject(proxy);
        } else { //cglib
            return getCglibProxyTargetObject(proxy);
        }
    }

    /**
     * CGLIB方式被代理类的获取
     */
    private static Object getCglibProxyTargetObject(Object proxy) throws Exception {
        Field h = proxy.getClass().getDeclaredField("CGLIB$CALLBACK_0");
        h.setAccessible(true);
        Object dynamicAdvisedInterceptor = h.get(proxy);
        Field advised = dynamicAdvisedInterceptor.getClass().getDeclaredField("advised");
        advised.setAccessible(true);
        return ((AdvisedSupport) advised.get(dynamicAdvisedInterceptor)).getTargetSource().getTarget();
    }

    /**
     * JDK动态代理方式被代理类的获取
     */
    private static Object getJdkDynamicProxyTargetObject(Object proxy) throws Exception {
        Field h = proxy.getClass().getSuperclass().getDeclaredField("h");
        h.setAccessible(true);
        AopProxy aopProxy = (AopProxy) h.get(proxy);
        Field advised = aopProxy.getClass().getDeclaredField("advised");
        advised.setAccessible(true);
        return ((AdvisedSupport) advised.get(aopProxy)).getTargetSource().getTarget();
    }

}
