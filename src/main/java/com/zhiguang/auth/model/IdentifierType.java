package com.zhiguang.auth.model;

// 登录方式
public enum IdentifierType {
    PHONE,
    EMAIL;

    //字符串 → 枚举的安全转换器
    public static IdentifierType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("identifier type required");
        }
        return switch (value.toLowerCase()) {
            case "phone", "mobile" -> PHONE;
            case "email" -> EMAIL;
            default -> throw new IllegalArgumentException("Unsupported identifier type: " + value);
        };
    }
}
