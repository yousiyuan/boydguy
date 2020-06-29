package com.boydguy.backend.api.controller;

import com.boydguy.backend.pojo.Product;
import com.boydguy.backend.service.BoydguyService;
import com.boydguy.generate.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/example")
public class ExampleApiController {

    @Autowired
    public ExampleApiController(BoydguyService boydguyService) {
        this.boydguyService = boydguyService;
    }

    private BoydguyService boydguyService;

    @GetMapping("/test")
    public String testApi() {
        List<Product> products = boydguyService.queryAllProductList();
        //RedisUtils.getValue("",null);
        return JsonUtils.to(products);
    }

    @GetMapping("/test2")
    public String testApi2() {
        boydguyService.deleteObjectTest();
        return "测试删除缓存";
    }

    @GetMapping("/cache")
    public String testCache() {
        List<Object> objects = boydguyService.ehcacheTest();
        return JsonUtils.to(objects);
    }

    @GetMapping("/cache2")
    public String testCache2() {
        return JsonUtils.to(boydguyService.ehcacheTest2());
    }

    @GetMapping("/clear")
    public boolean testClearCache() {
        return boydguyService.clearCacheTest();
    }

    @GetMapping("/redis1")
    public String redisTest1() {
        boydguyService.redisTest();
        return "redis1测试完毕";
    }

    @GetMapping("/redis2")
    public String redisTest2() {
        boydguyService.redisTest2();
        return "redis2测试完毕";
    }

    @GetMapping("/redis3")
    public String redisTest3() {
        boydguyService.redisTest3();
        return "redis3测试完毕";
    }

    @GetMapping("/redis4")
    public String redisTest4() {
        boydguyService.redisTest4();
        return "redis4测试完毕";
    }

    @GetMapping("/redis5")
    public String redisTest5() {
        boydguyService.redisTest5();
        return "redis5测试完毕";
    }

    @GetMapping("/redis6")
    public String redisTest6() {
        boydguyService.redisTest6();
        return "redis6测试完毕";
    }

    @GetMapping("/redis7")
    public String redisTest7() {
        boydguyService.redisTest7();
        return "redis7测试完毕";
    }

}
