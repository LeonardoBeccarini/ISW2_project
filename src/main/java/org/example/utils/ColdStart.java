package org.example.utils;


import org.eclipse.jgit.api.errors.GitAPIException;
import org.example.controller.retriever.TicketRetriever;
import org.example.controller.retriever.VersionRetreiver;
import org.example.enums.ProjectsEnum;
import org.example.model.Ticket;
import org.json.JSONException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public class ColdStart {

    private ColdStart() {}

    public static List<Ticket> getTicketForColdStart(ProjectsEnum project) throws GitAPIException, IOException, URISyntaxException, JSONException {
        String projectName = project.toString();
        VersionRetreiver versionRetreiver = new VersionRetreiver(projectName);
        TicketRetriever retriever = new TicketRetriever(versionRetreiver);
        List<Ticket> tickets = retriever.retrieveTickets(projectName);
        return retriever.getTicketWithIV(tickets);
    }
}
