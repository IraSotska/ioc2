package com.study.ioc.processor;

public interface BeanPostProcessor {
    Object postProcessBeforeInitialization(Object bean, String name);
    Object postProcessAfterInitialization(Object bean, String name);
}
