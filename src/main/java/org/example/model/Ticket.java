package org.example.model;

import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDate;
import java.util.List;

public class Ticket {
    private String key;
    private LocalDate creationDate;
    private LocalDate resolutionDate;
    private List<Version> affectedVersions;
    private Version injectedVersion;
    private Version openingVersion;
    private Version fixedVersion;
    private List<RevCommit> associatedCommits;

    public Ticket(String key, LocalDate creationDate, LocalDate resolutionDate, List<Version> affectedVersions) {
        this.key = key;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;
        this.affectedVersions = affectedVersions;
    }

    public String getKey() {
        return key;
    }

    public LocalDate getCreationDate() {
        return creationDate;
    }

    public LocalDate getResolutionDate() {
        return resolutionDate;
    }

    public List<RevCommit> getAssociatedCommits() {
        return associatedCommits;
    }

    public void setAssociatedCommits(List<RevCommit> associatedCommits) {
        this.associatedCommits = associatedCommits;
    }

    public Version getInjectedVersion() {
        return injectedVersion;
    }

    public Version getOpeningVersion() {
        return openingVersion;
    }

    public Version getFixedVersion() {
        return fixedVersion;
    }

    public List<Version> getAffectedVersions() {
        return affectedVersions;
    }

    public void setInjectedVersion() {
        if(!affectedVersions.isEmpty()) this.injectedVersion = affectedVersions.get(0);
        else{
            this.injectedVersion = null;
        }
    }

    public void setOpeningVersion(Version openingVersion) {
        this.openingVersion = openingVersion;
    }

    public void setFixedVersion(Version fixedVersion) {
        this.fixedVersion = fixedVersion;
    }
}
