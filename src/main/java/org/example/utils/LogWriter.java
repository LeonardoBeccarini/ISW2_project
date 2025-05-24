package org.example.utils;

import org.eclipse.jgit.revwalk.RevCommit;
import org.example.model.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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



    public static void writeMetricsToCSV(Set<MethodIdentifier> methods, String outputPath) throws IOException {
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
            writer.println("FilePath,MethodName,StartLine,EndLine,StmtAdded,MaxStmtAdded,AvgStmtAdded," +
                    "StmtDeleted,MaxStmtDeleted,AvgStmtDeleted,Churn,MaxChurn,AvgChurn," +
                    "CognitiveComplexity,StatementCount,NestingDepth,ParameterCount,NumAuthors");

            for (MethodIdentifier method : methods) {
                if (method.getMetricsList() == null )continue;
                Metrics m = method.getMetricsList();

                LOCMetrics stmtAdded = m.getStmtAdded();
                LOCMetrics stmtDeleted = m.getStmtDeleted();
                LOCMetrics churn = m.getChurnMetrics();
                ComplexityMetrics cm = m.getComplexityMetrics();

                writer.printf("%s,%s,%d,%d,%d,%d,%.2f,%d,%d,%.2f,%d,%d,%.2f,%d,%d,%d,%d,%d%n",
                        method.getFilePath(),
                        method.getMethodName(),
                        method.getStartLine(),
                        method.getEndLine(),
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
                        m.getAuthors()
                );
            }
        }
    }


}