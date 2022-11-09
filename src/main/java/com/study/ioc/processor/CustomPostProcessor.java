package com.study.ioc.processor;

public class CustomPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String name) {
        return null;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        return null;
    }
}
