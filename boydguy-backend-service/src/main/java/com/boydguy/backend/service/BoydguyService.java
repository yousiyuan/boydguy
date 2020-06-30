package com.boydguy.backend.service;

import com.boydguy.backend.pojo.Customer;
import com.boydguy.backend.pojo.Product;
import com.boydguy.backend.service.base.BaseService;
import com.boydguy.generate.utils.ComUtils;
import com.boydguy.generate.utils.EhcacheUtils;
import com.boydguy.generate.utils.JsonUtils;
import com.boydguy.generate.utils.RedisUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

import java.util.*;

@Slf4j
@Service
public class BoydguyService extends BaseService {

    public BoydguyService() {
    }

    //region Ehcache
    public List<Product> queryAllProductList() {
        //return productDao.selectProductByCategory(1);

//        Product product = new Product();
//        product.setProductId(25);
//        System.out.println(Collections.singletonList(productBaseDao.selectOne(product)));

        //return productBaseDao.selectAll();

//        Product product = new Product();
//        product.setCategoryId(1);
//        return productBaseDao.select(new Product() {{
//            setCategoryId(1);
//        }});

//        Example example = new Example(Product.class);
//        example.createCriteria()
//                .andGreaterThanOrEqualTo("productId", 30);
//        PageInfo<Product> pageData = productBaseDao.selectForPageList(3, 10, "product_id", example);
//        return pageData.getList();

        //List<Product> list = productBaseDao.selectByIds("1,3,5,7,9,11,13,15");

        //System.out.println(productBaseDao.insertList(list));
        //return list;

        //Example example = new Example(Product.class);
        //example.createCriteria()
        //        .andEqualTo("categoryId", 1)
        //        .andGreaterThanOrEqualTo("productId", 70);
        //return productBaseDao.selectByExample(example);

        System.out.println(customerBaseDao.selectAll());
        return productBaseDao.selectAll();
    }

    public List<Object> ehcacheTest() {
        Object cacheValue = EhcacheUtils.get("appCache", "sysTime");
        if (cacheValue == null) {
            log.info("sysTime-缓存中没有数据");
            ArrayList<Object> list = new ArrayList<>();
            Calendar calendar = Calendar.getInstance();
            list.add(calendar.get(Calendar.YEAR));
            list.add(calendar.get(Calendar.MONTH) + 1);
            list.add(calendar.get(Calendar.DAY_OF_MONTH));
            list.add(calendar.get(Calendar.HOUR_OF_DAY));
            list.add(calendar.get(Calendar.MINUTE));
            list.add(calendar.get(Calendar.SECOND));
            EhcacheUtils.put("appCache", "sysTime", list);
            log.info("sysTime-将数据放入缓存");
            return list;
        }
        log.info("sysTime-返回缓存中的数据");
        return JsonUtils.typeParse(cacheValue, new TypeReference<List<Object>>() {
        });
    }

    public Map<String, Object> ehcacheTest2() {
        List<Product> products = productBaseDao.selectAll();
        List<Customer> customers = customerBaseDao.selectAll();

        Map<String, Object> map = new HashMap<>();
        map.put("products", products);
        map.put("customers", customers);
        return map;
    }

    public boolean clearCacheTest() {
        EhcacheUtils.removeOneCacheElement("appCache", "sysTime");
        return true;
    }
    //endregion

    //region Redis
    public void redisTest() {
        //value
        RedisUtils.putValue("value1", "字符串1");
        RedisUtils.putValue("value2", new Customer());

        //set
        RedisUtils.putSet("set1", new HashSet<String>() {{
            add("流年·公子");
            add("流年·莉娜");
        }});
        RedisUtils.putSet("set1", 666);
        RedisUtils.putSet("set2", new HashSet<Product>() {{
            add(new Product());
        }});
        RedisUtils.putSet("set2", new Customer());

        //list
        RedisUtils.putList("list1", new ArrayList<Integer>() {{
            add(31);
            add(30);
        }});
        RedisUtils.putList("list1", new Customer());
        RedisUtils.putList("list2", new ArrayList<Product>() {{
            add(new Product());
        }});
        RedisUtils.putList("list2", new Customer());

        //hash
        RedisUtils.putHash("hash1", new HashMap<Object, Object>() {{
            put("Product", new Product());
            put("Customer", new Customer());
        }});
        RedisUtils.putHash("hash1", "num", 555);
        RedisUtils.putHash("hash2", 12, 24);
        RedisUtils.putHash("hash2", new HashMap<Object, Object>() {{
            put("123", 246);
            put(222, "444");
        }});

        //ZSet
        RedisUtils.putZSet("ZSet1", 456, 11);
        RedisUtils.putZSet("ZSet1", "zxb", 22);
        RedisUtils.putZSet("ZSet2", new Product(), 33);
        RedisUtils.putZSet("ZSet2", new Customer(), 44);
        RedisUtils.putZSet("ZSet2", "element01", 55);
        RedisUtils.putZSet("ZSet2", "element02", 66);
        RedisUtils.putZSet("ZSet2", "element03", 77);
        RedisUtils.putZSet("ZSet2", "element04", 88);
        RedisUtils.putZSet("ZSet2", "element05", 99);
    }

