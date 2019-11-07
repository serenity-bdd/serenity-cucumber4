package net.serenitybdd.cucumber.integration;

import cucumber.api.CucumberOptions;
import io.cucumber.junit.CucumberWithSerenity;
import org.junit.runner.RunWith;


@RunWith(CucumberWithSerenity.class)
@CucumberOptions(features="src/test/resources/samples/manual_table_based_numbers.feature")
public class TableScenarioMarkedAsManual {}
