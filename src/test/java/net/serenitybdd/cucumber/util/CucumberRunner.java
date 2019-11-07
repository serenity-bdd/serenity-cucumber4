package net.serenitybdd.cucumber.util;

import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.ResourceLoader;
import io.cucumber.core.options.CucumberOptionsAnnotationParser;
import io.cucumber.core.options.RuntimeOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import net.thucydides.core.configuration.SystemPropertiesConfiguration;
import net.thucydides.core.util.EnvironmentVariables;
import net.thucydides.core.util.SystemEnvironmentVariables;
import net.thucydides.core.webdriver.Configuration;
import org.junit.runner.Computer;
import org.junit.runner.JUnitCore;

import java.io.File;

/**
 * Created by john on 31/07/2014.
 */
public class CucumberRunner {

    public static void run(Class testClass) {
        JUnitCore jUnitCore = new JUnitCore();
        jUnitCore.run(new Computer(), testClass);
    }

    public static cucumber.runtime.Runtime serenityRunnerForCucumberTestRunner(Class testClass, File outputDirectory) {
        return serenityRunnerForCucumberTestRunner(testClass, outputDirectory, new SystemEnvironmentVariables());
    }

    public static cucumber.runtime.Runtime serenityRunnerForCucumberTestRunner(Class testClass,
                                                                               File outputDirectory,
                                                                               EnvironmentVariables environmentVariables) {
        ClassLoader classLoader = testClass.getClassLoader();
        ResourceLoader resourceLoader = new MultiLoader(classLoader);
        RuntimeOptions runtimeOptions = new CucumberOptionsAnnotationParser().parse(testClass).build();

        Configuration systemConfiguration = new SystemPropertiesConfiguration(environmentVariables);
        systemConfiguration.setOutputDirectory(outputDirectory);
        return CucumberWithSerenity.createSerenityEnabledRuntime(resourceLoader, classLoader, runtimeOptions, systemConfiguration);
    }

    public static cucumber.runtime.Runtime serenityRunnerForCucumberTestRunner(Class testClass, Configuration systemConfiguration) {
        ClassLoader classLoader = testClass.getClassLoader();
        ResourceLoader resourceLoader = new MultiLoader(classLoader);
        RuntimeOptions runtimeOptions = new CucumberOptionsAnnotationParser().parse(testClass).build();

        return CucumberWithSerenity.createSerenityEnabledRuntime(resourceLoader, classLoader, runtimeOptions, systemConfiguration);
    }

}
