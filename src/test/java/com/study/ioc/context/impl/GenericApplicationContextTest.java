package com.study.ioc.context.impl;

import com.study.entity.DefaultUserService;
import com.study.entity.MailService;
import com.study.ioc.entity.Bean;
import com.study.ioc.entity.BeanDefinition;
import com.study.ioc.exception.BeanInstantiationException;
import com.study.ioc.exception.NoSuchBeanDefinitionException;
import com.study.ioc.exception.NoUniqueBeanOfTypeException;
import com.study.processor.TestBeanFactoryPostProcessor;
import com.study.processor.TestPostProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GenericApplicationContextTest {

    private GenericApplicationContext genericApplicationContext;

    @BeforeEach
    public void before() {
        genericApplicationContext = new GenericApplicationContext();
    }

    @Test
    public void testCreateBeans() {
        Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();
        BeanDefinition beanDefinitionMailService = new BeanDefinition("mailServicePOP", "com.study.entity.MailService");
        beanDefinitionMap.put("mailServicePOP", beanDefinitionMailService);
        BeanDefinition beanDefinitionUserService = new BeanDefinition("userService", "com.study.entity.DefaultUserService");
        beanDefinitionMap.put("userService", beanDefinitionUserService);

        Map<String, Bean> beanMap = genericApplicationContext.createBeans(beanDefinitionMap);

        Bean actualMailBean = beanMap.get("mailServicePOP");
        assertNotNull(actualMailBean);
        assertEquals("mailServicePOP", actualMailBean.getId());
        assertEquals(MailService.class, actualMailBean.getValue().getClass());

        Bean actualUserBean = beanMap.get("userService");
        assertNotNull(actualUserBean);
        assertEquals("userService", actualUserBean.getId());
        assertEquals(DefaultUserService.class, actualUserBean.getValue().getClass());
    }


    @Test
    public void testCreateBeansWithWrongClass() {
        Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();
        BeanDefinition errorBeanDefinition = new BeanDefinition("mailServicePOP", "com.study.entity.TestClass");
        beanDefinitionMap.put("mailServicePOP", errorBeanDefinition);

        assertThrows(BeanInstantiationException.class, () -> {
            genericApplicationContext.createBeans(beanDefinitionMap);
        });
    }

    @Test
    public void testGetBeanById() {
        Map<String, Bean> beanMap = new HashMap<>();
        DefaultUserService beanValue1 = new DefaultUserService();
        DefaultUserService beanValue2 = new DefaultUserService();
        beanMap.put("bean1", new Bean("bean1", beanValue1));
        beanMap.put("bean2", new Bean("bean2", beanValue2));
        genericApplicationContext.setBeans(beanMap);
        DefaultUserService actualBeanValue1 = (DefaultUserService) genericApplicationContext.getBean("bean1");
        DefaultUserService actualBeanValue2 = (DefaultUserService) genericApplicationContext.getBean("bean2");
        assertNotNull(actualBeanValue1);
        assertNotNull(actualBeanValue2);
        assertEquals(beanValue1, actualBeanValue1);
        assertEquals(beanValue2, actualBeanValue2);
    }

    @Test
    public void testGetBeanByClazz() {
        Map<String, Bean> beanMap = new HashMap<>();
        DefaultUserService beanValue1 = new DefaultUserService();
        MailService beanValue2 = new MailService();
        beanMap.put("bean1", new Bean("bean1", beanValue1));
        beanMap.put("bean2", new Bean("bean2", beanValue2));
        genericApplicationContext.setBeans(beanMap);
        DefaultUserService actualBeanValue1 = genericApplicationContext.getBean(DefaultUserService.class);
        MailService actualBeanValue2 = genericApplicationContext.getBean(MailService.class);
        assertNotNull(actualBeanValue1);
        assertNotNull(actualBeanValue2);
        assertEquals(beanValue1, actualBeanValue1);
        assertEquals(beanValue2, actualBeanValue2);
    }

    @Test
    public void testGetBeanByClazzNoUniqueBean() {
        Map<String, Bean> beanMap = new HashMap<>();
        beanMap.put("bean1", new Bean("bean1", new DefaultUserService()));
        beanMap.put("bean2", new Bean("bean2", new DefaultUserService()));
        genericApplicationContext.setBeans(beanMap);

        assertThrows(NoUniqueBeanOfTypeException.class, () -> {
            genericApplicationContext.getBean(DefaultUserService.class);
        });

    }

    @Test
    public void testGetBeanByIdAndClazz() {
        Map<String, Bean> beanMap = new HashMap<>();
        DefaultUserService beanValue1 = new DefaultUserService();
        DefaultUserService beanValue2 = new DefaultUserService();
        beanMap.put("bean1", new Bean("bean1", beanValue1));
        beanMap.put("bean2", new Bean("bean2", beanValue2));
        genericApplicationContext.setBeans(beanMap);
        DefaultUserService actualBeanValue1 = genericApplicationContext.getBean("bean1", DefaultUserService.class);
        DefaultUserService actualBeanValue2 = genericApplicationContext.getBean("bean2", DefaultUserService.class);
        assertNotNull(actualBeanValue1);
        assertNotNull(actualBeanValue2);
        assertEquals(beanValue1, actualBeanValue1);
        assertEquals(beanValue2, actualBeanValue2);
    }

    @Test
    public void testGetBeanByIdAndClazzNoSuchBean() {
        Map<String, Bean> beanMap = new HashMap<>();
        DefaultUserService beanValue = new DefaultUserService();
        beanMap.put("bean1", new Bean("bean1", beanValue));
        genericApplicationContext.setBeans(beanMap);

        assertThrows(NoSuchBeanDefinitionException.class, () -> {
            genericApplicationContext.getBean("bean1", MailService.class);
        });
    }

    @Test
    public void getBeanNames() {
        Map<String, Bean> beanMap = new HashMap<>();
        beanMap.put("bean3", new Bean("bean3", new DefaultUserService()));
        beanMap.put("bean4", new Bean("bean4", new DefaultUserService()));
        beanMap.put("bean5", new Bean("bean5", new DefaultUserService()));
        genericApplicationContext.setBeans(beanMap);
        List<String> actualBeansNames = genericApplicationContext.getBeanNames();
        List<String> expectedBeansNames = Arrays.asList("bean3", "bean4", "bean5");
        assertTrue(actualBeansNames.containsAll(expectedBeansNames));
        assertTrue(expectedBeansNames.containsAll(actualBeansNames));
    }

    @Test
    public void testInjectValueDependencies() {
        Map<String, Bean> beanMap = new HashMap<>();
        Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();

        MailService mailServicePOP = new MailService();
        beanMap.put("mailServicePOP", new Bean("mailServicePOP", mailServicePOP));
        MailService mailServiceIMAP = new MailService();
        beanMap.put("mailServiceIMAP", new Bean("mailServiceIMAP", mailServiceIMAP));

        //  setPort(110) and setProtocol("POP3") via valueDependencies
        BeanDefinition popServiceBeanDefinition = new BeanDefinition("mailServicePOP", "com.study.entity.MailService");
        Map<String, String> popServiceValueDependencies = new HashMap<>();
        popServiceValueDependencies.put("port", "110");
        popServiceValueDependencies.put("protocol", "POP3");
        popServiceBeanDefinition.setValueDependencies(popServiceValueDependencies);
        beanDefinitionMap.put("mailServicePOP", popServiceBeanDefinition);

        //  setPort(143) and setProtocol("IMAP") via valueDependencies
        BeanDefinition imapServiceBeanDefinition = new BeanDefinition("mailServiceIMAP", "com.study.entity.MailService");
        Map<String, String> imapServiceValueDependencies = new HashMap<>();
        imapServiceValueDependencies.put("port", "143");
        imapServiceValueDependencies.put("protocol", "IMAP");
        imapServiceBeanDefinition.setValueDependencies(imapServiceValueDependencies);
        beanDefinitionMap.put("mailServiceIMAP", imapServiceBeanDefinition);

        genericApplicationContext.injectValueDependencies(beanDefinitionMap, beanMap);
        assertEquals(110, mailServicePOP.getPort());
        assertEquals("POP3", mailServicePOP.getProtocol());
        assertEquals(143, mailServiceIMAP.getPort());
        assertEquals("IMAP", mailServiceIMAP.getProtocol());
    }

    @Test
    public void testInjectRefDependencies() {
        Map<String, Bean> beanMap = new HashMap<>();
        Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();

        MailService mailServicePOP = new MailService();
        mailServicePOP.setPort(110);
        mailServicePOP.setProtocol("POP3");
        beanMap.put("mailServicePOP", new Bean("mailServicePOP", mailServicePOP));

        DefaultUserService userService = new DefaultUserService();
        beanMap.put("userService", new Bean("userService", userService));

        //  setMailService(mailServicePOP) via refDependencies
        BeanDefinition userServiceBeanDefinition = new BeanDefinition("userService", "com.study.entity.DefaultUserService");
        Map<String, String> userServiceRefDependencies = new HashMap<>();
        userServiceRefDependencies.put("mailService", "mailServicePOP");
        userServiceBeanDefinition.setRefDependencies(userServiceRefDependencies);
        beanDefinitionMap.put("userService", userServiceBeanDefinition);

        genericApplicationContext.injectRefDependencies(beanDefinitionMap, beanMap);
        assertNotNull(userService.getMailService());
        assertEquals(110, ((MailService) userService.getMailService()).getPort());
        assertEquals("POP3", ((MailService) userService.getMailService()).getProtocol());
    }

    @Test
    public void testInjectValue() throws ReflectiveOperationException {
        MailService mailService = new MailService();
        Method setPortMethod = MailService.class.getDeclaredMethod("setPort", Integer.TYPE);
        genericApplicationContext.injectValue(mailService, setPortMethod, "465");
        int actualPort = mailService.getPort();
        assertEquals(465, actualPort);
    }

    @Test
    public void testPostProcessBeans() {
        Map<String, Bean> beans = Map.of("MyMailService", Bean.builder()
                .id("MyMailService")
                .value(new MailService())
                .build());

        Map<String, Bean> systemBeans = Map.of("TestPostProcessor", Bean.builder()
                .id("TestPostProcessor")
                .value(new TestPostProcessor())
                .build());

        Map<String, Bean> postProcessedBeanMap = genericApplicationContext.postProcessBeans(beans, systemBeans,
                "postProcessBeforeInitialization");

        assertEquals("MyMailService", postProcessedBeanMap.get("MyMailService").getValue());
    }

    @Test
    public void testRunInitMethodsForBeans() {
        Map<String, Bean> beans = Map.of("MyMailService", Bean.builder()
                .id("MyMailService")
                .value(MailService.builder()
                        .port(1000)
                        .protocol("protocol")
                        .build())
                .build());

        genericApplicationContext.runInitMethods(beans);

        assertEquals(2000, ((MailService) beans.get("MyMailService").getValue()).getPort());
    }

    @Test
    public void testPostProcessBeanDefinitions() {
        List<BeanDefinition> beanDefinitionList = List.of(BeanDefinition.builder()
                .className("className")
                .id("id")
                .valueDependencies(Map.of())
                .refDependencies(Map.of())
                .build());

        genericApplicationContext.postProcessBeanDefinitions(beanDefinitionList,
                Map.of("Key", Bean.builder()
                        .id("Key")
                        .value(new TestBeanFactoryPostProcessor()).build()));

        assertEquals("newClassName", beanDefinitionList.get(0).getClassName());
    }
}
