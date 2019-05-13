package cucumber.runtime.formatter;

import cucumber.api.HookTestStep;
import cucumber.api.PickleStepTestStep;
import cucumber.api.Plugin;
import cucumber.api.Result;
import cucumber.api.event.*;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.io.ResourceLoader;
import gherkin.ast.*;
import gherkin.pickles.Argument;
import gherkin.pickles.PickleCell;
import gherkin.pickles.PickleRow;
import gherkin.pickles.PickleTable;
import io.cucumber.tagexpressions.Expression;
import io.cucumber.tagexpressions.TagExpressionParser;
import net.serenitybdd.core.Serenity;
import net.serenitybdd.core.SerenityListeners;
import net.serenitybdd.core.SerenityReports;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import net.serenitybdd.cucumber.formatting.ScenarioOutlineDescription;
import net.thucydides.core.guice.Injectors;
import net.thucydides.core.model.DataTable;
import net.thucydides.core.model.*;
import net.thucydides.core.model.stacktrace.RootCauseAnalyzer;
import net.thucydides.core.reports.ReportService;
import net.thucydides.core.steps.*;
import net.thucydides.core.util.Inflector;
import net.thucydides.core.webdriver.Configuration;
import net.thucydides.core.webdriver.ThucydidesWebDriverSupport;
import org.junit.internal.AssumptionViolatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static cucumber.runtime.formatter.TaggedScenario.*;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Cucumber Formatter for Serenity.
 *
 * @author L.Carausu (liviu.carausu@gmail.com)
 */
public class SerenityReporter implements  Plugin,ConcurrentEventListener {

    private final ThreadLocal<Context> CONTEXT = new ThreadLocal<Context>() {
        @Override
        protected Context initialValue() {
            return new Context(runtimeOptions);
        }
    };
    
    private static final String OPEN_PARAM_CHAR = "\uff5f";
    private static final String CLOSE_PARAM_CHAR = "\uff60";

    private static final String SCENARIO_OUTLINE_NOT_KNOWN_YET = "";

    private Configuration systemConfiguration;
    private final RuntimeOptions runtimeOptions;

    private final List<BaseStepListener> baseStepListeners;

    private final ConcurrentTestSourcesModel testSources = new ConcurrentTestSourcesModel();

    private final static String FEATURES_ROOT_PATH = "features";

    private static final Logger LOGGER = LoggerFactory.getLogger(SerenityReporter.class);


    /**
     * Constructor automatically called by cucumber when class is specified as plugin
     * in @CucumberOptions.
     */
    public SerenityReporter() {
        this.systemConfiguration = Injectors.getInjector().getInstance(Configuration.class);
        baseStepListeners = Collections.synchronizedList(new ArrayList<>());
        this.runtimeOptions = CucumberWithSerenity.currentRuntimeOptions();
    }

    public SerenityReporter(Configuration systemConfiguration, ResourceLoader resourceLoader, RuntimeOptions runtimeOptions) {
        this.systemConfiguration = systemConfiguration;
        this.runtimeOptions = runtimeOptions;
        baseStepListeners = Collections.synchronizedList(new ArrayList<>());
    }

    private StepEventBus getStepEventBus(String featurePath){
        if (CONTEXT.get().getLineFilters().containsKey(featurePath)) {
            featurePath += ":" + CONTEXT.get().getLineFilters().get(featurePath).get(0);
        }
        return StepEventBus.eventBusFor(featurePath);
    }

    private void setStepEventBus(String featurePath){
        if (CONTEXT.get().getLineFilters().containsKey(featurePath)) {
            featurePath += ":" + CONTEXT.get().getLineFilters().get(featurePath).get(0);
        }
        StepEventBus.setCurrentBusToEventBusFor(featurePath);
    }

    private void initialiseThucydidesListenersFor(String featurePath) {
        if (getStepEventBus(featurePath).isBaseStepListenerRegistered()) {
            return;
        }
        SerenityListeners listeners = new SerenityListeners(getStepEventBus(featurePath), systemConfiguration);
        baseStepListeners.add(listeners.getBaseStepListener());
    }

    private EventHandler<TestSourceRead> testSourceReadHandler = event -> handleTestSourceRead(event);
    private EventHandler<TestCaseStarted> caseStartedHandler = event -> handleTestCaseStarted(event);
    private EventHandler<TestCaseFinished> caseFinishedHandler = event -> handleTestCaseFinished(event);
    private EventHandler<TestStepStarted> stepStartedHandler = event -> handleTestStepStarted(event);
    private EventHandler<TestStepFinished> stepFinishedHandler = event -> handleTestStepFinished(event);
    private EventHandler<TestRunStarted> runStartedHandler = event -> handleTestRunStarted(event);
    private EventHandler<TestRunFinished> runFinishedHandler = event -> handleTestRunFinished(event);
    private EventHandler<WriteEvent> writeEventHandler = event -> handleWrite(event);

