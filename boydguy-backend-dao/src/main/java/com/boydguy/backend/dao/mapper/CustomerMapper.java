package com.boydguy.backend.dao.mapper;

import com.boydguy.backend.pojo.Customer;
import tk.mybatis.mapper.common.IdsMapper;
import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.common.MySqlMapper;

public interface CustomerMapper extends Mapper<Customer>, MySqlMapper<Customer>, IdsMapper<Customer> {
}