    public void redisTest2() {
        //value
        log.info(RedisUtils.getValue("value1"));//字符串1
        log.info(RedisUtils.getValue("value2"));//value2的Json

        //set
        log.info(RedisUtils.getSet("set1").toString());//3个
        log.info(RedisUtils.getSet("set2").toString());//2个

        //list
        log.info(RedisUtils.getList("list1").toString());//3个
        log.info(RedisUtils.getList("list2").toString());//2个

        //hash
        log.info(RedisUtils.getHash("hash1").toString());
        log.info(RedisUtils.getHash("hash2").toString());

        //ZSet
        log.info(RedisUtils.getZSet("ZSet1").toString());
        log.info(RedisUtils.getZSet("ZSet2").toString());
    }

    public void redisTest3() {
        RedisUtils.putValue("value1", "字符串666");
        RedisUtils.putValue("value2", new Product());
        log.info(JsonUtils.to(RedisUtils.getValue("value1")));//字符串666
        log.info(JsonUtils.to(RedisUtils.getValue("value2")));//Product的Json

        RedisUtils.setExpire(300, "value2");
        log.info("value2过期：{}", RedisUtils.getExpire("value2"));
        RedisUtils.removeCache(Arrays.asList("value1", "set1", "list1", "hash1", "ZSet1"));
        log.info("value1是否存在：{}", RedisUtils.exists("value1"));
        log.info("set2中Product的字符串是否存在：{}", RedisUtils.confirmSetElement("set2", new Product()));

        log.info("list2中删除Customer的字符串");
        RedisUtils.removeListElement("list2", new Customer(), 0L);
        log.info("list2的长度：{}", RedisUtils.getListSize("list2"));
        log.info("hash2中222等于：{}", RedisUtils.getHashValue("hash2", "222"));
        RedisUtils.removeHashEntry("hash2", 222);
        log.info("hash2中移除222");

        log.info("ZSet2的长度：{}", RedisUtils.getZSetSize("ZSet2"));
        log.info("Product字符串在ZSet2集合中的索引：{}", RedisUtils.getZSetElementIndex("ZSet2", new Product()));
        log.info("Product字符串在ZSet2集合中的Score：{}", RedisUtils.getZSetElementScore("ZSet2", new Product()));

        log.info(RedisUtils.getZSetElementByScoreRange("ZSet2", 55, 99).toString());

        RedisUtils.updateZSetScore("ZSet2", "index11", 111);
        RedisUtils.updateZSetScore("ZSet2", "index12", 222);
        RedisUtils.updateZSetScore("ZSet2", "index13", 333);
        RedisUtils.updateZSetScore("ZSet2", "index14", 444);
        RedisUtils.updateZSetScore("ZSet2", "index15", 555);
    }

    public void redisTest4() {
        RedisUtils.removeZSetElement("ZSet2", new Product());
        log.info("在ZSet2集合中移除Product字符串");

    }

    public void redisTest5() {
        Set<ZSetOperations.TypedTuple<String>> zSetWithScore = RedisUtils.getZSetByIndex("ZSet2", 7, 11);
        for (ZSetOperations.TypedTuple<String> item : zSetWithScore) {
            log.info("在ZSet2中：Value= {} ； Score= {}", item.getValue(), item.getScore());
        }

        RedisUtils.removeCache(Arrays.asList("value2", "set2", "list2", "hash2", "ZSet2"));
    }

    /**
     * redis事务：①单台redis服务器；②主从复制模式的集群；③哨兵模式(基于主从复制)的集群
     * 注意：redis的 Cluster 集群不支持事务
     */
    public void redisTest6() {
        redisService.cacheProductData();
        redisService.cacheCustomerData();
    }

    /**
     * redis事务：SessionCallback方式
     * 注意：Cluster 集群不支持事务
     */
    public void redisTest7() {
        redisService.redisDistributedCluster();
    }
    //endregion

    public void deleteObjectTest() {
        Example example0 = new Example(Product.class);
        example0.createCriteria()
                .andGreaterThanOrEqualTo("productId", 30);
        PageInfo<Product> pageData = productBaseDao.selectForPageList(3, 10, "product_id", example0);
        System.out.println(pageData.getList());


//        Product product = new Product();
//        product.setCategoryId(1);
//        System.out.println(productBaseDao.select(product));

        System.out.println(productBaseDao.selectByPrimaryKey(25));
        System.out.println(productBaseDao.selectByPrimaryKey(101));
        System.out.println(productBaseDao.selectByPrimaryKey(102));

//        System.out.println(productBaseDao.selectByIds("78,79,80,81,82,83,84,85"));

        Example example1 = new Example(Product.class);
        example1.createCriteria()
                .andEqualTo("categoryId", "2")
                .andGreaterThanOrEqualTo("productId", 30);
        System.out.println(productBaseDao.selectByExample(example1));

//        System.out.println(productBaseDao.delete(product));//select T

//        System.out.println(productBaseDao.deleteByPrimaryKey(25));//selectByPrimaryKey java.lang.Object

//        System.out.println(productBaseDao.deleteByIds("78,79,80,81,82,83,84,85"));//selectByIds java.lang.String

        Example example2 = new Example(Product.class);
        example2.createCriteria()
                .andGreaterThanOrEqualTo("productId", 78);
        Product product1 = new Product();
        product1.setProductName("流年·公子");
        product1.setQuantityPerUnit("位");
        System.out.println(productBaseDao.updateByExampleSelective(product1, example2));//selectByExample tk.mybatis.mapper.entity.Example
    }

    public String queryProductList() {
        Object result = rabbitService.produce("DRACD");
        return ComUtils.str(result);
    }

}
