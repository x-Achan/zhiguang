package com.zhiguang.counter.model;

public enum CounterType {
    LIKE("like"),
    FAV("fav");

    private final String value;

    CounterType(String value) {
        this.value = value;
    }
    public  String value(){
        return value;
    }
    /** 对账时从索引条目反解类型 */
    public static CounterType fromValue(String value) {
        for (CounterType t : values()) {
            if (t.value().equals(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown CounterType: " + value);
    }
}
