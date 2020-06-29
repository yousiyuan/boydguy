package com.boydguy.backend.dao.aspect;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
public class MethodDescription implements Serializable {
    /**
     * 类的访问修饰符
     */
    private String modifier;
    /**
     * 类的全路径
     */
    private String classFullPath;
    /**
     * 返回类型
     */
    private String returnType;
    /**
     * 方法名称
     */
    private String methodName;
    /**
     * 参数列表
     */
    private List<MethodParams> methodParams;

    public String getObjectString() {
        String[] fields = new String[]{modifier, returnType, classFullPath, methodName};
        String[] params = methodParams.stream().map(MethodParams::getObjectString).toArray(String[]::new);
        return String.join("$", fields) + "(" + String.join(",", params) + ")";
    }

}
