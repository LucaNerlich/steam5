package org.steam5.job;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Job classes here use constructor injection with final fields, so they have no no-arg
 * constructor for Quartz's default reflection-based instantiation to call. Returning the
 * existing Spring-managed singleton bean (already fully constructed) avoids that entirely;
 * falling back to the default behavior only covers Job types not managed by Spring.
 */
public final class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {

    private transient ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(final ApplicationContext context) {
        this.applicationContext = context;
    }

    @Override
    protected Object createJobInstance(final TriggerFiredBundle bundle) throws Exception {
        final Class<?> jobClass = bundle.getJobDetail().getJobClass();
        try {
            return applicationContext.getBean(jobClass);
        } catch (NoSuchBeanDefinitionException e) {
            return super.createJobInstance(bundle);
        }
    }

}
