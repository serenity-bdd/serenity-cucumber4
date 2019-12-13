package cucumber.runtime.formatter;

import cucumber.api.event.TestSourceRead;
import gherkin.ast.Feature;
import net.thucydides.core.util.Inflector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class FeatureFileLoader {

    private final TestSourcesModel testSources = new TestSourcesModel();

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureFileLoader.class);

    private Optional<Feature> featureFrom(String featureFileUri) {

        String defaultFeatureId = new File(featureFileUri).getName().replace(".feature", "");
        String defaultFeatureName = Inflector.getInstance().humanize(defaultFeatureId);

        parseGherkinIn(featureFileUri);

        if (isEmpty(testSources.getFeatureName(featureFileUri))) {
            return Optional.empty();
        }

        Feature feature = testSources.getFeature(featureFileUri);
        if (feature.getName().isEmpty()) {
            feature = featureWithDefaultName(feature, defaultFeatureName);
        }
        return Optional.of(feature);
    }

    private void parseGherkinIn(String featureFileUri) {
        try {
            testSources.getFeature(featureFileUri);
        } catch (Throwable ignoreParsingErrors) {
            LOGGER.warn("Could not parse the Gherkin in feature file " + featureFileUri + ": file ignored");
        }
    }

    public Feature featureWithDefaultName(Feature feature, String defaultName) {
        return new Feature(feature.getTags(),
                feature.getLocation(),
                feature.getLanguage(),
                feature.getKeyword(),
                defaultName,
                feature.getDescription(),
                feature.getChildren());
    }

    public void addTestSourceReadEvent(String path, TestSourceRead event) {
        testSources.addTestSourceReadEvent(event.uri, event);
    }

    public String getFeatureName(String featureFileUri) {
        return testSources.getFeatureName(featureFileUri);
    }

    public Feature getFeature(String featureFileUri) {
        return testSources.getFeature(featureFileUri);
    }

    TestSourcesModel.AstNode getAstNode(String path, int line) {
        return testSources.getAstNode(path,line);
    }
}
