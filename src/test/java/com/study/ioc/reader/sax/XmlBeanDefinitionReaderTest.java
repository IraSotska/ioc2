package com.study.ioc.reader.sax;

import com.study.ioc.entity.BeanDefinition;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XmlBeanDefinitionReaderTest {

    private static final String CONTEXT_XML = "<beans>\n" +
            "    <bean id=\"mailServicePOP\" class=\"com.study.entity.MailService\">\n" +
            "        <property name=\"port\" value=\"995\"/>\n" +
            "        <property name=\"protocol\" value=\"POP3\"/>\n" +
            "    </bean>\n" +
            "\n" +
            "    <bean id=\"userService\" class=\"com.study.entity.DefaultUserService\">\n" +
            "        <property name=\"mailService\" ref=\"mailServicePOP\"/>\n" +
            "    </bean>\n" +
            "\n" +
            "    <bean id=\"mailServiceIMAP\" class=\"com.study.entity.MailService\">\n" +
            "        <property name=\"port\" value=\"143\"/>\n" +
            "        <property name=\"protocol\" value=\"IMAP\"/>\n" +
            "    </bean>\n" +
            "</beans>";


    @Test
    public void testGetBeanDefinitionMap() throws Exception {
        XmlBeanDefinitionReader xmlBeanDefinitionReader = new XmlBeanDefinitionReader();
        Map<String, BeanDefinition> beanDefinitionMap = xmlBeanDefinitionReader.getBeanDefinitionMap(new ByteArrayInputStream(CONTEXT_XML.getBytes()));

        assertEquals(3, beanDefinitionMap.size());
        BeanDefinition beanDefinition1 = beanDefinitionMap.get("mailServicePOP");
        assertEquals("mailServicePOP", beanDefinition1.getId());
        assertEquals("com.study.entity.MailService", beanDefinition1.getClassName());

        assertTrue(beanDefinition1.getRefDependencies().isEmpty());
        Map<String, String> valueDependencies1 = beanDefinition1.getValueDependencies();
        assertEquals(2, valueDependencies1.size());
        assertTrue(valueDependencies1.containsKey("port"));
        assertEquals("995", valueDependencies1.get("port"));
        assertTrue(valueDependencies1.containsKey("protocol"));
        assertEquals("POP3", valueDependencies1.get("protocol"));

        BeanDefinition beanDefinition2 = beanDefinitionMap.get("userService");
        assertEquals("userService", beanDefinition2.getId());
        assertEquals("com.study.entity.DefaultUserService", beanDefinition2.getClassName());

        assertTrue(beanDefinition2.getValueDependencies().isEmpty());
        Map<String, String> refDependencies2 = beanDefinition2.getRefDependencies();
        assertEquals(1, refDependencies2.size());
        assertTrue(refDependencies2.containsKey("mailService"));
        assertEquals("mailServicePOP", refDependencies2.get("mailService"));

        BeanDefinition beanDefinition3 = beanDefinitionMap.get("mailServiceIMAP");
        assertEquals("mailServiceIMAP", beanDefinition3.getId());
        assertEquals("com.study.entity.MailService", beanDefinition3.getClassName());

        assertTrue(beanDefinition3.getRefDependencies().isEmpty());
        Map<String, String> valueDependencies3 = beanDefinition3.getValueDependencies();
        assertEquals(2, valueDependencies3.size());
        assertTrue(valueDependencies3.containsKey("port"));
        assertEquals("143", valueDependencies3.get("port"));
        assertTrue(valueDependencies3.containsKey("protocol"));
        assertEquals("IMAP", valueDependencies3.get("protocol"));

    }
}
