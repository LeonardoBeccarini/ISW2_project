package org.example.controller.retriever;

import org.example.model.Ticket;
import org.example.model.Version;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.example.utils.JsonUtils.readJsonFromUrl;

public class TicketRetriever {
    private VersionRetreiver versionRetreiver;

    public TicketRetriever(VersionRetreiver versionRetreiver) {
        this.versionRetreiver = versionRetreiver;
    }

    public List<Ticket> retrieveTickets(String projectName) throws JSONException, IOException {
       ArrayList<Ticket> retrievedTickets = new ArrayList<>();
       int j, i = 0, total = 1;
       //Get JSON API for closed bugs w/ AV in the project
       do {
           //Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
           j = i + 1000;
           String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                   + projectName + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                   + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                   + i + "&maxResults=" + j;
           JSONObject json = readJsonFromUrl(url);
           JSONArray issues = json.getJSONArray("issues");
           total = json.getInt("total");

           for (; i < total && i < j; i++) {

               //Iterate through each bug
               String key = issues.getJSONObject(i%1000).get("key").toString();
               String resolutionDateString = issues.getJSONObject(i%1000).getJSONObject("fields").get("resolutiondate").toString();
               String creationDateString = issues.getJSONObject(i%1000).getJSONObject("fields").get("created").toString();
               LocalDate creationDate = LocalDate.parse(creationDateString.substring(0,10));
               LocalDate resolutionDate = LocalDate.parse(resolutionDateString.substring(0,10));

               List<Version> affectedVersions = versionRetreiver.getAffectedVersions(issues.getJSONObject(i%1000).getJSONObject("fields").getJSONArray("versions"));

               Ticket ticket = new Ticket(key, resolutionDate, creationDate, affectedVersions);
               Version openingVersion = versionRetreiver.getVersionAfter(ticket.getCreationDate());
               Version fixedVersion = versionRetreiver.getVersionAfter(ticket.getResolutionDate());
               ticket.setFixedVersion(fixedVersion);
               ticket.setOpeningVersion(openingVersion);
               ticket.setInjectedVersionTemp();
                // ticket is added only if FV>OV
               if(ticket.getFixedVersion() != null
                       && ticket.getOpeningVersion() != null &&
                       !(ticket.getFixedVersion().getIndex()<ticket.getOpeningVersion().getIndex())){
                   retrievedTickets.add(ticket);
               }
           }
       } while (i < total);
        retrievedTickets.sort(Comparator.comparing(Ticket::getResolutionDate));
       return retrievedTickets;
   }

   public List<Ticket> getTicketWithIV(List<Ticket> tickets) {
        List<Ticket> ticketsWithIV = new ArrayList<>();
        for (Ticket ticket : tickets) {
            if(ticket.hasIV()){
                ticketsWithIV.add(ticket);
            }
        }
        return ticketsWithIV;
   }

}
