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
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Slf4j
@NoArgsConstructor
public class GenericApplicationContext implements ApplicationContext {

    private Map<String, Bean> beans;

    public GenericApplicationContext(String... paths) {
        this(new XmlBeanDefinitionReader(paths));
    }

    public GenericApplicationContext(BeanDefinitionReader definitionReader) {

        log.info("Read bean definitions.");
        Map<String, BeanDefinition> beanDefinitions = definitionReader.getBeanDefinition();
        Map<String, Bean> allBeans = createBeans(beanDefinitions);

        Map<String, Bean> beanPostProcessors = filterBeanImplementsInterface(allBeans, "com.study.ioc.processor.BeanPostProcessor");
        Map<String, Bean> beanDefinitionPostProcessors = filterBeanImplementsInterface(allBeans, "com.study.ioc.processor.BeanFactoryPostProcessor");

        beanPostProcessors.keySet().forEach(beanDefinitions::remove);
        beanDefinitionPostProcessors.keySet().forEach(beanDefinitions::remove);

        log.info("Post process bean definitions.");
        postProcessBeanDefinitions(beanDefinitions.values().stream().toList(), beanDefinitionPostProcessors);

        log.info("Instantiation of beans started.");
        beans = createBeans(beanDefinitions);

        log.info("Run post process before initialization methods on beans.");
        beans = postProcessBeans(beans, beanPostProcessors, "postProcessBeforeInitialization");
        log.info("Run init methods on beans start.");
        runInitMethods(beans);
        log.info("Run init methods on beans end.");
        log.info("Run post process after initialization methods on beans.");
        beans = postProcessBeans(beans, beanPostProcessors, "postProcessAfterInitialization");

        beanPostProcessors.keySet().forEach(beans::remove);
        beanDefinitionPostProcessors.keySet().forEach(beans::remove);

        log.info("Inject value dependencies.");
        injectValueDependencies(beanDefinitions, beans);
        log.info("Inject reference dependencies.");
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

        return checkIfOneBeanExistAndReturn(beansById, null, beanId);
    }

    @Override
    public <T> T getBean(Class<T> clazz) {
        List<Bean> beansByClass = beans.values().stream()
                .filter(bean -> Objects.equals(bean.getValue().getClass(), clazz))
                .collect(toList());

        return clazz.cast(checkIfOneBeanExistAndReturn(beansByClass, clazz, null));
    }

    @Override
    public <T> T getBean(String id, Class<T> clazz) {
        List<Bean> resultBeans = beans.values().stream()
                .filter(bean -> Objects.equals(bean.getValue().getClass(), clazz) && Objects.equals(bean.getId(), id))
                .collect(toList());

        return clazz.cast(checkIfOneBeanExistAndReturn(resultBeans, clazz, id));
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

    private Object checkIfOneBeanExistAndReturn(List<Bean> beans, Class<?> clazz, String id) {
        if (beans.isEmpty()) {
            throw new NoSuchBeanDefinitionException(id, clazz.getCanonicalName());
        }
        if (beans.size() > 1) {
            throw new NoUniqueBeanOfTypeException("Found " + beans.size() + " beans of " + clazz + " class.");
        }
        return beans.get(0).getValue();
    }

    private Object castValue(String propertyValue, Class<?> clazz) {
        if (int.class == clazz || Integer.class == clazz) {
            return Integer.valueOf(propertyValue);

        } else if (byte.class == clazz || Byte.class == clazz) {
            return Byte.valueOf(propertyValue);

        } else if (short.class == clazz || Short.class == clazz) {
            return Short.valueOf(propertyValue);

        } else if (long.class == clazz || Long.class == clazz) {
            return Long.valueOf(propertyValue);

        } else if (boolean.class == clazz || Boolean.class == clazz) {
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
        if (setter.isEmpty()) {
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
