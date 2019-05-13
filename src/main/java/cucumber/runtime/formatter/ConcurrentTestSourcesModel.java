package cucumber.runtime.formatter;

import cucumber.api.event.TestSourceRead;
import gherkin.AstBuilder;
import gherkin.GherkinDialect;
import gherkin.GherkinDialectProvider;
import gherkin.Parser;
import gherkin.ParserException;
import gherkin.TokenMatcher;
import gherkin.ast.Background;
import gherkin.ast.Examples;
import gherkin.ast.Feature;
import gherkin.ast.GherkinDocument;
import gherkin.ast.Node;
import gherkin.ast.ScenarioDefinition;
import gherkin.ast.ScenarioOutline;
import gherkin.ast.Step;
import gherkin.ast.TableRow;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Like cucumber.runtime.formatter.TestSourcesModel, but safe to use from multiple threads
class ConcurrentTestSourcesModel {
  private final Map<String, TestSourceRead> pathToReadEventMap = new ConcurrentHashMap<>();
  private final Map<String, GherkinDocument> pathToAstMap = new ConcurrentHashMap<String, GherkinDocument>();
  private final Map<String, Map<Integer, ConcurrentTestSourcesModel.AstNode>> pathToNodeMap = new ConcurrentHashMap<String, Map<Integer, ConcurrentTestSourcesModel.AstNode>>();

  static Feature getFeatureForTestCase(ConcurrentTestSourcesModel.AstNode astNode) {
    while (astNode.parent != null) {
      astNode = astNode.parent;
    }
    return (Feature) astNode.node;
  }

  static Background getBackgroundForTestCase(ConcurrentTestSourcesModel.AstNode astNode) {
    Feature feature = getFeatureForTestCase(astNode);
    ScenarioDefinition backgound = feature.getChildren().get(0);
    if (backgound instanceof Background) {
      return (Background) backgound;
    } else {
      return null;
    }
  }

  static ScenarioDefinition getScenarioDefinition(ConcurrentTestSourcesModel.AstNode astNode) {
    return astNode.node instanceof ScenarioDefinition ? (ScenarioDefinition) astNode.node : (ScenarioDefinition) astNode.parent.parent.node;
  }

  static boolean isScenarioOutlineScenario(TestSourcesModel.AstNode astNode) {
    return !(astNode.node instanceof ScenarioDefinition);
  }

  static boolean isBackgroundStep(TestSourcesModel.AstNode astNode) {
    return astNode.parent.node instanceof Background;
  }

  static String calculateId(TestSourcesModel.AstNode astNode) {
    Node node = astNode.node;
    if (node instanceof ScenarioDefinition) {
      return calculateId(astNode.parent) + ";" + convertToId(((ScenarioDefinition) node).getName());
    }
    if (node instanceof TestSourcesModel.ExamplesRowWrapperNode) {
      return calculateId(astNode.parent) + ";" + Integer.toString(((TestSourcesModel.ExamplesRowWrapperNode) node).bodyRowIndex + 2);
    }
    if (node instanceof TableRow) {
      return calculateId(astNode.parent) + ";" + Integer.toString(1);
    }
    if (node instanceof Examples) {
      return calculateId(astNode.parent) + ";" + convertToId(((Examples) node).getName());
    }
    if (node instanceof Feature) {
      return convertToId(((Feature) node).getName());
    }
    return "";
  }

  static String convertToId(String name) {
    return name.replaceAll("[\\s'_,!]", "-").toLowerCase();
  }

  void addTestSourceReadEvent(String path, TestSourceRead event) {
    pathToReadEventMap.put(path, event);
  }

  Feature getFeature(String path) {
    if (!pathToAstMap.containsKey(path)) {
      parseGherkinSource(path);
    }
    if (pathToAstMap.containsKey(path)) {
      return pathToAstMap.get(path).getFeature();
    }
    return null;
  }

  ScenarioDefinition getScenarioDefinition(String path, int line) {
    return getScenarioDefinition(getAstNode(path, line));
  }

  ConcurrentTestSourcesModel.AstNode getAstNode(String path, int line) {
    if (!pathToNodeMap.containsKey(path)) {
      parseGherkinSource(path);
    }
    if (pathToNodeMap.containsKey(path)) {
      return pathToNodeMap.get(path).get(line);
    }
    return null;
  }

  boolean hasBackground(String path, int line) {
    if (!pathToNodeMap.containsKey(path)) {
      parseGherkinSource(path);
    }
    if (pathToNodeMap.containsKey(path)) {
      ConcurrentTestSourcesModel.AstNode astNode = pathToNodeMap.get(path).get(line);
      return getBackgroundForTestCase(astNode) != null;
    }
    return false;
  }

