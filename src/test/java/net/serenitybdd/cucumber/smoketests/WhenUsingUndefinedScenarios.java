package net.serenitybdd.cucumber.smoketests;

import cucumber.api.CucumberOptions;
import io.cucumber.junit.CucumberWithSerenity;
import org.junit.runner.RunWith;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(features="src/test/resources/smoketests/undefined_scenarios.feature")
public class WhenUsingUndefinedScenarios {}
