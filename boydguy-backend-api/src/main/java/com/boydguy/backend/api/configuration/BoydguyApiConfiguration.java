package com.boydguy.backend.api.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableCaching(proxyTargetClass = true)
@ComponentScan(basePackages = {"com.boydguy.backend", "com.boydguy.generate"})
@MapperScan(basePackages = {"com.boydguy.backend.dao.mapper"})
@Import(value = {JacksonConfig.class, RedisConfig.class, RabbitConfig.class, RepositoryConfig.class})
public class BoydguyApiConfiguration {
}
