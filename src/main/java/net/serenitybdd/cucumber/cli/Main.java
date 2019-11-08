package net.serenitybdd.cucumber.cli;

import cucumber.runtime.ClassFinder;
import cucumber.runtime.Runtime;
import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.io.ResourceLoaderClassFinder;
import io.cucumber.core.options.CommandlineOptionsParser;
import io.cucumber.core.options.RuntimeOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import net.serenitybdd.cucumber.CucumberWithSerenityRuntime;

import java.io.IOException;

public class Main {

    public static void main(String[] argv) throws Throwable {
        byte exitstatus = run(argv, Thread.currentThread().getContextClassLoader());
        System.exit(exitstatus);
    }

    public static byte run(String[] argv, ClassLoader classLoader) throws IOException {
        RuntimeOptions  runtimeOptions = new CommandlineOptionsParser().parse(argv).build() ;
        ResourceLoader resourceLoader = new MultiLoader(classLoader);
        ClassFinder classFinder = new ResourceLoaderClassFinder(resourceLoader, classLoader);
        CucumberWithSerenity.setRuntimeOptions(runtimeOptions);
        Runtime runtime =  CucumberWithSerenityRuntime.using(resourceLoader, classLoader, classFinder, runtimeOptions);


        runtime.run();
        return runtime.exitStatus();
    }
}
