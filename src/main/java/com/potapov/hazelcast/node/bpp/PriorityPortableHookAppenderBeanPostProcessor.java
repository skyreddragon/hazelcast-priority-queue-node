package com.potapov.hazelcast.node.bpp;

import com.hazelcast.config.SerializationConfig;
import com.potapov.hazelcast.node.config.PriorityPortableHook;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Created by днс on 16.01.2017.
 */
public class PriorityPortableHookAppenderBeanPostProcessor implements BeanPostProcessor {

    public Object postProcessBeforeInitialization(Object bean, String name) throws BeansException {
        return bean;
    }

    public Object postProcessAfterInitialization(Object bean, String name) throws BeansException {
        if (bean instanceof SerializationConfig) {
            PriorityPortableHook hook = new PriorityPortableHook();
            ((SerializationConfig) bean).addPortableFactory(PriorityPortableHook.F_ID, hook.createFactory());
        }
        return bean;
    }
}
