package net.serenitybdd.cucumber.smoketests;

import cucumber.api.CucumberOptions;
import io.cucumber.junit.CucumberWithSerenity;
import org.junit.runner.RunWith;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(features="classpath:smoketests/using_background_steps.feature")
public class WhenUsingBackgroundSteps {}
