package org.example;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.example.controller.processors.MetricsProcessor;
import org.example.controller.processors.Proportion;
import org.example.controller.retriever.GitRetriever;
import org.example.controller.retriever.TicketRetriever;
import org.example.controller.retriever.VersionRetreiver;
import org.example.model.MethodIdentifier;
import org.example.model.Ticket;
import org.example.utils.LogWriter;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public class Execution {
    public static void analyzeProject(String projectname){
        try {

            VersionRetreiver versionRetriever = new VersionRetreiver(projectname);
            TicketRetriever ticketRetriever = new TicketRetriever(versionRetriever);

            List<Ticket> tickets = ticketRetriever.retrieveTickets(projectname);
            Proportion proportionProcessor = new Proportion();
            List<Ticket> finalTicketList = proportionProcessor.processProportion(tickets, versionRetriever.getVersionList());
            GitRetriever gitRetriever = new GitRetriever(projectname, "https://github.com/apache/"+projectname.toLowerCase()+".git", versionRetriever);
            gitRetriever.associateCommitToTicket(finalTicketList);
            gitRetriever.associateCommitToVersion();

            LogWriter.writeTicketLog(projectname, finalTicketList);
            List<MethodIdentifier> touchedMethods = gitRetriever.getTouchedMethods(versionRetriever.getVersionList());
            System.out.println("Total touched methods: " + touchedMethods.size());
            MetricsProcessor metricsProcessor = new MetricsProcessor(gitRetriever, touchedMethods);
            List<MethodIdentifier> touchedMethodsWithMetrics = metricsProcessor.computeAllMetrics();
            gitRetriever.labelMethods(touchedMethodsWithMetrics, finalTicketList);

            LogWriter.writeArffForWalkForward(projectname, touchedMethodsWithMetrics, versionRetriever.getVersionList());


        }catch(JSONException | IOException | GitAPIException e){
            e.printStackTrace();
        }
    }
}
