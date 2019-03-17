package net.serenitybdd.cucumber.suiteslicing;

import org.junit.Before;
import org.junit.Test;

import java.net.URI;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;

public class CucumberScenarioLoaderTest {

    private TestStatistics testStatistics;

    @Before
    public void setup() {
        testStatistics = new DummyStatsOfWeightingOne();
    }

    @Test
    public void shouldEnsureThatFeaturesWithBackgroundsDontCountThemAsScenarios() throws Exception {
        URI uri = getClass().getClassLoader().getResource("samples/simple_table_based_scenario.feature").toURI();
        WeightedCucumberScenarios weightedCucumberScenarios = new CucumberScenarioLoader(newArrayList(uri), testStatistics).load();
        assertThat(weightedCucumberScenarios.scenarios, containsInAnyOrder(MatchingCucumberScenario.with()
                                                                               .featurePath("simple_table_based_scenario.feature")
                                                                               .feature("Buying things - with tables")
                                                                               .scenario("Buying lots of widgets"),
                                                                           MatchingCucumberScenario.with()
                                                                               .featurePath("simple_table_based_scenario.feature")
                                                                               .feature("Buying things - with tables")
                                                                               .scenario("Buying more widgets")));
    }

    @Test
    public void shouldLoadFeatureAndScenarioTagsOntoCorrectScenarios() throws Exception {
        URI uri = getClass().getClassLoader().getResource("samples/simple_table_based_scenario.feature").toURI();
        WeightedCucumberScenarios weightedCucumberScenarios = new CucumberScenarioLoader(newArrayList(uri), testStatistics).load();

        assertThat(weightedCucumberScenarios.scenarios, contains(MatchingCucumberScenario.with()
                                                                     .featurePath("simple_table_based_scenario.feature")
                                                                     .feature("Buying things - with tables")
                                                                     .scenario("Buying lots of widgets")
                                                                     .tags("@shouldPass"),
                                                                 MatchingCucumberScenario.with()
                                                                     .featurePath("simple_table_based_scenario.feature")
                                                                     .feature("Buying things - with tables")
                                                                     .scenario("Buying more widgets")
                                                                     .tags()));
    }


}
