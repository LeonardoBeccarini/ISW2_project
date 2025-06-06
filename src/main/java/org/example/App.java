package org.example;

import org.example.controller.processors.WekaProcessor;
import org.example.controller.retriever.VersionRetreiver;
import org.example.model.ClassifierEvaluation;
import org.example.model.EvaluationFile;
import org.example.utils.LogWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class App {
    public static void main(String[] args) {
        //Execution.analyzeProject("BOOKKEEPER");
        VersionRetreiver versionRetreiver = new VersionRetreiver("BOOKKEEPER");
        WekaProcessor wekaProcessor = new WekaProcessor(versionRetreiver.getVersionList().size()/2, "BOOKKEEPER");
        try {
            List<ClassifierEvaluation> allEval= wekaProcessor.walkForwardValidation();
            EvaluationFile evaluationFile = new EvaluationFile("BOOKKEEPER", allEval, "details");
            evaluationFile.reportEvaluationOnCsv();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
