package org.mengyun.tcctransaction.spring.support;

import org.mengyun.tcctransaction.support.BeanFactory;
import org.mengyun.tcctransaction.support.FactoryBuilder;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;

/**
 * Created by changmingxie on 11/22/15.
 * 用于Spring的BeanFactory
 */
public class SpringBeanFactory implements BeanFactory, ApplicationContextAware {

    private ApplicationContext applicationContext;

    /**
     * 注入应用上下文
     * @param applicationContext 应用上下文
     * @throws BeansException BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        // 注册bean工厂
        FactoryBuilder.registerBeanFactory(this);
    }

    @Override
    public boolean isFactoryOf(Class clazz) {
        Map map = this.applicationContext.getBeansOfType(clazz);
        return map.size() > 0;
    }

    @Override
    public <T> T getBean(Class<T> var1) {
        return this.applicationContext.getBean(var1);
    }
}
