package io.github.ythibos.edi.testing;

import java.util.List;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

import io.github.ythibos.edi.adapter.browser.CaptainsBrowser;

public final class EveryTestCoreRecordsItsBrowser implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configurations) {
        return new RecordingInsteadOfOpening();
    }

    private record RecordingInsteadOfOpening() implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration configuration) {
            context.addBeanFactoryPostProcessor(RecordingInsteadOfOpening::replaceTheCaptainsBrowser);
        }

        private static void replaceTheCaptainsBrowser(ConfigurableListableBeanFactory beans) {
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beans;
            for (String browser : beans.getBeanNamesForType(CaptainsBrowser.class, true, false)) {
                registry.removeBeanDefinition(browser);
                registry.registerBeanDefinition(browser, new RootBeanDefinition(RecordingBrowser.class));
            }
        }
    }
}
