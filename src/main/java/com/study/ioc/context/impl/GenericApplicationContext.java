package com.study.ioc.context.impl;

import com.study.ioc.context.ApplicationContext;
import com.study.ioc.entity.Bean;
import com.study.ioc.entity.BeanDefinition;
import com.study.ioc.exception.BeanInstantiationException;
import com.study.ioc.exception.NoSuchBeanDefinitionException;
import com.study.ioc.exception.NoUniqueBeanOfTypeException;
import com.study.ioc.exception.PostProcessBeanFactoryException;
import com.study.ioc.processor.PostConstruct;
import com.study.ioc.reader.BeanDefinitionReader;
import com.study.ioc.reader.sax.XmlBeanDefinitionReader;
import lombok.NoArgsConstructor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@NoArgsConstructor
public class GenericApplicationContext implements ApplicationContext {

    private Map<String, Bean> beans;

    public GenericApplicationContext(String... paths) {
        this(new XmlBeanDefinitionReader(paths));
    }

    public GenericApplicationContext(BeanDefinitionReader definitionReader) {
        Map<String, BeanDefinition> beanDefinitions = definitionReader.getBeanDefinition();
        Map<String, Bean> allBeans = createBeans(beanDefinitions);

        Map<String, Bean> beanPostProcessors = filterBeanImplementsInterface(allBeans, "BeanPostProcessor");
        Map<String, Bean> beanDefinitionPostProcessors = filterBeanImplementsInterface(allBeans, "BeanFactoryPostProcessor");

        beanPostProcessors.keySet().forEach(beanDefinitions::remove);
        beanDefinitionPostProcessors.keySet().forEach(beanDefinitions::remove);

        postProcessBeanDefinitions((List<BeanDefinition>) beanDefinitions.values(), beanDefinitionPostProcessors);

        beans = createBeans(beanDefinitions);

        beans = postProcessBeans(beans, beanPostProcessors, "postProcessBeforeInitialization");
        runInitMethods(beans);
        beans = postProcessBeans(beans, beanPostProcessors, "postProcessAfterInitialization");

        beanPostProcessors.keySet().forEach(beans::remove);
        beanDefinitionPostProcessors.keySet().forEach(beans::remove);

        injectValueDependencies(beanDefinitions, beans);
        injectRefDependencies(beanDefinitions, beans);
    }

    void runInitMethods(Map<String, Bean> beans) {
        beans.forEach((key, value) -> {
            Object object = value.getValue();

            Arrays.stream(object.getClass().getDeclaredMethods()).forEach(
                    method -> {
                        if (method.getAnnotation(PostConstruct.class) == null) {
                            return;
                        }
                        try {
                            method.setAccessible(true);
                            method.invoke(object);
                        } catch (Exception e) {
                            throw new PostProcessBeanFactoryException("Exception while run post construct method on bean with id: " + key, e);
                        }
                    }
            );
        });
    }

    Map<String, Bean> postProcessBeans(Map<String, Bean> beans, Map<String, Bean> systemBeans, String methodName) {
        return beans.entrySet().stream().map(entry -> {

            Object object = entry.getValue().getValue();
            for (Bean bean : systemBeans.values()) {
                try {
                    Method postProcessBeforeInitializationMethod = bean.getValue().getClass()
                            .getDeclaredMethod(methodName, Object.class, String.class);

                    object = postProcessBeforeInitializationMethod.invoke(bean.getValue(), object, entry.getKey());
                } catch (Exception e) {
                    throw new PostProcessBeanFactoryException("Exception while post process bean: " + entry.getKey(), e);
                }
            }
            return Bean.builder()
                    .value(object)
                    .id(entry.getKey())
                    .build();

        }).collect(toMap(Bean::getId, bean -> bean));
    }

    void postProcessBeanDefinitions(List<BeanDefinition> beanDefinitionList, Map<String, Bean> systemBeans) {
        systemBeans.values().forEach(entry -> {
            Object objectForProcess = entry.getValue();
            try {
                Method postProcessBeanFactoryMethod = objectForProcess.getClass()
                        .getDeclaredMethod("postProcessBeanFactory", List.class);
                postProcessBeanFactoryMethod.invoke(objectForProcess, beanDefinitionList);
            } catch (Exception e) {
                throw new PostProcessBeanFactoryException("Exception while post process bean definition " + entry.getId(), e);
            }
        });
    }

