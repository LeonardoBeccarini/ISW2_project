package org.example;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.controller.processors.MetricsProcessor;
import org.example.controller.processors.Proportion;
import org.example.model.MethodIdentifier;
import org.example.model.Metrics;
import org.example.model.Ticket;
import org.example.controller.retriever.GitRetriever;
import org.example.controller.retriever.TicketRetriever;
import org.example.controller.retriever.VersionRetreiver;
import org.example.utils.LogWriter;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class App
{
    public static void main( String[] args ) {
         try {

            VersionRetreiver versionRetriever = new VersionRetreiver("BOOKKEEPER");
            TicketRetriever ticketRetriever = new TicketRetriever(versionRetriever);

            List<Ticket> tickets = ticketRetriever.retrieveTickets("BOOKKEEPER");
            Proportion proportionProcessor = new Proportion();
            List <Ticket> finalTicketList = proportionProcessor.processProportion(tickets, versionRetriever.getVersionList());
            GitRetriever gitRetriever = new GitRetriever("BOOKKEEPER", "https://github.com/apache/bookkeeper.git", versionRetriever);
            gitRetriever.associateCommitToTicket(finalTicketList);
            gitRetriever.associateCommitToVersion();

            LogWriter.writeTicketLog("Bookkeeper", finalTicketList);
            Set<MethodIdentifier> touchedMethods = gitRetriever.getTouchedMethods(versionRetriever.getVersionList());
            System.out.println("Total touched methods: " + touchedMethods.size());
             MetricsProcessor metricsProcessor = new MetricsProcessor(gitRetriever, touchedMethods);
             metricsProcessor.computeAllMetrics();
             LogWriter.writeMetricsToCSV(touchedMethods, "./output/dataset_"+ "BOOKKEEEPER" + ".csv");

        // DEBUG code
          /*   MethodIdentifier method = new ArrayList<>(touchedMethods).get(0);
             List<RevCommit> commitList = method.getCommitList();
             for (RevCommit commit : commitList) {
                 System.out.println("[DEBUG] " + "Commit: " + commit.getName());
             }
             metricsProcessor.computeLocAndChurnMetrics(method);
            Metrics methodMetric = method.getMetricsList();
            System.out.println("[DEBUG] " + "LOC: " + methodMetric.getChurnMetrics().getVal());
            System.out.println("[DEBUG] " + "Churn: " + methodMetric.getStmtAdded().getVal() + " " + methodMetric.getStmtDeleted().getVal());

*/

        } catch (JSONException | IOException |GitAPIException e) {
            e.printStackTrace();
        }
    }
}
