package fr.davidson.diff.jjoules.class_instrumentation.process;

import fr.davidson.diff.jjoules.class_instrumentation.sorter.SorterEnum;
import fr.davidson.diff.jjoules.class_instrumentation.sorter.TestMethodsSorter;
import fr.davidson.diff.jjoules.util.Checker;
import fr.davidson.diff.jjoules.util.NodeManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.PrettyPrinter;
import sun.rmi.runtime.Log;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Benjamin DANGLOT
 * benjamin.danglot@davidson.fr
 * 01/12/2020
 */
public class JJoulesProcessor extends AbstractProcessor<CtMethod<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JJoulesProcessor.class);

    private static final String TEST_FOLDER_PATH = "src/test/java/";

    protected final Set<CtType<?>> instrumentedTypes;

    protected final Map<String, List<String>> testsToBeInstrumented;

    protected String rootPathFolder;

    private JUnitVersion jUnitVersion;

    private final Map<String, Integer> numberOfDuplicationRequired;

    private final int numberOfDuplicationForAll;

    private final int numberOfTestMethodToProcess;

    private int currentNumberOfTestMethodProcessed;

    public JJoulesProcessor(final Map<String, List<String>> testsList, String rootPathFolder) {
        this(Collections.emptyMap(), testsList, rootPathFolder);

    }

    public JJoulesProcessor(final Map<String, Integer> numberOfDuplicationRequired,
                            final Map<String, List<String>> testsList,
                            String rootPathFolder) {
        this(numberOfDuplicationRequired, testsList, rootPathFolder, -1);
    }

    public JJoulesProcessor(final Map<String, Integer> numberOfDuplicationRequired,
                            final Map<String, List<String>> testsList,
                            String rootPathFolder,
                            int numberOfTestMethodToProcess) {
        this(numberOfDuplicationRequired, testsList, rootPathFolder, numberOfTestMethodToProcess, SorterEnum.SORTER.get());
    }

    public JJoulesProcessor(final Map<String, Integer> numberOfDuplicationRequired,
                            final Map<String, List<String>> testsList,
                            String rootPathFolder,
                            int numberOfTestMethodToProcess,
                            TestMethodsSorter sorter) {
        this.instrumentedTypes = new HashSet<>();
        this.testsToBeInstrumented = sorter.sort(numberOfTestMethodToProcess, numberOfDuplicationRequired, testsList);
        this.rootPathFolder = rootPathFolder;
        this.numberOfDuplicationRequired = numberOfDuplicationRequired;
        this.numberOfDuplicationForAll = -1;
        this.currentNumberOfTestMethodProcessed = 0;
        this.numberOfTestMethodToProcess = numberOfTestMethodToProcess;
    }

    @Override
    public boolean isToBeProcessed(CtMethod<?> candidate) {
        if (this.testsToBeInstrumented.values()
                .stream()
                .noneMatch(tests -> tests.contains(candidate.getSimpleName()))) {
            return false;
        }
        CtType<?> declaringType = candidate.getDeclaringType();
        if (declaringType == null) {
            return false;
        }
        return Checker.mustInstrument(this.testsToBeInstrumented, declaringType.getQualifiedName(), candidate.getSimpleName()) ||
                Checker.checkInheritance(this.testsToBeInstrumented, candidate);
    }

    @Override
    public void processingDone() {
        LOGGER.info("Processing Done...");
    }

    private void printCtType(CtType<?> type) {
        final File directory = new File(this.rootPathFolder + "/" + TEST_FOLDER_PATH);
        type.getFactory().getEnvironment().setSourceOutputDirectory(directory);
        final PrettyPrinter prettyPrinter = type.getFactory().getEnvironment().createPrettyPrinter();
        final String fileName = this.rootPathFolder + "/" +
                TEST_FOLDER_PATH + "/" +
                type.getQualifiedName().replaceAll("\\.", "/") + ".java";
        LOGGER.info("Printing {} to {}", type.getQualifiedName(), fileName);
        try (final FileWriter write = new FileWriter(fileName)) {
            write.write(prettyPrinter.printTypes(type));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setRootPathFolder(String rootPathFolder) {
        this.rootPathFolder = rootPathFolder;
    }

    public void resetNumberOfTestMethodProcessed() {
        this.currentNumberOfTestMethodProcessed = 0;
    }

    @Override
    public void process(CtMethod<?> ctMethod) {
        if (this.numberOfTestMethodToProcess != -1 && this.currentNumberOfTestMethodProcessed > this.numberOfTestMethodToProcess) {
            return;
        }
        LOGGER.info("Processing {}#{}", ctMethod.getDeclaringType().getQualifiedName(), ctMethod.getSimpleName());
        final CtType<?> originalTestClass = ctMethod.getParent(CtType.class);
        this.jUnitVersion = getJUnitVersion(ctMethod);
        final AbstractInternalJJoulesProcessor internalProcessor = this.jUnitVersion.getInternalProcessor();
        final CtMethod<?> clone = ctMethod.clone();
        final CtType<?> testClass = originalTestClass.clone();
        originalTestClass.getPackage().addType(testClass);
        final String testName = originalTestClass.getQualifiedName() + "#" + clone.getSimpleName();
        final int numberOfDuplication = this.numberOfDuplicationRequired.isEmpty() ?
                this.numberOfDuplicationForAll : this.numberOfDuplicationRequired.get(testName);
        testClass.setSimpleName(testClass.getSimpleName() + "_" + ctMethod.getSimpleName());// + "_" + numberOfDuplication);
        NodeManager.removeOtherMethods(ctMethod, testClass, internalProcessor.getPredicateIsTest());
        NodeManager.replaceAllReferences(originalTestClass, clone, testClass);
        NodeManager.replaceAllReferences(originalTestClass, testClass, testClass);
        internalProcessor.processSetupAndTearDown(ctMethod, testClass);
        final long start = System.currentTimeMillis();
        this.printCtType(testClass);
        this.addDuplicationToAlreadyPrintedTestClass(clone, testClass, numberOfDuplication);
        final long elapsedTime = System.currentTimeMillis() - start;
        LOGGER.info("Printed {} with {} duplication in {} ms", testName, numberOfDuplication, elapsedTime);
        this.currentNumberOfTestMethodProcessed++;
        if(this.numberOfTestMethodToProcess != -1) {
            LOGGER.info("{}/{}", this.currentNumberOfTestMethodProcessed, this.numberOfTestMethodToProcess);
        }
    }
    
    private void addDuplicationToAlreadyPrintedTestClass(CtMethod<?> ctMethod, CtType<?> testClass, int numberOfDuplication) {
        final String fileName = this.rootPathFolder + "/" +
                TEST_FOLDER_PATH + "/" +
                testClass.getQualifiedName().replaceAll("\\.", "/") + ".java";
        final String content = readContentOfTestClass(fileName);
        ctMethod.setSimpleName(ctMethod.getSimpleName() + "_%s");
        final String toDuplicate = ctMethod.toString();
        try (final FileWriter fileWriter = new FileWriter(fileName)) {
            fileWriter.write(content);
            for (int i = 0; i < numberOfDuplication; i++) {
                fileWriter.write(String.format(toDuplicate, i));
            }
            fileWriter.write("}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private String readContentOfTestClass(final String fileName) {
        try (final BufferedReader reader =
                     new BufferedReader(new FileReader(fileName))) {
            final String content = reader.lines().collect(Collectors.joining("\n"));
            return content.substring(0, content.length() - 1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JUnitVersion getJUnitVersion(CtMethod<?> ctMethod) {
        for (JUnitVersion junitVersion : JUnitVersion.values()) {
            if (junitVersion.getInternalProcessor().isTestOfThisVersion(ctMethod)) {
                return junitVersion;
            }
        }
        return JUnitVersion.JUNIT4;
    }

}
