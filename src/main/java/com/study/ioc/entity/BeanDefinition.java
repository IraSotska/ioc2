package com.study.ioc.entity;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class BeanDefinition {
    private final String id;
    private String className;
    private Map<String, String> valueDependencies;
    private Map<String, String> refDependencies;

    public BeanDefinition(String id, String className) {
        this.id = id;
        this.className = className;
    }
}
