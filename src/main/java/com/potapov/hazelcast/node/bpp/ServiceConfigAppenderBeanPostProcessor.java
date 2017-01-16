package com.potapov.hazelcast.node.bpp;

import com.hazelcast.config.ServiceConfig;
import com.hazelcast.config.ServicesConfig;
import com.potapov.hazelcast.node.config.PriorityQueueService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Created by днс on 16.01.2017.
 */
public class ServiceConfigAppenderBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String name) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) throws BeansException {
        if (bean instanceof ServicesConfig) {
            final ServiceConfig serviceConfig = new ServiceConfig();
            serviceConfig.setEnabled(true);
            serviceConfig.setClassName(PriorityQueueService.class.getName());
            serviceConfig.setName(PriorityQueueService.SERVICE_NAME);
            ((ServicesConfig) bean).addServiceConfig(serviceConfig);
        }
        return bean;
    }
}
