package org.example;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.model.Ticket;
import org.example.model.Version;
import org.example.retriever.CommitRetriever;
import org.example.retriever.TicketRetriever;
import org.example.retriever.VersionRetreiver;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        try {
            VersionRetreiver versionRetriever = new VersionRetreiver("BOOKKEEPER");
            TicketRetriever ticketRetriever = new TicketRetriever(versionRetriever);
            CommitRetriever commitRetriever = new CommitRetriever("BOOKKEEPER", "https://github.com/apache/bookkeeper.git", versionRetriever);
            List<Ticket> tickets = ticketRetriever.retrieveTickets("Bookkeeper");
            List<RevCommit> commits = commitRetriever.retrieveAllCommits();
            /*for (Ticket ticket : tickets) {
                System.out.println(ticket.getKey());
                Version injectedV = ticket.getInjectedVersion();
                Version openeingV = ticket.getOpeningVersion();
                Version fixedV = ticket.getFixedVersion();
                if (injectedV == null) {
                    System.out.println("missing IV! ");
                } else System.out.println(injectedV.getName());
                if (openeingV == null) {
                    System.out.println("missing OV!");
                } else System.out.println(openeingV.getName());
                if (fixedV == null) {
                    System.out.println("missing FV! ");
                } else System.out.println(fixedV.getName());

            }*/
            for(Version version: versionRetriever.getVersionList()){

                System.out.println("ID: " +version.getIndex() + " versionName:"+version.getName());
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }
}
