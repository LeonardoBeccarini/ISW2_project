package org.example.controller.processors;

import org.example.controller.retriever.VersionRetreiver;
import org.example.model.ClassifierEvaluation;
import weka.attributeSelection.*;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.ReplaceMissingValues;
import weka.filters.unsupervised.attribute.StringToNominal;
import weka.gui.beans.DataSource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WekaProcessor {
    private final int numIter;

    private static final String RANDOM_FOREST = "Random Forest";
    private static final String NAIVE_BAYES = "Naive Bayes";
    private static final String IBK = "IBk";

    //Feature selection type
    private static final String FILTER = "Filter (IG)";
    private static final String WRAPPER ="Wrapper (Best Fit)";

    //Sampling type
    private static final String NO = "None";
    private static final String UNDER ="Under";
    private static final String OVER ="Over";
    private static final String SMOTE ="Smote";



    //Used classifiers
    private NaiveBayes naiveBayesClassifier;
    private RandomForest randomForestClassifier;
    private IBk ibkClassifier;

    //List of various classifier evaluation
    private final List<ClassifierEvaluation> simpleNaiveBayesList;
    private final List<ClassifierEvaluation> simpleRandomForestList;
    private final List<ClassifierEvaluation> simpleIBkList;

    private final List<ClassifierEvaluation> filterNaiveBayesList;
    private final List<ClassifierEvaluation> filterRandomForestList;
    private final List<ClassifierEvaluation> filterIBkList;

    private final List<ClassifierEvaluation> wrapperNaiveBayesList;
    private final List<ClassifierEvaluation> wrapperRandomForestList;
    private final List<ClassifierEvaluation> wrapperIBkList;


/*    private final List<ClassifierEvaluation> undersamplingNaiveBayesList;
    private final List<ClassifierEvaluation> undersamplingRandomForestList;
    private final List<ClassifierEvaluation> undersamplingIBkList;

    private final List<ClassifierEvaluation> oversamplingNaiveBayesList;
    private final List<ClassifierEvaluation> oversamplingRandomForestList;
    private final List<ClassifierEvaluation> oversamplingIBkList;

    private final List<ClassifierEvaluation> smoteNaiveBayesList;
    private final List<ClassifierEvaluation> smoteRandomForestList;
    private final List<ClassifierEvaluation> smoteIBkList;*/

    private final String projName;

    public WekaProcessor(int numIter, String projName) {
        this.numIter = numIter;
        this.projName = projName;

        this.naiveBayesClassifier = new NaiveBayes();
        this.randomForestClassifier = new RandomForest();
        this.ibkClassifier = new IBk();

        this.simpleNaiveBayesList = new ArrayList<>();
        this.simpleRandomForestList = new ArrayList<>();
        this.simpleIBkList =new ArrayList<>();

       this.filterNaiveBayesList = new ArrayList<>();
        this.filterRandomForestList = new ArrayList<>();
        this.filterIBkList = new ArrayList<>();

        this.wrapperNaiveBayesList =new ArrayList<>();
        this.wrapperRandomForestList = new ArrayList<>();
        this.wrapperIBkList = new ArrayList<>();

    }

    public List<ClassifierEvaluation> walkForwardValidation() throws Exception {
        for (int i = 1; i < this.numIter; i++) {
            // Define file paths
            String testFilePath = "/home/leonardo/IdeaProjects/ISW2_project/retrieved_data/datasets" + "/" +this.projName + "/"
                    + this.projName + "_WF_" + i + "/" + this.projName + "_TE" + (i + 1) + ".arff";
            String trainFilePath = "/home/leonardo/IdeaProjects/ISW2_project/retrieved_data/datasets" + "/" +this.projName + "/"
                    + this.projName + "_WF_" + i + "/" + this.projName + "_TR" + i + ".arff";

            // Create ArffLoader objects
            ArffLoader sourceTest = new ArffLoader();
            ArffLoader sourceTra = new ArffLoader();

            // Check if the files exist
            File testFile = new File(testFilePath);
            File trainFile = new File(trainFilePath);

            if (!testFile.exists()) {
                throw new IOException("Test file not found: " + testFilePath);
            }
            if (!trainFile.exists()) {
                throw new IOException("Training file not found: " + trainFilePath);
            }

            // Set the file paths for ArffLoader
            sourceTest.setFile(testFile);
            sourceTra.setFile(trainFile);

            // Read dataset
            Instances training = sourceTra.getDataSet();
            Instances testing = sourceTest.getDataSet();

            int numAttributes = training.numAttributes();
            training.setClassIndex(numAttributes - 1);
            testing.setClassIndex(numAttributes - 1);

            // Perform validation without feature selection or sampling
            simpleValidation(i, training, testing, NO);
            filterValidation(i, training, testing, FILTER);
            wrapperValidationWithBestFirst(i, training, testing, WRAPPER);
        }
        List<ClassifierEvaluation> allEvaluations;
        // Merge all evaluations from each iteration into one final list
        allEvaluations = mergeAllEvaluations();
        return allEvaluations;
    }




    private List<ClassifierEvaluation> mergeAllEvaluations() {
        List<ClassifierEvaluation> allEvaluationList = new ArrayList<>();

        // Merging all classifier evaluation lists into the final list
        mergeEvaluationList(allEvaluationList, simpleNaiveBayesList, filterNaiveBayesList, wrapperNaiveBayesList);
        mergeEvaluationList(allEvaluationList, simpleRandomForestList, filterRandomForestList, wrapperRandomForestList);
        mergeEvaluationList(allEvaluationList, simpleIBkList, filterIBkList, wrapperIBkList);

        return allEvaluationList;
    }
    private Instances preprocessData(Instances data) throws Exception {
        // 1. Convert string attributes (FilePath, MethodName, Version) to nominal
        StringToNominal stringToNominalFilter = new StringToNominal();
        // Set the attribute indices (1-based indices: 1 = FilePath, 2 = MethodName, 5 = Version)
        stringToNominalFilter.setOptions(new String[] {
                "-R", "1,2,5"  // Convert FilePath, MethodName, and Version (indices 1, 2, 5) to nominal
        });

        stringToNominalFilter.setInputFormat(data);
        data = Filter.useFilter(data, stringToNominalFilter);  // Apply the filter
        return data;
    }

    private void mergeEvaluationList(List<ClassifierEvaluation> allEvaluationList,
                                     List<ClassifierEvaluation> simpleList,
                                     List<ClassifierEvaluation> filterList,
                                     List<ClassifierEvaluation> wrapperList) {
        // Add simple evaluations
        allEvaluationList.addAll(simpleList);

        // Add filter evaluations
        allEvaluationList.addAll(filterList);

        // Add wrapper evaluations
        allEvaluationList.addAll(wrapperList);
    }
    private void resetClassifiers(Instances filteredTraining) throws Exception {
        naiveBayesClassifier = new NaiveBayes();
        naiveBayesClassifier.buildClassifier(filteredTraining);
        randomForestClassifier = new RandomForest();
        randomForestClassifier.buildClassifier(filteredTraining);
        ibkClassifier = new IBk();
        ibkClassifier.buildClassifier(filteredTraining);
    }

    /** Does the simple evaluation without any feature selection/sampling/cost sensitive */
    private void simpleValidation(int i, Instances training, Instances testing, String featureSelection) throws Exception {

        //Build the classifiers
        naiveBayesClassifier.buildClassifier(training);
        randomForestClassifier.buildClassifier(training);
        ibkClassifier.buildClassifier(training);

        Evaluation evaluation = new Evaluation(testing);
        //simple Naive Bayes
        ClassifierEvaluation simpleNaiveBayes = new ClassifierEvaluation(this.projName, i, NAIVE_BAYES, featureSelection, NO);
        simpleNaiveBayesList.add(evaluateClassifier(evaluation,simpleNaiveBayes,naiveBayesClassifier,training,testing));

        evaluation = new Evaluation(testing);
        //simple RandomForest
        ClassifierEvaluation simpleRandomForest = new ClassifierEvaluation(this.projName, i, RANDOM_FOREST, featureSelection, NO);
        simpleRandomForestList.add(evaluateClassifier(evaluation,simpleRandomForest,randomForestClassifier,training,testing));

        evaluation = new Evaluation(testing);
        //simple IBK
        ClassifierEvaluation simpleIBk = new ClassifierEvaluation(this.projName, i, IBK, featureSelection, NO);
        simpleIBkList.add(evaluateClassifier(evaluation,simpleIBk,ibkClassifier,training,testing));

    }

    private void filterValidation(int i, Instances training, Instances testing, String featureSelection) throws Exception {
        // Filter method (using Information Gain as evaluator) with Ranker search method
        InfoGainAttributeEval filterEval = new InfoGainAttributeEval();
        Ranker ranker = new Ranker();  // Ranker search method for filter method
        AttributeSelection filter = new AttributeSelection();  // AttributeSelection object for filter method

        // Set evaluator and Ranker search method for filter method
        filter.setEvaluator(filterEval);
        filter.setSearch(ranker);

        // Apply the filter to the training dataset
        filter.SelectAttributes(training);  // Perform attribute selection on training data
        int[] selectedAttributes = filter.selectedAttributes();  // Get selected attribute indices

        // Apply the filter to the testing dataset by selecting the same attributes
        filter.SelectAttributes(testing);  // Perform attribute selection on testing data

        // Create new Instances with the selected attributes
        Instances filteredTraining = new Instances(training);
        Instances filteredTesting = new Instances(testing);

        // Remove unselected attributes from both the training and testing datasets
        for (int index = filteredTraining.numAttributes() - 1; index >= 0; index--) {
            boolean isSelected = false;
            // Check if the attribute is selected
            for (int selectedAttr : selectedAttributes) {
                if (selectedAttr == index) {
                    isSelected = true;
                    break;
                }
            }
            if (!isSelected) {
                filteredTraining.deleteAttributeAt(index);
                filteredTesting.deleteAttributeAt(index);
            }
        }

        // Set the class index for both filtered datasets
        int numAttrFiltered = filteredTraining.numAttributes();
        filteredTraining.setClassIndex(numAttrFiltered - 1);
        filteredTesting.setClassIndex(numAttrFiltered - 1);

        resetClassifiers(filteredTraining);

        // Evaluation for Naive Bayes
        Evaluation evaluation = new Evaluation(filteredTesting);
        ClassifierEvaluation simpleNaiveBayes = new ClassifierEvaluation(this.projName, i, NAIVE_BAYES, featureSelection, NO);
        simpleNaiveBayesList.add(evaluateClassifier(evaluation, simpleNaiveBayes, naiveBayesClassifier, filteredTraining, filteredTesting));

        // Evaluation for Random Forest
        evaluation = new Evaluation(filteredTesting);
        ClassifierEvaluation simpleRandomForest = new ClassifierEvaluation(this.projName, i, RANDOM_FOREST, featureSelection, NO);
        simpleRandomForestList.add(evaluateClassifier(evaluation, simpleRandomForest, randomForestClassifier, filteredTraining, filteredTesting));

        // Evaluation for IBK
        evaluation = new Evaluation(filteredTesting);
        ClassifierEvaluation simpleIBk = new ClassifierEvaluation(this.projName, i, IBK, featureSelection, NO);
        simpleIBkList.add(evaluateClassifier(evaluation, simpleIBk, ibkClassifier, filteredTraining, filteredTesting));
    }

    private void wrapperValidationWithBestFirst(int index, Instances training, Instances testing, String direction) throws Exception {
        // Initialize the CfsSubsetEval evaluator (correlation-based) and BestFirst search method
        CfsSubsetEval filterEval = new CfsSubsetEval();  // Correlation-based evaluator for feature selection
        BestFirst ranker = new BestFirst();  // BestFirst search method for feature selection
        AttributeSelection filter = new AttributeSelection();  // AttributeSelection object for wrapper method

        // Set evaluator and search method for wrapper-based feature selection
        filter.setEvaluator(filterEval);
        filter.setSearch(ranker);

        // Apply the filter (Wrapper feature selection) to the training dataset
        filter.SelectAttributes(training);
        int[] selectedAttributes = filter.selectedAttributes();  // Get selected attribute indices

        // Print out number of selected attributes for debugging
        System.out.println("Number of selected attributes: " + selectedAttributes.length);

        // Check if there are any selected attributes
        if (selectedAttributes.length == 0) {
            System.out.println("Warning: No attributes selected after feature selection.");
            return;  // Exit if no attributes are selected
        }

        // Apply the filter to the testing dataset (use the same selected attributes)
        filter.SelectAttributes(testing);

        // Create new Instances with the selected attributes
        Instances filteredTraining = new Instances(training);
        Instances filteredTesting = new Instances(testing);

        // Remove unselected attributes from both the training and testing datasets
        for (int i = filteredTraining.numAttributes() - 1; i >= 0; i--) {
            boolean isSelected = false;
            // Check if the attribute is selected
            for (int selectedAttr : selectedAttributes) {
                if (selectedAttr == i) {
                    isSelected = true;
                    break;
                }
            }
            if (!isSelected) {
                filteredTraining.deleteAttributeAt(i);
                filteredTesting.deleteAttributeAt(i);
            }
        }

        // Check if the class attribute is still present after feature selection
        int numAttrFiltered = filteredTraining.numAttributes();
        if (numAttrFiltered <= 1) {
            System.out.println("Warning: Only the class attribute remains in the filtered dataset.");
            return;  // Exit if there are no features left
        }

        // Set the class index for both filtered datasets
        filteredTraining.setClassIndex(numAttrFiltered - 1);
        filteredTesting.setClassIndex(numAttrFiltered - 1);

        resetClassifiers(filteredTraining);

        // Evaluation for Naive Bayes
        Evaluation evaluation = new Evaluation(filteredTesting);
        ClassifierEvaluation simpleNaiveBayes = new ClassifierEvaluation(this.projName, index, NAIVE_BAYES, direction, NO);
        simpleNaiveBayesList.add(evaluateClassifier(evaluation, simpleNaiveBayes, naiveBayesClassifier, filteredTraining, filteredTesting));

        // Evaluation for Random Forest
        evaluation = new Evaluation(filteredTesting);
        ClassifierEvaluation simpleRandomForest = new ClassifierEvaluation(this.projName, index, RANDOM_FOREST, direction, NO);
        simpleRandomForestList.add(evaluateClassifier(evaluation, simpleRandomForest, randomForestClassifier, filteredTraining, filteredTesting));

        // Evaluation for IBK
        evaluation = new Evaluation(filteredTesting);
        ClassifierEvaluation simpleIBk = new ClassifierEvaluation(this.projName, index, IBK, direction, NO);
        simpleIBkList.add(evaluateClassifier(evaluation, simpleIBk, ibkClassifier, filteredTraining, filteredTesting));
    }




    private static ClassifierEvaluation evaluateClassifier(Evaluation evaluation, ClassifierEvaluation classifierEvaluation, AbstractClassifier classifierType, Instances training, Instances testing) throws Exception {
        evaluation.evaluateModel(classifierType,testing);
        classifierEvaluation.setTrainingPercent(100.0*training.numInstances()/(training.numInstances()+testing.numInstances()));
        classifierEvaluation.setPrecision(evaluation.precision(0));
        classifierEvaluation.setRecall(evaluation.recall(0));
        classifierEvaluation.setKappa(evaluation.kappa());
        classifierEvaluation.setTp(evaluation.numTruePositives(0));
        classifierEvaluation.setFp(evaluation.numFalsePositives(0));
        classifierEvaluation.setTn(evaluation.numTrueNegatives(0));
        classifierEvaluation.setFn(evaluation.numFalseNegatives(0));
        return classifierEvaluation;
    }
}

