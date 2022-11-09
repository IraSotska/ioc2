package com.study.processor;

import com.study.ioc.processor.BeanPostProcessor;

public class TestPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String name) {
        return name;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        return null;
    }
}
