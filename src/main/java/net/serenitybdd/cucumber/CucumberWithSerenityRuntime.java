package net.serenitybdd.cucumber;

import cucumber.runtime.ClassFinder;
import cucumber.runtime.Runtime;
import io.cucumber.core.options.RuntimeOptions;
import cucumber.runtime.formatter.SerenityReporter;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.io.ResourceLoaderClassFinder;
import net.thucydides.core.guice.Injectors;
import net.thucydides.core.webdriver.Configuration;

import java.util.Optional;

public class CucumberWithSerenityRuntime {

    public static Runtime using(ResourceLoader resourceLoader,
                                ClassLoader classLoader,
                                ClassFinder classFinder,
                                RuntimeOptions runtimeOptions) {
        Configuration systemConfiguration = Injectors.getInjector().getInstance(Configuration.class);
        return createSerenityEnabledRuntime(resourceLoader, classLoader, classFinder, runtimeOptions, systemConfiguration);
    }

    public static Runtime using(ResourceLoader resourceLoader, ClassLoader classLoader, RuntimeOptions runtimeOptions) {
        Configuration systemConfiguration = Injectors.getInjector().getInstance(Configuration.class);
        return createSerenityEnabledRuntime(resourceLoader, classLoader, null, runtimeOptions, systemConfiguration);
    }

    private static Runtime createSerenityEnabledRuntime(ResourceLoader resourceLoader,
                                                        ClassLoader classLoader,
                                                        ClassFinder classFinder,
                                                        RuntimeOptions runtimeOptions,
                                                        Configuration systemConfiguration) {
        ClassFinder resolvedClassFinder = Optional.ofNullable(classFinder).orElse(new ResourceLoaderClassFinder(resourceLoader, classLoader));
        SerenityReporter reporter = new SerenityReporter(systemConfiguration, resourceLoader);
        Runtime runtime = Runtime.builder().withResourceLoader(resourceLoader).withClassFinder(resolvedClassFinder).
                withClassLoader(classLoader).withRuntimeOptions(runtimeOptions).withAdditionalPlugins(reporter).build();
        return runtime;
    }
}
