package com.boydguy.backend.service.base;

import com.boydguy.backend.dao.ProductDao;
import com.boydguy.backend.dao.base.BaseDao;
import com.boydguy.backend.pojo.Customer;
import com.boydguy.backend.pojo.Product;
import com.boydguy.backend.service.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseService {

    @Autowired
    protected ProductDao productDao;

    @Autowired
    protected BaseDao<Product> productBaseDao;

    @Autowired
    protected BaseDao<Customer> customerBaseDao;

    @Autowired
    protected RedisService redisService;

}
