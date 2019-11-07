package net.serenitybdd.cucumber.smoketests;

import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(features="src/test/resources/smoketests/undefined_scenarios.feature")
public class WhenUsingUndefinedScenarios {}
