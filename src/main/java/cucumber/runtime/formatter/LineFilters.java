package cucumber.runtime.formatter;

import gherkin.ast.Examples;
import gherkin.ast.TableRow;
import net.serenitybdd.cucumber.CucumberWithSerenity;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LineFilters {

    private Map<URI, Set<Integer>> lineFilters;

    public LineFilters() {
        lineFilters = newLineFilters();
    }

    public static LineFilters forCurrentContext() {
        return new LineFilters();
    }

    public Optional<URI> getURIForFeaturePath(String featurePath) {
        return lineFilters.keySet().stream()
                .filter(uri -> featurePath.equals(uri.toString()))
                .findFirst();
    }

    private Map<URI, Set<Integer>> newLineFilters() {
        Map<URI, Set<Integer>> lineFiltersFromRuntime = CucumberWithSerenity.currentRuntimeOptions().getLineFilters();
        if (lineFiltersFromRuntime == null) {
            return new HashMap<>();
        } else {
            return lineFiltersFromRuntime;
        }
    }

    public Set<Integer> getLineNumbersFor(URI featurePath) {
        return lineFilters.get(featurePath);
    }


    public boolean examplesAreNotExcluded(Examples examples, String featurePath) {
        if (lineFilters.isEmpty()) {
            return true;
        }
        if (lineFiltersContainFeaturePath(featurePath)) {
            return false;
        } else {
            Optional<URI> uriForFeaturePath = getURIForFeaturePath(featurePath);

            return uriForFeaturePath.filter(
                    uri -> examples.getTableBody().stream()
                            .anyMatch(
                                    row -> lineFilters.get(uri).contains(row.getLocation().getLine()))
            ).isPresent();
        }
    }

    public boolean tableRowIsNotExcludedBy(TableRow tableRow, String featurePath) {
        if (lineFilters.isEmpty()) {
            return true;
        }
        if (lineFiltersContainFeaturePath(featurePath)) {
            return false;
        } else {
            Optional<URI> uriForFeaturePath = getURIForFeaturePath(featurePath);
            return uriForFeaturePath.filter(uri -> lineFilters.get(uri).contains(tableRow.getLocation().getLine())).isPresent();
        }
    }

    private boolean lineFiltersContainFeaturePath(String featurePath) {
        return getURIForFeaturePath(featurePath) != null;
    }
}
