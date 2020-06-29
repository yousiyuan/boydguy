package com.boydguy.backend.dao.aspect;

import com.boydguy.generate.utils.RedisUtils;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MethodParams {
    private String paramType;
    private Object paramValue;

    public String getObjectString() {
        return paramType + "=" + RedisUtils.str(paramValue);
    }

}
