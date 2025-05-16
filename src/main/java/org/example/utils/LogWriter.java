package org.example.utils;

import org.eclipse.jgit.revwalk.RevCommit;
import org.example.model.Ticket;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