    private void handleTestRunStarted(TestRunStarted event) {
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestSourceRead.class, testSourceReadHandler);
        publisher.registerHandlerFor(TestRunStarted.class, runStartedHandler);
        publisher.registerHandlerFor(TestRunFinished.class, runFinishedHandler);
        publisher.registerHandlerFor(TestCaseStarted.class, caseStartedHandler);
        publisher.registerHandlerFor(TestCaseFinished.class, caseFinishedHandler);
        publisher.registerHandlerFor(TestStepStarted.class, stepStartedHandler);
        publisher.registerHandlerFor(TestStepFinished.class, stepFinishedHandler);
        publisher.registerHandlerFor(WriteEvent.class, writeEventHandler);
    }

    private ThreadLocal<String> currentFeaturePath = new ThreadLocal<>();

    private void currentFeaturePathIs(String featurePath) {
        currentFeaturePath.set(featurePath);
    }

    private String currentFeaturePath() {
        return currentFeaturePath.get();
    }

    private void handleTestSourceRead(TestSourceRead event) {
        LOGGER.debug("TestSourceRead " + event.uri);
        testSources.addTestSourceReadEvent(event.uri, event);
    }

    private void resetEventBusFor(String featurePath) {
        StepEventBus.clearEventBusFor(featurePath);
    }

    private String relativeUriFrom(String fullPathUri) {
        String featuresRoot = File.separatorChar + FEATURES_ROOT_PATH + File.separatorChar;
        if (fullPathUri.contains(featuresRoot)) {
            return fullPathUri.substring(fullPathUri.lastIndexOf(featuresRoot) + FEATURES_ROOT_PATH.length() + 2);
        } else {
            return fullPathUri;
        }
    }

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

    private Story userStoryFrom(Feature feature, String featureFileUri) {

        Story userStory = Story.withIdAndPath(TestSourcesModel.convertToId(feature.getName()), feature.getName(), featureFileUri).asFeature();

        if (!isEmpty(feature.getDescription())) {
            userStory = userStory.withNarrative(feature.getDescription());
        }
        return userStory;
    }

    private void handleTestCaseStarted(TestCaseStarted event) {
        
        LOGGER.debug("TestCaseStarted " + event.getTestCase().getName());
        
        //initLineFilters(new MultiLoader(SerenityReporter.class.getClassLoader()));
        currentFeaturePathIs(event.testCase.getUri());
        setStepEventBus(event.testCase.getUri());

        String scenarioName = event.testCase.getName();
        ConcurrentTestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeaturePath(), event.testCase.getLine());
        Optional<Feature> currentFeature = featureFrom(event.testCase.getUri());

        if ((astNode != null) && currentFeature.isPresent()) {
            CONTEXT.get().currentScenarioDefinition = ConcurrentTestSourcesModel.getScenarioDefinition(astNode);

            //the sources are read in parallel, global current feature cannot be used
            String scenarioId = scenarioIdFrom(currentFeature.get().getName(), TestSourcesModel.convertToId(CONTEXT.get().currentScenarioDefinition.getName()));
            boolean newScenario = !scenarioId.equals(CONTEXT.get().currentScenario);
            if (newScenario) {
                LOGGER.debug("New scenario " + scenarioId);
                configureDriver(currentFeature.get(), currentFeaturePath());
                if (CONTEXT.get().currentScenarioDefinition instanceof ScenarioOutline) {
                    CONTEXT.get().examplesRunning = true;
                    CONTEXT.get().addingScenarioOutlineSteps = true;
                    examples(currentFeature.get(), ((ScenarioOutline) CONTEXT.get().currentScenarioDefinition).getTags(),
                      CONTEXT.get().currentScenarioDefinition.getName(), ((ScenarioOutline) CONTEXT.get().currentScenarioDefinition).getExamples());
                }
                
                initScenarioTags(event.getTestCase().getUri());
                startOfScenarioLifeCycle(currentFeature.get(), scenarioName, CONTEXT.get().currentScenarioDefinition, event.testCase.getLine());
                CONTEXT.get().currentScenario = scenarioIdFrom(currentFeature.get().getName(),
                  TestSourcesModel.convertToId(CONTEXT.get().currentScenarioDefinition.getName()));
            } else {
                if (CONTEXT.get().currentScenarioDefinition instanceof ScenarioOutline) {
                    startExample(event.testCase.getLine());
                }
            }
            Background background = ConcurrentTestSourcesModel.getBackgroundForTestCase(astNode);
            if (background != null) {
                handleBackground(background);
            }
        }
    }
    
    private void handleTestCaseFinished(TestCaseFinished event) {
        if (CONTEXT.get().examplesRunning) {
            handleResult(event.result);
        }

        if (event.result.is(Result.Type.FAILED) && noAnnotatedResultIdDefinedFor(event)) {
            getStepEventBus(event.testCase.getUri()).testFailed(event.result.getError());
        } else {
            getStepEventBus(event.testCase.getUri()).testFinished();
        }

        CONTEXT.get().stepQueue.clear();
        if (CONTEXT.get().examplesRunning) {
            finishExample();
        }
    }

    private boolean noAnnotatedResultIdDefinedFor(TestCaseFinished event) {
        BaseStepListener baseStepListener = getStepEventBus(event.testCase.getUri()).getBaseStepListener();
        return (baseStepListener.getTestOutcomes().isEmpty() || (latestOf(baseStepListener.getTestOutcomes()).getAnnotatedResult() == null));
    }

    private TestOutcome latestOf(List<TestOutcome> testOutcomes) {
        return testOutcomes.get(testOutcomes.size() - 1);
    }

    private List<String> createCellList(PickleRow row) {
        List<String> cells = new ArrayList<>();
        for (PickleCell cell : row.getCells()) {
            cells.add(cell.getValue());
        }
        return cells;
    }

    private void handleTestStepStarted(TestStepStarted event) {
        LOGGER.debug("TestStepStarted " + event.getTestCase().getName());
        if (!(event.testStep instanceof HookTestStep)) {
            if(event.testStep instanceof PickleStepTestStep) {
                PickleStepTestStep pickleTestStep = (PickleStepTestStep)event.testStep;
                ConcurrentTestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeaturePath(), pickleTestStep.getStepLine());
                if (astNode != null) {
                    Step step = (Step) astNode.node;
                    if (!CONTEXT.get().addingScenarioOutlineSteps) {
                        CONTEXT.get().stepQueue.add(step);
                        CONTEXT.get().testStepQueue.add(event.testStep);
                    }
                    Step currentStep = CONTEXT.get().stepQueue.peek();
                    String stepTitle = stepTitleFrom(currentStep, pickleTestStep);
                    getStepEventBus(currentFeaturePath()).stepStarted(ExecutedStepDescription.withTitle(stepTitle));
                    getStepEventBus(currentFeaturePath()).updateCurrentStepTitle(normalized(stepTitle));
                }
            }
        }
    }

    private void handleWrite(WriteEvent event) {
        getStepEventBus(currentFeaturePath()).stepStarted(ExecutedStepDescription.withTitle(event.text));
        getStepEventBus(currentFeaturePath()).stepFinished();
    }

    private void handleTestStepFinished(TestStepFinished event) {
        LOGGER.debug("TestStepFinished " + event.getTestCase().getName());
        if (!(event.testStep instanceof HookTestStep)) {
            handleResult(event.result);
        }
    }

    private void handleTestRunFinished(TestRunFinished event) {
        generateReports();
        assureTestSuiteFinished();
    }

    private ReportService getReportService() {
        return SerenityReports.getReportService(systemConfiguration);
    }

    private Feature featureWithDefaultName(Feature feature, String defaultName) {
        return new Feature(feature.getTags(),
                feature.getLocation(),
                feature.getLanguage(),
                feature.getKeyword(),
                defaultName,
                feature.getDescription(),
                feature.getChildren());
    }

    private void configureDriver(Feature feature, String featurePath) {
        getStepEventBus(featurePath).setUniqueSession(systemConfiguration.shouldUseAUniqueBrowser());
        List<String> tags = getTagNamesFrom(feature.getTags());
        String requestedDriver = getDriverFrom(tags);
        String requestedDriverOptions = getDriverOptionsFrom(tags);
        if (isNotEmpty(requestedDriver)) {
            ThucydidesWebDriverSupport.useDefaultDriver(requestedDriver);
            ThucydidesWebDriverSupport.useDriverOptions(requestedDriverOptions);
        }
    }

    private List<String> getTagNamesFrom(List<Tag> tags) {
        List<String> tagNames = new ArrayList<>();
        for (Tag tag : tags) {
            tagNames.add(tag.getName());
        }
        return tagNames;
    }

    private String getDriverFrom(List<String> tags) {
        String requestedDriver = null;
        for (String tag : tags) {
            if (tag.startsWith("@driver:")) {
                requestedDriver = tag.substring(8);
            }
        }
        return requestedDriver;
    }

    private String getDriverOptionsFrom(List<String> tags) {
        String requestedDriver = null;
        for (String tag : tags) {
            if (tag.startsWith("@driver-options:")) {
                requestedDriver = tag.substring(16);
            }
        }
        return requestedDriver;
    }

    private void examples(Feature currentFeature, List<Tag> scenarioOutlineTags, String id, List<Examples> examplesList) {
        String featureName = currentFeature.getName();
        List<Tag> currentFeatureTags = currentFeature.getTags();
        CONTEXT.get().addingScenarioOutlineSteps = false;
        initializeExamples();
        for (Examples examples : examplesList) {
            if (examplesAreNotExcludedByTags(examples, scenarioOutlineTags, currentFeatureTags) && examplesAreNotExcludedByLinesFilter(examples)) {
                List<TableRow> examplesTableRows = examples.getTableBody().stream().filter(
                        tableRow -> tableRowIsNotExcludedByLinesFilter(tableRow)).collect(Collectors.toList());
                List<String> headers = getHeadersFrom(examples.getTableHeader());
                List<Map<String, String>> rows = getValuesFrom(examplesTableRows, headers);
                for (int i = 0; i < examplesTableRows.size(); i++) {
                    addRow(exampleRows(), headers, examplesTableRows.get(i));
                    if (examples.getTags() != null) {
                        exampleTags().put(examplesTableRows.get(i).getLocation().getLine(), examples.getTags());
                    }
                }
                String scenarioId = scenarioIdFrom(featureName, id);
                boolean newScenario = !scenarioId.equals(CONTEXT.get().currentScenarioId);
                CONTEXT.get().table = (newScenario) ?
                        thucydidesTableFrom(SCENARIO_OUTLINE_NOT_KNOWN_YET, headers, rows, trim(examples.getName()), trim(examples.getDescription()))
                        : addTableRowsTo(CONTEXT.get().table, headers, rows, trim(examples.getName()), trim(examples.getDescription()));

                CONTEXT.get().table.addTagsToLatestDataSet(examples.getTags().stream().map(tag -> TestTag.withValue(tag.getName().substring(1))).collect(Collectors.toList()));
                CONTEXT.get().exampleCount = CONTEXT.get().table.getSize();
                CONTEXT.get().currentScenarioId = scenarioId;
            }
        }
    }

    private boolean examplesAreNotExcludedByLinesFilter(Examples examples) {
        if (CONTEXT.get().getLineFilters().isEmpty()) {
            return true;
        }

        if (!CONTEXT.get().getLineFilters().containsKey(currentFeaturePath())) {
            return false;
        } else {
            return examples.getTableBody().stream().anyMatch(
                    row -> CONTEXT.get().getLineFilters().get(currentFeaturePath()).contains((long) row.getLocation().getLine()));
        }
    }

    private boolean tableRowIsNotExcludedByLinesFilter(TableRow tableRow) {
        if (CONTEXT.get().getLineFilters().isEmpty()) {
            return true;
        }

        if (!CONTEXT.get().getLineFilters().containsKey(currentFeaturePath())) {
            return false;
        } else {
            return CONTEXT.get().getLineFilters().get(currentFeaturePath()).contains((long) tableRow.getLocation().getLine());
        }
    }

    private boolean examplesAreNotExcludedByTags(Examples examples, List<Tag> scenarioOutlineTags, List<Tag> currentFeatureTags) {
        if (testRunHasFilterTags()) {
            return examplesMatchFilter(examples, scenarioOutlineTags, currentFeatureTags);
        }
        return true;
    }

    private boolean examplesMatchFilter(Examples examples, List<Tag> scenarioOutlineTags, List<Tag> currentFeatureTags) {
        List<Tag> allExampleTags = getExampleAllTags(examples, scenarioOutlineTags, currentFeatureTags);
        List<String> allTagsForAnExampleScenario = allExampleTags.stream().map(Tag::getName).collect(Collectors.toList());
        String TagValuesFromCucumberOptions = getCucumberRuntimeTags().get(0);
        TagExpressionParser parser = new TagExpressionParser();
        Expression expressionNode = parser.parse(TagValuesFromCucumberOptions);
        return expressionNode.evaluate(allTagsForAnExampleScenario);
    }

    private boolean testRunHasFilterTags() {
        List<String> tagFilters = getCucumberRuntimeTags();
        return (tagFilters != null) && tagFilters.size() > 0;
    }

    private List<String> getCucumberRuntimeTags() {
        if (CucumberWithSerenity.currentRuntimeOptions() == null) {
            return new ArrayList<>();
        } else {
            return CucumberWithSerenity.currentRuntimeOptions().getTagFilters();
        }
    }

    private List<Tag> getExampleAllTags(Examples examples, List<Tag> scenarioOutlineTags, List<Tag> currentFeatureTags) {
        List<Tag> exampleTags = examples.getTags();
        List<Tag> allTags = new ArrayList<>();
        if (exampleTags != null)
            allTags.addAll(exampleTags);
        if (scenarioOutlineTags != null)
            allTags.addAll(scenarioOutlineTags);
        if (currentFeatureTags != null)
            allTags.addAll(currentFeatureTags);
        return allTags;
    }

    private List<String> getHeadersFrom(TableRow headerRow) {
        return headerRow.getCells().stream().map(TableCell::getValue).collect(Collectors.toList());
    }

    private List<Map<String, String>> getValuesFrom(List<TableRow> examplesTableRows, List<String> headers) {

        List<Map<String, String>> rows = new ArrayList<>();
        for (int row = 0; row < examplesTableRows.size(); row++) {
            Map<String, String> rowValues = new HashMap<>();
            int column = 0;
            List<String> cells = examplesTableRows.get(row).getCells().stream().map(TableCell::getValue).collect(Collectors.toList());
            for (String cellValue : cells) {
                String columnName = headers.get(column++);
                rowValues.put(columnName, cellValue);
            }
            rows.add(rowValues);
        }
        return rows;
    }

    private void addRow(Map<Integer, Map<String, String>> exampleRows,
                        List<String> headers,
                        TableRow currentTableRow) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int j = 0; j < headers.size(); j++) {
            List<String> cells = currentTableRow.getCells().stream().map(TableCell::getValue).collect(Collectors.toList());
            row.put(headers.get(j), cells.get(j));
        }
        exampleRows().put(currentTableRow.getLocation().getLine(), row);
    }

    private String scenarioIdFrom(String featureId, String scenarioIdOrExampleId) {
        return (featureId != null && scenarioIdOrExampleId != null) ? String.format("%s;%s", featureId, scenarioIdOrExampleId) : "";
    }

    private void initializeExamples() {
        CONTEXT.get().examplesRunning = true;
    }

    private Map<Integer, Map<String, String>> exampleRows() {
        if (CONTEXT.get().exampleRows == null) {
            CONTEXT.get().exampleRows = Collections.synchronizedMap(new HashMap<>());
        }
        return CONTEXT.get().exampleRows;
    }

    private Map<Integer, List<Tag>> exampleTags() {
        if (CONTEXT.get().exampleTags == null) {
            CONTEXT.get().exampleTags = Collections.synchronizedMap(new HashMap<>());
        }
        return CONTEXT.get().exampleTags;
    }

    private DataTable thucydidesTableFrom(String scenarioOutline,
                                          List<String> headers,
                                          List<Map<String, String>> rows,
                                          String name,
                                          String description) {
        return DataTable.withHeaders(headers).andScenarioOutline(scenarioOutline).andMappedRows(rows).andTitle(name).andDescription(description).build();
    }

    private DataTable addTableRowsTo(DataTable table, List<String> headers,
                                     List<Map<String, String>> rows,
                                     String name,
                                     String description) {
        table.startNewDataSet(name, description);
        for (Map<String, String> row : rows) {
            table.appendRow(rowValuesFrom(headers, row));
        }
        return table;
    }

    private List<String> rowValuesFrom(List<String> headers, Map<String, String> row) {
        return headers.stream()
                .map(header -> row.get(header))
                .collect(toList());
    }

    private void startOfScenarioLifeCycle(Feature feature, String scenarioName, ScenarioDefinition scenario, Integer currentLine) {

        boolean newScenario = !scenarioIdFrom(TestSourcesModel.convertToId(feature.getName()),
          TestSourcesModel.convertToId(scenario.getName())).equals(CONTEXT.get().currentScenario);
        CONTEXT.get().currentScenario = scenarioIdFrom(TestSourcesModel.convertToId(feature.getName()), TestSourcesModel.convertToId(scenario.getName()));
        if (CONTEXT.get().examplesRunning) {
            if (newScenario) {
                startScenario(feature, scenario, scenarioName);
                getStepEventBus(currentFeaturePath()).useExamplesFrom(CONTEXT.get().table);
                getStepEventBus(currentFeaturePath()).useScenarioOutline(ScenarioOutlineDescription.from(scenario).getDescription());
            } else {
                getStepEventBus(currentFeaturePath()).addNewExamplesFrom(CONTEXT.get().table);
            }
            startExample(currentLine);
        } else {
            startScenario(feature, scenario, scenarioName);
        }
    }

    private void startScenario(Feature currentFeature, ScenarioDefinition scenarioDefinition, String scenarioName) {
        getStepEventBus(currentFeaturePath()).setTestSource(TestSourceType.TEST_SOURCE_CUCUMBER.getValue());
        getStepEventBus(currentFeaturePath()).testStarted(scenarioName,
                scenarioIdFrom(TestSourcesModel.convertToId(currentFeature.getName()), TestSourcesModel.convertToId(scenarioName)));
        getStepEventBus(currentFeaturePath()).addDescriptionToCurrentTest(scenarioDefinition.getDescription());
        getStepEventBus(currentFeaturePath()).addTagsToCurrentTest(convertCucumberTags(currentFeature.getTags()));

        if (isScenario(scenarioDefinition)) {
            getStepEventBus(currentFeaturePath()).addTagsToCurrentTest(convertCucumberTags(((Scenario) scenarioDefinition).getTags()));
        } else if (isScenarioOutline(scenarioDefinition)) {
            getStepEventBus(currentFeaturePath()).addTagsToCurrentTest(convertCucumberTags(((ScenarioOutline) scenarioDefinition).getTags()));
        }

        registerFeatureJiraIssues(currentFeature.getTags());
        List<Tag> tags = getTagsOfScenarioDefinition(scenarioDefinition);
        registerScenarioJiraIssues(tags);

        CONTEXT.get().scenarioTags = tagsForScenario(scenarioDefinition);
        updateResultFromTags(CONTEXT.get().scenarioTags);
    }

    private List<Tag> tagsForScenario(ScenarioDefinition scenarioDefinition) {
        List<Tag> scenarioTags = new ArrayList<>(CONTEXT.get().featureTags);
        scenarioTags.addAll(getTagsOfScenarioDefinition(scenarioDefinition));
        return scenarioTags;
    }

    private void initScenarioTags(String uri) {

        String featurePath = uri;

        Optional<Feature> possibleFeature = featureFrom(featurePath);

        possibleFeature.ifPresent(
          feature -> {
              CONTEXT.get().featureTags = new ArrayList<>(feature.getTags());

              resetEventBusFor(featurePath);
              initialiseThucydidesListenersFor(featurePath);
              configureDriver(feature, featurePath);

              Story userStory = userStoryFrom(feature, relativeUriFrom(uri));

              getStepEventBus(uri).testSuiteStarted(userStory);

          }
        );
    }

    private boolean isScenario(ScenarioDefinition scenarioDefinition) {
        return scenarioDefinition instanceof Scenario;
    }

    private boolean isScenarioOutline(ScenarioDefinition scenarioDefinition) {
        return scenarioDefinition instanceof ScenarioOutline;
    }

    private List<Tag> getTagsOfScenarioDefinition(ScenarioDefinition scenarioDefinition) {
        List<Tag> tags = new ArrayList<>();
        if (isScenario(scenarioDefinition)) {
            tags = ((Scenario) scenarioDefinition).getTags();
        } else if (isScenarioOutline(scenarioDefinition)) {
            tags = ((ScenarioOutline) scenarioDefinition).getTags();
        }
        return tags;
    }

    private void registerFeatureJiraIssues(List<Tag> tags) {
        List<String> issues = extractJiraIssueTags(tags);
        if (!issues.isEmpty()) {
            getStepEventBus(currentFeaturePath()).addIssuesToCurrentStory(issues);
        }
    }

    private void registerScenarioJiraIssues(List<Tag> tags) {
        List<String> issues = extractJiraIssueTags(tags);
        if (!issues.isEmpty()) {
            getStepEventBus(currentFeaturePath()).addIssuesToCurrentTest(issues);
        }
    }

    private List<TestTag> convertCucumberTags(List<Tag> cucumberTags) {
        List<TestTag> tags = new ArrayList<>();
        for (Tag tag : cucumberTags) {
            tags.add(TestTag.withValue(tag.getName().substring(1)));
        }
        return new ArrayList(tags);
    }

    private List<String> extractJiraIssueTags(List<Tag> cucumberTags) {
        List<String> issues = new ArrayList<>();
        for (Tag tag : cucumberTags) {
            if (tag.getName().startsWith("@issue:")) {
                String tagIssueValue = tag.getName().substring("@issue:".length());
                issues.add(tagIssueValue);
            }
            if (tag.getName().startsWith("@issues:")) {
                String tagIssuesValues = tag.getName().substring("@issues:".length());
                issues.addAll(Arrays.asList(tagIssuesValues.split(",")));
            }
        }
        return issues;
    }
    
    private void startExample(Integer lineNumber) {
        Map<String, String> data = exampleRows().get(lineNumber);
        getStepEventBus(currentFeaturePath()).clearStepFailures();
        getStepEventBus(currentFeaturePath()).exampleStarted(data);
        if (exampleTags().containsKey(lineNumber)) {
            List<Tag> currentExampleTags = exampleTags().get(lineNumber);
            getStepEventBus(currentFeaturePath()).addTagsToCurrentTest(convertCucumberTags(currentExampleTags));
        }
    }

    private void finishExample() {
        getStepEventBus(currentFeaturePath()).exampleFinished();
        CONTEXT.get().exampleCount--;
        if (CONTEXT.get().exampleCount == 0) {
            CONTEXT.get().examplesRunning = false;
            setTableScenarioOutline();
        } else {
            CONTEXT.get().examplesRunning = true;
        }
    }

    private void setTableScenarioOutline() {
        List<Step> steps = CONTEXT.get().currentScenarioDefinition.getSteps();
        StringBuffer scenarioOutlineBuffer = new StringBuffer();
        for (Step step : steps) {
            scenarioOutlineBuffer.append(step.getKeyword()).append(step.getText()).append("\n\r");
        }
        String scenarioOutline = scenarioOutlineBuffer.toString();
        if (CONTEXT.get().table != null) {
            CONTEXT.get().table.setScenarioOutline(scenarioOutline);
        }
    }


    private void handleBackground(Background background) {
        CONTEXT.get().waitingToProcessBackgroundSteps = true;
        String backgroundName = background.getName();
        if (backgroundName != null) {
            getStepEventBus(currentFeaturePath()).setBackgroundTitle(backgroundName);
        }
        String backgroundDescription = background.getDescription();
        if (backgroundDescription == null) {
            backgroundDescription = "";
        }
        getStepEventBus(currentFeaturePath()).setBackgroundDescription(backgroundDescription);
    }

    private void assureTestSuiteFinished() {
        CONTEXT.get().stepQueue.clear();
        CONTEXT.get().testStepQueue.clear();

        Optional.ofNullable(currentFeaturePath()).ifPresent(
                featurePath -> {
                    getStepEventBus(featurePath).testSuiteFinished();
                    getStepEventBus(featurePath).dropAllListeners();
                    getStepEventBus(featurePath).clear();
                    StepEventBus.clearEventBusFor(featurePath);
                }
        );
        Serenity.done();
        CONTEXT.get().table = null;
        CONTEXT.get().currentScenarioId = null;

    }

    private void handleResult(Result result) {
        Step currentStep = CONTEXT.get().stepQueue.poll();
        cucumber.api.TestStep currentTestStep = CONTEXT.get().testStepQueue.poll();
        recordStepResult(result, currentStep, currentTestStep);
        if (CONTEXT.get().stepQueue.isEmpty()) {
            recordFinalResult();
        }
    }

    private void recordStepResult(Result result, Step currentStep, cucumber.api.TestStep currentTestStep) {

        if (StepEventBus.getEventBus().currentTestIsSuspended()) {
            getStepEventBus(currentFeaturePath()).stepIgnored();
        } else if (Result.Type.PASSED.equals(result.getStatus())) {
            getStepEventBus(currentFeaturePath()).stepFinished();
        } else if (Result.Type.FAILED.equals(result.getStatus())) {
            failed(stepTitleFrom(currentStep, currentTestStep), result.getError());
        } else if (Result.Type.SKIPPED.equals(result.getStatus())) {
            getStepEventBus(currentFeaturePath()).stepIgnored();
        } else if (Result.Type.PENDING.equals(result.getStatus())) {
            getStepEventBus(currentFeaturePath()).stepPending();
        } else if (Result.Type.SKIPPED.equals(result.getStatus())) {
            getStepEventBus(currentFeaturePath()).stepIgnored();
        } else if (Result.Type.UNDEFINED.equals(result.getStatus())) {
            getStepEventBus(currentFeaturePath()).stepPending();
        }
    }

    private void recordFinalResult() {
        if (CONTEXT.get().waitingToProcessBackgroundSteps) {
            CONTEXT.get().waitingToProcessBackgroundSteps = false;
        } else {
            updateResultFromTags(CONTEXT.get().scenarioTags);
        }
    }

    private void updateResultFromTags(List<Tag> scenarioTags) {
        if (isManual(scenarioTags)) {
            updateManualResultsFrom(scenarioTags);
        } else if (isPending(scenarioTags)) {
            getStepEventBus(currentFeaturePath()).testPending();
        } else if (isSkippedOrWIP(scenarioTags)) {
            getStepEventBus(currentFeaturePath()).testSkipped();
            getStepEventBus(currentFeaturePath()).getBaseStepListener().overrideResultTo(TestResult.SKIPPED);
        } else if (isIgnored(scenarioTags)) {
            getStepEventBus(currentFeaturePath()).testIgnored();
            getStepEventBus(currentFeaturePath()).getBaseStepListener().overrideResultTo(TestResult.IGNORED);
        }
    }

    private void updateManualResultsFrom(List<Tag> scenarioTags) {
        getStepEventBus(currentFeaturePath()).testIsManual();
        manualResultDefinedIn(scenarioTags).ifPresent(
                result -> {
                    if (result == TestResult.FAILURE) {

                        String failureMessage = failureMessageFrom(CONTEXT.get().currentScenarioDefinition.getDescription())
                          .orElse("Failed manual test");

                        getStepEventBus(currentFeaturePath()).getBaseStepListener()
                                .latestTestOutcome().ifPresent( outcome -> outcome.setTestFailureMessage(failureMessage));
                    }
                    getStepEventBus(currentFeaturePath()).getBaseStepListener().overrideResultTo(result);
                }
        );
    }

    private Optional<String> failureMessageFrom(String description) {
        if (description == null || description.isEmpty()) {
            return Optional.empty();
        }
        String firstLine = description.split("\r?\n")[0];
        if (firstLine.trim().toLowerCase().startsWith("failure:")) {
            return Optional.of("Failed manual test: " + firstLine.trim().substring(8).trim());
        } else {
            return Optional.empty();
        }
    }

    private void failed(String stepTitle, Throwable cause) {
        if (!errorOrFailureRecordedForStep(stepTitle, cause)) {
            if (!isEmpty(stepTitle)) {
                getStepEventBus(currentFeaturePath()).updateCurrentStepTitle(stepTitle);
            }
            Throwable rootCause = new RootCauseAnalyzer(cause).getRootCause().toException();
            if (isAssumptionFailure(rootCause)) {
                getStepEventBus(currentFeaturePath()).assumptionViolated(rootCause.getMessage());
            } else {
                getStepEventBus(currentFeaturePath()).stepFailed(new StepFailure(ExecutedStepDescription.withTitle(normalized(currentStepTitle())), rootCause));
            }
        }
    }

    private String currentStepTitle() {
        return getStepEventBus(currentFeaturePath()).getCurrentStep().isPresent() ? getStepEventBus(currentFeaturePath()).getCurrentStep().get().getDescription() : "";
    }

    private boolean errorOrFailureRecordedForStep(String stepTitle, Throwable cause) {
        if (!latestTestOutcome().isPresent()) {
            return false;
        }
        if (!latestTestOutcome().get().testStepWithDescription(stepTitle).isPresent()) {
            return false;
        }
        Optional<TestStep> matchingTestStep = latestTestOutcome().get().testStepWithDescription(stepTitle);
        if (matchingTestStep.isPresent() && matchingTestStep.get().getException() != null) {
            return (matchingTestStep.get().getException().getOriginalCause() == cause);
        }

        return false;
    }

    private Optional<TestOutcome> latestTestOutcome() {

        if (!getStepEventBus(currentFeaturePath()).isBaseStepListenerRegistered()) {
            return Optional.empty();
        }

        List<TestOutcome> recordedOutcomes = getStepEventBus(currentFeaturePath()).getBaseStepListener().getTestOutcomes();
        return (recordedOutcomes.isEmpty()) ? Optional.empty()
                : Optional.of(recordedOutcomes.get(recordedOutcomes.size() - 1));
    }

    private boolean isAssumptionFailure(Throwable rootCause) {
        return (AssumptionViolatedException.class.isAssignableFrom(rootCause.getClass()));
    }

    private String stepTitleFrom(Step currentStep, cucumber.api.TestStep testStep) {
        if (currentStep != null && testStep instanceof PickleStepTestStep)
            return currentStep.getKeyword()
                    + ((PickleStepTestStep)testStep).getPickleStep().getText()
                    + embeddedTableDataIn((PickleStepTestStep) testStep);
        return "";
    }

    private String embeddedTableDataIn(PickleStepTestStep currentStep) {
        if (!currentStep.getStepArgument().isEmpty()) {
            Argument argument = currentStep.getStepArgument().get(0);
            if (argument instanceof PickleTable) {
                List<Map<String, Object>> rowList = new ArrayList<Map<String, Object>>();
                for (PickleRow row : ((PickleTable) argument).getRows()) {
                    Map<String, Object> rowMap = new HashMap<String, Object>();
                    rowMap.put("cells", createCellList(row));
                    rowList.add(rowMap);
                }
                return convertToTextTable(rowList);
            }
        }
        return "";
    }

    private String convertToTextTable(List<Map<String, Object>> rows) {
        StringBuilder textTable = new StringBuilder();
        textTable.append(System.lineSeparator());
        for (Map<String, Object> row : rows) {
            textTable.append("|");
            for (String cell : (List<String>) row.get("cells")) {
                textTable.append(" ");
                textTable.append(cell);
                textTable.append(" |");
            }
            if (row != rows.get(rows.size() - 1)) {
                textTable.append(System.lineSeparator());
            }
        }
        return textTable.toString();
    }

    private void generateReports() {
        getReportService().generateReportsFor(getAllTestOutcomes());
    }

    public List<TestOutcome> getAllTestOutcomes() {
        return baseStepListeners.stream().map(BaseStepListener::getTestOutcomes).flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private String normalized(String value) {
        return value.replaceAll(OPEN_PARAM_CHAR, "{").replaceAll(CLOSE_PARAM_CHAR, "}");
    }

    private String trim(String stringToBeTrimmed) {
        return (stringToBeTrimmed == null) ? null : stringToBeTrimmed.trim();
    }
    
    
    private static class Context {
        private final Queue<Step> stepQueue;
        private final Queue<cucumber.api.TestStep> testStepQueue;

        private boolean examplesRunning;

        //keys are line numbers, entries are example rows (key=header, value=rowValue )
        private Map<Integer, Map<String, String>> exampleRows;

        //keys are line numbers
        private Map<Integer, List<Tag>> exampleTags;

        private int exampleCount = 0;

        private DataTable table;

        private boolean waitingToProcessBackgroundSteps = false;

        private String currentScenarioId;

        private ScenarioDefinition currentScenarioDefinition;

        private String currentScenario;

        private List<Tag> featureTags;

        private boolean addingScenarioOutlineSteps = false;

        private Map<String, List<Long>> lineFilters;

        private List<Tag> scenarioTags;
        
        Context(RuntimeOptions runtimeOptions) {
            this.stepQueue = new LinkedList<>();
            this.testStepQueue = new LinkedList<>();
            initLineFilters(runtimeOptions);
        }
        
        public Map<String, List<Long>> getLineFilters() {
            return lineFilters;
        }

        private void initLineFilters(RuntimeOptions runtimeOptions) {
            Map<String, List<Long>> lineFiltersFromRuntime = runtimeOptions.getLineFilters();
            if (lineFiltersFromRuntime == null) {
                lineFilters = new HashMap<>();
            } else {
                lineFilters = lineFiltersFromRuntime;
            }
        }
    }
}
