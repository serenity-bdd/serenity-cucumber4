package cucumber.runtime.formatter;

import net.serenitybdd.cucumber.CucumberWithSerenity;

import java.net.URI;
import java.util.*;

public class FeaturePathFormatter {

    private LineFilters lineFilters;

    public FeaturePathFormatter() {
        this.lineFilters = LineFilters.forCurrentContext();
    }

    public String featurePathWithPrefixIfNecessary(final String featurePath) {
        return lineFilters
                .getURIForFeaturePath(featurePath)
                .map(matchingURI -> featurePathWithPrefix(matchingURI, featurePath))
                .orElse(featurePath);
    }

    private String featurePathWithPrefix(URI featurePathUri, String featurePath) {
        Set<Integer> allLineNumbersSet = lineFilters.getLineNumbersFor(featurePathUri);
        List<Integer> allLineNumbersList = new ArrayList<>(allLineNumbersSet);
        long featurePathPrefix = allLineNumbersList.get(0);
        return featurePath + ":" + featurePathPrefix;
    }

    private URI getURIForFeaturePath(Map<URI, Set<Integer>> map, String featurePath) {
        for (URI currentURI : map.keySet()) {
            if (featurePath.equals(currentURI.toString())) {
                return currentURI;
            }
        }
        return null;
    }
}
