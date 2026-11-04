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

import io.github.ythibos.edi.adapter.windows.OutlookCalendar;

public final class EveryTestCoreReadsARecordingCalendar implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configurations) {
        return new RecordingInsteadOfReadingOutlook();
    }

    private record RecordingInsteadOfReadingOutlook() implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration configuration) {
            context.addBeanFactoryPostProcessor(RecordingInsteadOfReadingOutlook::replaceTheCaptainsCalendar);
        }

        private static void replaceTheCaptainsCalendar(ConfigurableListableBeanFactory beans) {
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beans;
            for (String calendar : beans.getBeanNamesForType(OutlookCalendar.class, true, false)) {
                registry.removeBeanDefinition(calendar);
                registry.registerBeanDefinition(calendar, new RootBeanDefinition(RecordingCalendar.class));
            }
        }
    }
}
