package net.serenitybdd.cucumber.smoketests;

import io.cucumber.junit.CucumberOptions;
import io.cucumber.junit.CucumberWithSerenity;
import org.junit.runner.RunWith;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(features="src/test/resources/smoketests/using_fixture_methods.feature")
public class WhenUsingFixtureMethods {
}
