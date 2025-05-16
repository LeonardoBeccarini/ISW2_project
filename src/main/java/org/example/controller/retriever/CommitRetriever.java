package org.example.controller.retriever;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.example.utils.GitUtils;
import org.example.model.Ticket;
import org.example.model.Version;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommitRetriever {
    private final VersionRetreiver versionRetriever;

    private final Git git;
    private final Repository repository;

    private final String CLONE_DIR = "repos/";

    private List<RevCommit> commitList;

    public CommitRetriever(String projectName, String projRepoUrl, VersionRetreiver versionRetriever) throws IOException, GitAPIException {
        this.versionRetriever = versionRetriever;
        // clone the repository
        String pathName = CLONE_DIR + projectName.toLowerCase() + "Clone";
        File directory = new File(pathName);
        if (directory.exists()) {
            repository = new FileRepositoryBuilder()
                    .setGitDir(new File(pathName, ".git"))
                    .build();
            git = new Git(repository);
        } else {
            git = Git.cloneRepository()
                    .setURI(projRepoUrl)
                    .setDirectory(directory).call();
            repository = git.getRepository();
        }
    }

    public List<RevCommit> retrieveAllCommits() throws GitAPIException {

        if(commitList != null){
            return commitList;
        }
        List<RevCommit> commits = new ArrayList<>();
        Iterable<RevCommit> commitsIterable = git.log().call();
        List<Version> projVersions = versionRetriever.getVersionList();
        Version lastVersion = projVersions.get(projVersions.size() - 1);
// add a commit to the retrieved commits list only if its date is before the date of the last release.
        for (RevCommit commit : commitsIterable) {
            if(!GitUtils.castToLocalDate(commit.getCommitterIdent().getWhen()).isAfter(lastVersion.getDate())){
                commits.add(commit);
            }
        }
        commits.sort(Comparator.comparing(o->o.getCommitterIdent().getWhen()));
        this.commitList = commits;
        return commits;
    }

    // get the commits associated to a given ticket
    private List<RevCommit> getAssociatedCommits(@NotNull List<RevCommit> commits, Ticket ticket) {
        List<RevCommit> associatedCommit = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\b"+ ticket.getKey()+ "\\b");
        for (RevCommit commit : commits) {
            String commitMessage = commit.getFullMessage();
            Matcher matcher = pattern.matcher(commitMessage);
            if(matcher.find()){
                associatedCommit.add(commit);
            }
        }
        return associatedCommit;
    }

    // for every ticket get its associated commits and check whether they are consistent
    public void associateCommitToTicket(@NotNull List<Ticket> tickets) throws GitAPIException {
        List<RevCommit> commits = this.retrieveAllCommits();
        for(Ticket ticket : tickets){
            List<RevCommit> associatedCommits = this.getAssociatedCommits(commits, ticket);
            List<RevCommit> consistentCommits = new ArrayList<>();
            for(RevCommit commit : associatedCommits){
                LocalDate when = GitUtils.castToLocalDate(commit.getCommitterIdent().getWhen());
                // consistency checks: commit date <= fixedVersionDate and commit date > injectedVersionDate TODO(dubbio: non dovrebbe essere < e >=?)
                // in questo modo prendo solo i bug-fixing commit
                if(!ticket.getFixedVersion().getDate().isBefore(when) &&
                !ticket.getInjectedVersion().getDate().isAfter(when)){
                    consistentCommits.add(commit);
                }
            }
            ticket.setAssociatedCommits(consistentCommits);
        }
        //delete tickets without consistent commits associated
        tickets.removeIf(ticket -> ticket.getAssociatedCommits().isEmpty());
    }

    public void associateCommitToVersion() throws GitAPIException{
        LocalDate lowerBound = LocalDate.of(1970,1,1); // Unix epoch as lower bound
        List<Version> versions = versionRetriever.getVersionList();
        for(Version version: versions){
            LocalDate currentVersionDate = version.getDate();
            for(RevCommit commit : this.retrieveAllCommits()){
                LocalDate commitDate = GitUtils.castToLocalDate(commit.getCommitterIdent().getWhen());
                if(commitDate.isBefore(currentVersionDate) || commitDate.isEqual(currentVersionDate) && commitDate.isBefore(lowerBound)){
                    version.addCommit(commit);
                }
            }
            lowerBound = currentVersionDate;
        }
        versionRetriever.deleteVersionWithoutCommits();
    }
}