  String getKeywordFromSource(String uri, int stepLine) {
    Feature feature = getFeature(uri);
    if (feature != null) {
      TestSourceRead event = getTestSourceReadEvent(uri);
      String trimmedSourceLine = event.source.split("\n")[stepLine - 1].trim();
      GherkinDialect dialect = new GherkinDialectProvider(feature.getLanguage()).getDefaultDialect();
      for (String keyword : dialect.getStepKeywords()) {
        if (trimmedSourceLine.startsWith(keyword)) {
          return keyword;
        }
      }
    }
    return "";
  }

  private TestSourceRead getTestSourceReadEvent(String uri) {
    if (pathToReadEventMap.containsKey(uri)) {
      return pathToReadEventMap.get(uri);
    }
    return null;
  }

  String getFeatureName(String uri) {
    Feature feature = getFeature(uri);
    if (feature != null) {
      return feature.getName();
    }
    return "";
  }

  private void parseGherkinSource(String path) {
    if (!pathToReadEventMap.containsKey(path)) {
      return;
    }
    Parser<GherkinDocument> parser = new Parser<GherkinDocument>(new AstBuilder());
    TokenMatcher matcher = new TokenMatcher();
    try {
      GherkinDocument gherkinDocument = parser.parse(pathToReadEventMap.get(path).source, matcher);
      pathToAstMap.put(path, gherkinDocument);
      Map<Integer, ConcurrentTestSourcesModel.AstNode> nodeMap = new HashMap<Integer, ConcurrentTestSourcesModel.AstNode>();
      ConcurrentTestSourcesModel.AstNode currentParent = new ConcurrentTestSourcesModel.AstNode(gherkinDocument.getFeature(), null);
      for (ScenarioDefinition child : gherkinDocument.getFeature().getChildren()) {
        processScenarioDefinition(nodeMap, child, currentParent);
      }
      pathToNodeMap.put(path, nodeMap);
    } catch (ParserException e) {
      // Ignore exceptions
    }
  }

  private void processScenarioDefinition(Map<Integer, ConcurrentTestSourcesModel.AstNode> nodeMap, ScenarioDefinition child, ConcurrentTestSourcesModel.AstNode currentParent) {
    ConcurrentTestSourcesModel.AstNode childNode = new ConcurrentTestSourcesModel.AstNode(child, currentParent);
    nodeMap.put(child.getLocation().getLine(), childNode);
    for (Step step : child.getSteps()) {
      nodeMap.put(step.getLocation().getLine(), new ConcurrentTestSourcesModel.AstNode(step, childNode));
    }
    if (child instanceof ScenarioOutline) {
      processScenarioOutlineExamples(nodeMap, (ScenarioOutline) child, childNode);
    }
  }

  private void processScenarioOutlineExamples(Map<Integer, ConcurrentTestSourcesModel.AstNode> nodeMap, ScenarioOutline scenarioOutline,
                                              ConcurrentTestSourcesModel.AstNode childNode) {
    for (Examples examples : scenarioOutline.getExamples()) {
      ConcurrentTestSourcesModel.AstNode examplesNode = new ConcurrentTestSourcesModel.AstNode(examples, childNode);
      TableRow headerRow = examples.getTableHeader();
      ConcurrentTestSourcesModel.AstNode headerNode = new ConcurrentTestSourcesModel.AstNode(headerRow, examplesNode);
      nodeMap.put(headerRow.getLocation().getLine(), headerNode);
      for (int i = 0; i < examples.getTableBody().size(); ++i) {
        TableRow examplesRow = examples.getTableBody().get(i);
        Node rowNode = new ConcurrentTestSourcesModel.ExamplesRowWrapperNode(examplesRow, i);
        ConcurrentTestSourcesModel.AstNode expandedScenarioNode = new ConcurrentTestSourcesModel.AstNode(rowNode, examplesNode);
        nodeMap.put(examplesRow.getLocation().getLine(), expandedScenarioNode);
      }
    }
  }

  static class ExamplesRowWrapperNode extends Node {
    final int bodyRowIndex;

    ExamplesRowWrapperNode(Node examplesRow, int bodyRowIndex) {
      super(examplesRow.getLocation());
      this.bodyRowIndex = bodyRowIndex;
    }
  }

  static class AstNode {
    final Node node;
    final ConcurrentTestSourcesModel.AstNode parent;

    AstNode(Node node, ConcurrentTestSourcesModel.AstNode parent) {
      this.node = node;
      this.parent = parent;
    }
  }
}
