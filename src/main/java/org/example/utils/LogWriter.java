package org.example.utils;

import org.eclipse.jgit.revwalk.RevCommit;
import org.example.enums.CsvEnum;
import org.example.model.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LogWriter {

    private static final String SEPARATOR = "----------------------------------------------------------------------------------" ;

    public static Path buildLogPath(String projectName) {
        return Path.of("./outputs", projectName.toUpperCase(), "Log") ;
    }

    public static void writeTicketLog(String projectName, List<Ticket> ticketInfoList) throws IOException {
        Files.createDirectories(buildLogPath(projectName));
        try (Writer writer = new BufferedWriter(new FileWriter(Path.of(buildLogPath(projectName).toString(), "Ticket").toString()))) {
            writer.write("Ticket Totali >> " + ticketInfoList.size() + "\n\n");
            for (Ticket ticketInfo : ticketInfoList) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Ticket ID >> ").append(ticketInfo.getKey()).append("\n");
                stringBuilder.append("Opening Date >> ").append(ticketInfo.getCreationDate()).append("\n");
                stringBuilder.append("Resolution Date >> ").append(ticketInfo.getResolutionDate()).append("\n");
                stringBuilder.append("Injected Version >> ").append(ticketInfo.getInjectedVersion() == null ? "NULL" : ticketInfo.getInjectedVersion().getName()).append("\n");
                stringBuilder.append("Fix Version >> ").append(ticketInfo.getFixedVersion().getName()).append("\n");
                stringBuilder.append("Opening Version >> ").append(ticketInfo.getOpeningVersion().getName()).append("\n");


                stringBuilder.append("Fix Commit List >> ").append("\n");
                for (RevCommit commit : ticketInfo.getAssociatedCommits()) {
                    stringBuilder.append("\t").append(commit.getName()).append("\n");
                }

                stringBuilder.append(SEPARATOR).append("\n\n");
                writer.write(stringBuilder.toString());
            }
        }
    }



    public static void writeMetricsToCSV(List<MethodIdentifier> methods, String outputPath) throws IOException {
        File file = new File(outputPath);

        // Ensure the parent directory exists
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
            }
        }

        // Proceed with writing the file
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Header
            writer.println("FilePath,MethodName,StartLine,EndLine,Version,StmtAdded,MaxStmtAdded,AvgStmtAdded," +
                    "StmtDeleted,MaxStmtDeleted,AvgStmtDeleted,Churn,MaxChurn,AvgChurn," +
                    "CognitiveComplexity,StatementCount,NestingDepth,ParameterCount,NumCodeSmells,NumAuthors, Duplication, MethodHistories, Buggyness");

            for (MethodIdentifier method : methods) {
                if (method.getMetricsList() == null )continue;
                Metrics m = method.getMetricsList();

                LOCMetrics stmtAdded = m.getStmtAdded();
                LOCMetrics stmtDeleted = m.getStmtDeleted();
                LOCMetrics churn = m.getChurnMetrics();
                ComplexityMetrics cm = m.getComplexityMetrics();

                writer.printf("%s,%s,%d,%d,%s,%d,%d,%.2f,%d,%d,%.2f,%d,%d,%.2f,%d,%d,%d,%d,%d,%d,%d,%d,%b%n",
                        method.getFilePath(),
                        method.getMethodName(),
                        method.getStartLine(),
                        method.getEndLine(),
                        method.getVersion() != null ? method.getVersion().getName() : "UNKNOWN",
                        stmtAdded != null ? stmtAdded.getVal() : 0,
                        stmtAdded != null ? stmtAdded.getMaxVal() : 0,
                        stmtAdded != null ? stmtAdded.getAvgVal() : 0.0,
                        stmtDeleted != null ? stmtDeleted.getVal() : 0,
                        stmtDeleted != null ? stmtDeleted.getMaxVal() : 0,
                        stmtDeleted != null ? stmtDeleted.getAvgVal() : 0.0,
                        churn != null ? churn.getVal() : 0,
                        churn != null ? churn.getMaxVal() : 0,
                        churn != null ? churn.getAvgVal() : 0.0,
                        cm != null ? cm.getCognitiveComplexity() : 0,
                        cm != null ? cm.getStatementCount() : 0,
                        cm != null ? cm.getNestingDepth() : 0,
                        cm != null ? cm.getParameterCount() : 0,
                        cm != null ? cm.getNumCodeSmells() : 0,
                        m.getAuthors(),
                        cm !=null ? cm.getDuplication():0,
                        m.getMethodHistories(),
                        m.getBugged()
                );
            }
        }
    }
    public static void writeArffForWalkForward(String projName, List<MethodIdentifier> methodIdentifierList, List<Version> versionList) throws IOException {
        // VersionInfoList contains only version with commit associated
        int end;
        if (versionList.size() % 2 == 0) end = versionList.size() / 2;
        else end = (versionList.size() + 1) / 2;

        // Since it has 2 versions, start WalkForward
        for (int i = 2; i <= end; i++) {
            List<MethodIdentifier> filteredTrainingJavaClassesList = new ArrayList<>();

            // Collect classes for the training set up until the version under testing
            for (int j = 1; j < i; j++) {
                int versionID = versionList.get(j - 1).getIndex();
                // Get classes until the version under testing
                List<MethodIdentifier> temporaryFilteredJavaClassesList = MethodUtil.filterJavaClassesByVersion(methodIdentifierList, versionID);
                filteredTrainingJavaClassesList.removeAll(temporaryFilteredJavaClassesList);
                filteredTrainingJavaClassesList.addAll(temporaryFilteredJavaClassesList);
            }

            // Generate the ARFF file for the training set
            DatasetFile labelingTraining = new DatasetFile(projName, CsvEnum.TRAINING, i - 1, i - 1, filteredTrainingJavaClassesList);
            labelingTraining.writeOnArff();

            int testingVersionID = versionList.get(i - 1).getIndex();

            // Get classes for the testing set
            List<MethodIdentifier> filteredTestingJavaClassesList = MethodUtil.filterJavaClassesByVersion(methodIdentifierList, testingVersionID);

            // Generate the ARFF file for the testing set
            DatasetFile labelingTesting = new DatasetFile(projName, CsvEnum.TESTING, i - 1, i, filteredTestingJavaClassesList);
            labelingTesting.writeOnArff();
        }
    }



}