package io.cucumber.junit;

import java.lang.reflect.Field;

import io.cucumber.junit.FeatureRunner;

public class FeatureRunnerExtractors {

    public static String extractFeatureName(FeatureRunner runner) {
        String displayName = runner.getDescription().getDisplayName();
        return displayName.substring(displayName.indexOf(":") + 1).trim();
    }

    public static String featurePathFor(FeatureRunner featureRunner) {
        try {
            Field field = featureRunner.getDescription().getClass().getDeclaredField("fUniqueId");
            field.setAccessible(true);
            return field.get(featureRunner.getDescription()).toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
