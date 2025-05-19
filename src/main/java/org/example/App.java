package org.example;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.controller.processors.Proportion;
import org.example.model.MethodIdentifier;
import org.example.model.Ticket;
import org.example.controller.retriever.GitRetriever;
import org.example.controller.retriever.TicketRetriever;
import org.example.controller.retriever.VersionRetreiver;
import org.example.utils.LogWriter;
import org.json.JSONException;

import java.io.IOException;
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
            List<RevCommit> commits = versionRetriever.getVersionList().get(1).getCommitList();
             for (RevCommit commit : commits) {
                 System.out.println("Commit: " + commit.getName());

                 // Get touched methods in this commit
                 Set<MethodIdentifier> touchedMethods = gitRetriever.getTouchedMethodsInCommit(commit);

                 if (touchedMethods.isEmpty()) {
                     System.out.println("  No methods touched.");
                 } else {
                     for (MethodIdentifier method : touchedMethods) {
                         System.out.println("  Touched method: " + method.getFilePath() + " :: " + method.getMethodName());
                     }
                 }

                 System.out.println();
             }


        } catch (JSONException | IOException |GitAPIException e) {
            e.printStackTrace();
        }
    }
}
