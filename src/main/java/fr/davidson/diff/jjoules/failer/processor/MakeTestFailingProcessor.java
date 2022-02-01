package fr.davidson.diff.jjoules.failer.processor;

import eu.stamp_project.testrunner.test_framework.TestFramework;
import fr.davidson.diff.jjoules.instrumentation.InstrumentationProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;

import java.util.Map;
import java.util.Set;

/**
 * @author Benjamin DANGLOT
 * benjamin.danglot@davidson.fr
 * on 30/06/2021
 */
public class MakeTestFailingProcessor extends InstrumentationProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MakeTestFailingProcessor.class);

    public MakeTestFailingProcessor(Map<String, Set<String>> testsToBeInstrumented, String rootPathFolder, String testFolderPath) {
        super(testsToBeInstrumented, rootPathFolder, testFolderPath);
    }

    @Override
    public void process(CtMethod<?> ctMethod) {
        final Factory factory = ctMethod.getFactory();
        TestFramework.init(factory);
        ctMethod.getBody().insertEnd(factory.createCodeSnippetStatement(
                TestFramework.isJUnit4(ctMethod) ?
                        "org.junit.Assert.fail()" :
                        "org.junit.jupiter.api.Assertions.fail()")
        );
        this.instrumentedTypes.add(ctMethod.getDeclaringType());
    }

    @Override
    public void processingDone() {
        this.instrumentedTypes.forEach(this::printCtType);
        LOGGER.info("{} instrumented test classes have been printed!", this.instrumentedTypes.size());
    }

}