    @Override
    public Object getBean(String beanId) {
        List<Bean> beansById = beans.values().stream()
                .filter(bean -> Objects.equals(bean.getId(), beanId))
                .collect(toList());
        checkIfOneBeanExist(beansById, null, beanId);

        return beansById.get(0).getValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        List<Bean> beansByClass = beans.values().stream()
                .filter(bean -> Objects.equals(bean.getValue().getClass(), clazz))
                .collect(toList());
        checkIfOneBeanExist(beansByClass, clazz, null);

        return (T) beansByClass.get(0).getValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(String id, Class<T> clazz) {
        List<Bean> resultBeans = beans.values().stream()
                .filter(bean -> Objects.equals(bean.getValue().getClass(), clazz) && Objects.equals(bean.getId(), id))
                .collect(toList());
        checkIfOneBeanExist(resultBeans, clazz, id);

        return (T) resultBeans.get(0).getValue();
    }

    @Override
    public List<String> getBeanNames() {
        return new ArrayList<>(beans.keySet());
    }

    Map<String, Bean> createBeans(Map<String, BeanDefinition> beanDefinitionMap) {
        Map<String, Bean> result = new HashMap<>();
        beanDefinitionMap.forEach((key, value) -> {
            try {
                Constructor<?> constructor = Class.forName(value.getClassName()).getConstructor();
                result.put(key, new Bean(value.getId(), constructor.newInstance()));
            } catch (Exception e) {
                throw new BeanInstantiationException("Exception while create bean with id: " + key, e);
            }
        });
        return result;
    }

    void injectValueDependencies(Map<String, BeanDefinition> beanDefinitions, Map<String, Bean> beans) {
        beanDefinitions.values().forEach(beanDefinition -> {
            beanDefinition.getValueDependencies().forEach((fieldName, value) -> {
                Object beanForInject = beans.get(beanDefinition.getId()).getValue();
                try {
                    injectValue(beanForInject, getSetter(beanForInject, fieldName), value);
                } catch (Exception e) {
                    throw new BeanInstantiationException("Can't create bean with bean definition: " + beanDefinition, e);
                }
            });
        });
    }

    void injectRefDependencies(Map<String, BeanDefinition> beanDefinitions, Map<String, Bean> beans) {
        beanDefinitions.forEach((beanDefKey, value) -> value.getRefDependencies().forEach((key, injectedBeanName) -> {
            Object beanForInject = beans.get(beanDefKey).getValue();
            Method setter = getSetter(beanForInject, key);

            try {
                setter.invoke(beanForInject, beans.get(injectedBeanName).getValue());
            } catch (Exception e) {
                throw new BeanInstantiationException("Exception while inject reference dependency bean with id: " + beanDefKey, e);
            }
        }));
    }

    void injectValue(Object object, Method classMethod, String propertyValue) throws ReflectiveOperationException {
        classMethod.invoke(object, castValue(propertyValue, classMethod.getParameterTypes()[0]));
    }

    void setBeans(Map<String, Bean> beans) {
        this.beans = beans;
    }

    private String getSetterName(String fieldName) {
        return "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }

    private void checkIfOneBeanExist(List<Bean> beans, Class<?> clazz, String id) {
        if (beans.isEmpty()) {
            throw new NoSuchBeanDefinitionException(id, clazz.getCanonicalName());
        }
        if (beans.size() > 1) {
            throw new NoUniqueBeanOfTypeException("Found " + beans.size() + " beans of " + clazz + " class.");
        }
    }

    private Object castValue(String propertyValue, Class<?> clazz) {
        String type = clazz.getCanonicalName();

        if (int.class.getCanonicalName().equals(type) || Integer.class.getCanonicalName().equals(type)) {
            return Integer.valueOf(propertyValue);

        } else if (byte.class.getCanonicalName().equals(type) || Byte.class.getCanonicalName().equals(type)) {
            return Byte.valueOf(propertyValue);

        } else if (short.class.getCanonicalName().equals(type) || Short.class.getCanonicalName().equals(type)) {
            return Short.valueOf(propertyValue);

        } else if (long.class.getCanonicalName().equals(type) || Long.class.getCanonicalName().equals(type)) {
            return Long.valueOf(propertyValue);

        } else if (boolean.class.getCanonicalName().equals(type) || Boolean.class.getCanonicalName().equals(type)) {
            return Boolean.valueOf(propertyValue);

        } else {
            return clazz.cast(propertyValue);
        }
    }

    private Method getSetter(Object beanForInject, String fieldName) {
        String setterName = getSetterName(fieldName);
        Optional<Method> setter = Arrays.stream(beanForInject.getClass().getDeclaredMethods())
                .filter(method -> method.getName().equals(setterName))
                .findFirst();
        if (!setter.isPresent()) {
            throw new IllegalArgumentException("Setter for field: " + fieldName + " is not present.");
        }
        return setter.get();
    }

    private Map<String, Bean> filterBeanImplementsInterface(Map<String, Bean> beans, String interfaceName) {
        return beans.entrySet().stream()
                .filter(bean -> Arrays.stream(bean.getValue().getValue().getClass().getInterfaces())
                        .anyMatch(i -> i.getCanonicalName().equals(interfaceName)))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
