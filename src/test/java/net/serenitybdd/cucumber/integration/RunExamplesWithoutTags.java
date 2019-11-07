package net.serenitybdd.cucumber.integration;

import cucumber.api.*;
import io.cucumber.junit.CucumberWithSerenity;
import org.junit.runner.*;


@RunWith(CucumberWithSerenity.class)
@CucumberOptions(features="src/test/resources/features/calculator/basic_arithmetic_with_tables_and_examples_no_tags.feature",tags = {"not @example_two"})
public class RunExamplesWithoutTags {}