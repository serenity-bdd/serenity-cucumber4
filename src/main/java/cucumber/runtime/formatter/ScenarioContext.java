package cucumber.runtime.formatter;


import cucumber.api.TestStep;
import gherkin.ast.*;
import net.thucydides.core.model.DataTable;

import java.util.*;

class ScenarioContext {
    final Queue<Step> stepQueue = new LinkedList<>();
    final Queue<cucumber.api.TestStep> testStepQueue = new LinkedList<>();

    boolean examplesRunning;

    //keys are line numbers, entries are example rows (key=header, value=rowValue )
    Map<Integer, Map<String, String>> exampleRows;

    //keys are line numbers
    Map<Integer, List<Tag>> exampleTags;

    int exampleCount = 0;

    DataTable table;

    boolean waitingToProcessBackgroundSteps = false;

    String currentScenarioId;

    ScenarioDefinition currentScenarioDefinition;

    String currentScenario;

    List<Tag> featureTags = new ArrayList<>();

    boolean addingScenarioOutlineSteps = false;

    public Queue<Step> getStepQueue() {
        return stepQueue;
    }

    public Queue<TestStep> getTestStepQueue() {
        return testStepQueue;
    }

    public boolean isExamplesRunning() {
        return examplesRunning;
    }

    public Map<Integer, Map<String, String>> getExampleRows() {
        return exampleRows;
    }

    public Map<Integer, List<Tag>> getExampleTags() {
        return exampleTags;
    }

    public int getExampleCount() {
        return exampleCount;
    }

    public DataTable getTable() {
        return table;
    }

    public boolean isWaitingToProcessBackgroundSteps() {
        return waitingToProcessBackgroundSteps;
    }

    public String getCurrentScenarioId() {
        return currentScenarioId;
    }

    public ScenarioDefinition getCurrentScenarioDefinition() {
        return currentScenarioDefinition;
    }

    public String getCurrentScenario() {
        return currentScenario;
    }

    public List<Tag> getFeatureTags() {
        return featureTags;
    }

    public boolean isAddingScenarioOutlineSteps() {
        return addingScenarioOutlineSteps;
    }

    public void setFeatureTags(List<Tag> tags) {
        this.featureTags = new ArrayList<>(tags);
    }

    public void setCurrentScenarioDefinitionFrom(TestSourcesModel.AstNode astNode) {
        this.currentScenarioDefinition = TestSourcesModel.getScenarioDefinition(astNode);
    }

    public boolean isAScenarioOutline() {
        return currentScenarioDefinition instanceof ScenarioOutline;
    }

    public void startNewExample() {
        examplesRunning = true;
        addingScenarioOutlineSteps = true;
    }

    public List<Tag> getScenarioTags() {
        if (currentScenarioDefinition instanceof ScenarioOutline) {
            return ((ScenarioOutline) currentScenarioDefinition).getTags();
        } else if (currentScenarioDefinition instanceof Scenario) {
            return ((Scenario) currentScenarioDefinition).getTags();
        } else {
            return new ArrayList<>();
        }
    }

    public String getScenarioName() {
        return currentScenarioDefinition.getName();
    }

    public List<Examples> getScenarioExamples() {
        return ((ScenarioOutline) currentScenarioDefinition).getExamples();
    }

    public void clearStepQueue() {
        stepQueue.clear();
    }

    public void addStep(Step step) {
        stepQueue.add(step);
    }

    public void addTestStep(TestStep testStep) {
        testStepQueue.add(testStep);
    }

    public Step getCurrentStep() {
        return stepQueue.peek();
    }
}
