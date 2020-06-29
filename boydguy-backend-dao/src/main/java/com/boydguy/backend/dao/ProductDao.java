package com.boydguy.backend.dao;

import com.boydguy.backend.dao.mapper.ProductMapper;
import com.boydguy.backend.pojo.Product;
import com.boydguy.generate.annotation.ApplyAppCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProductDao {

    private ProductMapper productMapper;

    @Autowired
    public ProductDao(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @ApplyAppCache
    public List<Product> selectProductByCategory(Integer categoryId) {
        return productMapper.selectProductByCategory(categoryId);
    }

}
