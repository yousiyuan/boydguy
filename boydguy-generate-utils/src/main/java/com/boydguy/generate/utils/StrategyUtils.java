package com.boydguy.generate.utils;

import com.boydguy.generate.annotation.ServiceMapper;
import org.reflections.Reflections;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工厂模式的工具类：通过注解@ServiceMapper实现service bean与不同业务类型的映射
 */
public class StrategyUtils {

    private static Map<String, String> map = new ConcurrentHashMap<>();

    static {
        //通过反射扫描指定包下的类
        Reflections reflections = new Reflections("com.boydguy.backend.service");
        //获取带ServiceMapper注解的类
        Set<Class<?>> clazzList = reflections.getTypesAnnotatedWith(ServiceMapper.class);

        for (Class<?> clazz : clazzList) {
            ServiceMapper annotationServiceMapper = clazz.getAnnotation(ServiceMapper.class);
            String[] businessTypeArray = annotationServiceMapper.businessType();

            Service annotationService = clazz.getAnnotation(Service.class);
            String beanName = annotationService.value();

            for (String businessType : businessTypeArray) {
                map.put(businessType, beanName);
            }
        }
    }

    public static String getBeanName(String businessType) {
        return map.get(businessType);
    }

}
