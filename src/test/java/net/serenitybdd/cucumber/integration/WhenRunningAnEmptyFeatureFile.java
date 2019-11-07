package net.serenitybdd.cucumber.integration;

import cucumber.api.CucumberOptions;
import io.cucumber.junit.CucumberWithSerenity;
import org.junit.runner.RunWith;

/**
 * Run all of the passing sample scenarios - a quick smoke test
 */
@RunWith(CucumberWithSerenity.class)
@CucumberOptions(features = {"src/test/resources/samples/empty"}, tags = "@shouldPass")
public class WhenRunningAnEmptyFeatureFile {
}