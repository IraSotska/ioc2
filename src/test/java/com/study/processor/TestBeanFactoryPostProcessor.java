package com.study.processor;

import com.study.ioc.entity.BeanDefinition;
import com.study.ioc.processor.BeanFactoryPostProcessor;

import java.util.List;

public class TestBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(List<BeanDefinition> beanDefinitionList) {
        beanDefinitionList.forEach(beanDefinition -> beanDefinition.setClassName("newClassName"));
    }
}
