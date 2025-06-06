package org.example.controller.processors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.example.enums.ProjectsEnum;
import org.example.model.Ticket;
import org.example.model.Version;
import org.example.utils.ColdStartUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Proportion {
    private double coldStartProportionValue = -1;

    public List<Ticket> processProportion(@NotNull List<Ticket> tickets, List<Version> versions) {
        List<Ticket> ticketsForProportionList = new ArrayList<>();
        List<Ticket> finalTicketList = new ArrayList<>();
        double proportion;

        LocalDate firstTicketWithIVDate = tickets
                .stream()
                .filter(Ticket::hasIV)
                .toList()
                .getFirst().getResolutionDate();

        for (Ticket ticket : tickets) {

            // If the ticket has a list of AVs (i.e., it already has an IV)
            if (ticket.hasIV()) {
                ticketsForProportionList.add(ticket);
            }


            // If the ticket doesn't have an IV, treat it as cold start
            else if (ticket.getResolutionDate().isBefore(firstTicketWithIVDate)) {
                try {
                    proportion = computeColdStartProportionValue();
                    computeInjectedVersion(ticket, versions, proportion);
                    computeAffectedVersionsList(ticket, versions);
                } catch (GitAPIException | IOException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
            // If the ticket doesn't have a list of AVs (and it's not before the first IV ticket), compute proportion
            // TODO applica cold start fino a quando la ticketForProportionList non ha abbastanza ticket per calcolare un valore di proportion
            else if (!ticket.hasIV() && !ticket.getResolutionDate().isBefore(firstTicketWithIVDate)) {

                if(ticketsForProportionList.size()< 6){
                    try {
                        proportion = computeColdStartProportionValue();
                    } catch (GitAPIException | IOException | URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                } else { proportion = getProportion(ticketsForProportionList);}

                System.out.printf("Proportion value of ticket %s is %.2f\n", ticket.getKey(), proportion);
                System.out.flush();

                computeInjectedVersion(ticket, versions, proportion);
                computeAffectedVersionsList(ticket, versions);

            }

            finalTicketList.add(ticket);
        }
        finalTicketList.removeIf(ticket-> (ticket.getInjectedVersion().getIndex()>ticket.getOpeningVersion().getIndex() ||
                ticket.getInjectedVersion().getIndex()>ticket.getFixedVersion().getIndex()));

        finalTicketList.sort(Comparator.comparing(Ticket::getResolutionDate));

        return finalTicketList;
    }

    private double computeColdStartProportionValue() throws GitAPIException, IOException, URISyntaxException {
        if (this.coldStartProportionValue != -1) return this.coldStartProportionValue;

        List<Double> proportionValueList = new ArrayList<>();

        for (ProjectsEnum proj : ProjectsEnum.values()) {
            double p;
            try {
                List<Ticket> tickets = ColdStartUtils.getTicketForColdStart(proj);
                p = getProportion(tickets);
                System.out.printf("ColdStartUtils proportion value of %s is %.2f\n", proj, p);
            } catch (GitAPIException | IOException | URISyntaxException | JSONException e) {
                throw new RuntimeException(e);
            }
            if (p != 0)
                proportionValueList.add(p);

        }

        this.coldStartProportionValue = computeMedian(proportionValueList);
        return coldStartProportionValue;
    }

    private double computeMedian(@NotNull List<Double> proportionValueList) {
        proportionValueList.sort(Double::compareTo);
        if (proportionValueList.size() % 2 != 0) {
            return proportionValueList.get((proportionValueList.size() - 1) / 2);
        } else {
            double v1 = proportionValueList.get((proportionValueList.size() - 1) / 2);
            double v2 = proportionValueList.get(proportionValueList.size() / 2);
            return (v1 + v2) / 2;
        }
    }

    private double getProportion(@NotNull List<Ticket> ticketsForProportion) {

        ticketsForProportion.sort(Comparator.comparing(Ticket::getResolutionDate));
        double proportionTotal = 0;
        double proportionForTicket;
        int counter = 0;

        for (Ticket ticketWithIV : ticketsForProportion) {
            int iv = ticketWithIV.getInjectedVersion().getIndex();
            int ov = ticketWithIV.getOpeningVersion().getIndex();
            int fv = ticketWithIV.getFixedVersion().getIndex();
            if (isAValidTicketForProportion(ticketWithIV)) {
                if (fv == ov) {
                    proportionForTicket = (1.0) * (fv - iv);
                } else {
                    proportionForTicket = (1.0) * (fv - iv) / (fv - ov);
                }
                proportionTotal += proportionForTicket;
                counter++;
            }

        }
        if (counter == 0)
            return 0;

        return proportionTotal / counter;
    }


    private void computeInjectedVersion(@NotNull Ticket ticket, List<Version> versionList, double proportion) {
        int injectedVersionId;
        int fv = ticket.getFixedVersion().getIndex();
        int ov = ticket.getOpeningVersion().getIndex();
        // Predicted IV = FV - (FV-OV)*P
        // If FV = OV, then use FV - proportion
        if (fv == ov) {
            injectedVersionId = Math.clamp(
                    (int) (fv - proportion),
                    1, versionList.size() - 1
            );
        } else {
            // Corrected formula: IV = FV - (FV-OV)*P
            injectedVersionId = Math.clamp(
                    (int) (fv - ((fv - ov) * proportion)),
                    1, versionList.size() - 1
            );
        }

        // Assign the IV to the ticket
        ticket.setInjectedVersion(versionList.stream()
                .filter(release -> release.getIndex() == injectedVersionId)
                .toList()
                .getFirst()
        );
    }

    /**
     * Set, given the release list, the AVs of a ticket
     *
     * @param ticket      the ticket to assign the AVs to
     * @param versionList the release list to extract the AVs from
     */
    private void computeAffectedVersionsList(Ticket ticket, List<Version> versionList) {
        List<Version> completeAffectedVersionsList = new ArrayList<>();

        // AV IDs are such that: IV <= AV(i) <= OV
        for (Version version : versionList
                .stream()
                .filter(version ->
                        (version.getIndex() >= ticket.getInjectedVersion().getIndex())
                                && (version.getIndex() <= ticket.getOpeningVersion().getIndex()))
                .toList()) {
            completeAffectedVersionsList.add(new Version(version.getId(), version.getName(), version.getDate()));
        }
        ticket.setAffectedVersions(completeAffectedVersionsList);
    }

    public static boolean isAValidTicketForProportion(@NotNull Ticket ticket) {

        if (ticket.getInjectedVersion() == null || ticket.getOpeningVersion() == null || ticket.getFixedVersion() == null)
            return false;

        int iv = ticket.getInjectedVersion().getIndex();
        int ov = ticket.getOpeningVersion().getIndex();
        return ov != iv;
    }
}
