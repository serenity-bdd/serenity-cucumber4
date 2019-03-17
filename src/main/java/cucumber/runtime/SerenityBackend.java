package cucumber.runtime;

import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.snippets.FunctionNameGenerator;
import gherkin.pickles.PickleStep;
import io.cucumber.stepexpression.TypeRegistry;
import net.thucydides.core.steps.StepEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class SerenityBackend implements Backend {

    private static final Logger LOGGER = LoggerFactory.getLogger(SerenityBackend.class);

    private final ResourceLoader resourceLoader;
    private final TypeRegistry typeRegistry;

    public SerenityBackend(ResourceLoader resourceLoader, TypeRegistry typeRegistry) {
        this.resourceLoader = resourceLoader;
        this.typeRegistry = typeRegistry;
    }

    /**
     * Invoked once before all features. This is where stepdefs and hooks should be loaded.
     */

    @Override
    public void loadGlue(Glue glue, List<URI> gluePaths){

    }

    /**
     * Invoked before a new scenario starts. Implementations should do any necessary
     * setup of new, isolated state here.
     */
    public void buildWorld(){}



    @Override
    /**
     * Invoked at the end of a scenario, after hooks
     */
    public void disposeWorld() {
        if (!StepEventBus.getEventBus().isBaseStepListenerRegistered()) {
            LOGGER.warn("It looks like you are running a feature using @RunWith(Cucumber.class) instead of @RunWith(CucumberWithSerenity.class). Are you sure this is what you meant to do?");
        }
    }

    @Override
    public  List<String>  getSnippet(PickleStep step, String keyword, FunctionNameGenerator functionNameGenerator) {
        return new ArrayList<>();
    }

}
