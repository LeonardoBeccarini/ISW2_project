package org.example;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.example.controller.processors.Proportion;
import org.example.model.Ticket;
import org.example.controller.retriever.CommitRetriever;
import org.example.controller.retriever.TicketRetriever;
import org.example.controller.retriever.VersionRetreiver;
import org.example.model.Version;
import org.example.utils.LogWriter;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;


public class App 
{
    public static void main( String[] args ) {
         try {

            VersionRetreiver versionRetriever = new VersionRetreiver("BOOKKEEPER");
            TicketRetriever ticketRetriever = new TicketRetriever(versionRetriever);

            List<Ticket> tickets = ticketRetriever.retrieveTickets("BOOKKEEPER");
            Proportion proportionProcessor = new Proportion();
            List <Ticket> finalTicketList = proportionProcessor.processProportion(tickets, versionRetriever.getVersionList());
            CommitRetriever commitRetriever = new CommitRetriever("BOOKKEEPER", "https://github.com/apache/bookkeeper.git", versionRetriever);
            commitRetriever.associateCommitToTicket(finalTicketList);
            commitRetriever.associateCommitToVersion();

            LogWriter.writeTicketLog("Bookkeeper", finalTicketList);

        } catch (JSONException | IOException |GitAPIException e) {
            e.printStackTrace();
        }
    }
}
