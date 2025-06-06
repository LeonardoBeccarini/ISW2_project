package org.example.controller.retriever;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import org.example.model.MethodIdentifier;
import org.example.utils.GitUtils;
import org.example.model.Ticket;
import org.example.model.Version;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitRetriever {
    private final VersionRetreiver versionRetriever;

    private final Git git;
    private final Repository repository;

    private List<RevCommit> commitList;

    public GitRetriever(String projectName, String projRepoUrl, VersionRetreiver versionRetriever) throws IOException, GitAPIException {
        this.versionRetriever = versionRetriever;
        // clone the repository
        String pathName = "repos/" + projectName.toLowerCase() + "Clone";
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

    // get the commits associated with a given ticket
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


    // from here method to retireve touched methods given a commit

    public void labelMethods(List<MethodIdentifier> methods, List<Ticket> tickets) throws GitAPIException {
        for(Ticket ticket : tickets){
            List<RevCommit> associatedCommits= ticket.getAssociatedCommits();
            for(RevCommit commit : associatedCommits){
                Version associatedVersion = versionRetriever.getVersionOfCommit(commit);
                if(associatedVersion != null){
                    List<String> modifiedMethods = getModifiedMethodsName(commit);
                    for (String modifiedMethod : modifiedMethods) {
                        updateJavaClassBuggyness(methods, modifiedMethod, ticket.getInjectedVersion(), ticket.getFixedVersion());                    }
                }
            }
        }
    }
    private void updateJavaClassBuggyness(List<MethodIdentifier> methodList, String methodName, Version iv, Version fv){
        for (MethodIdentifier method : methodList) {
            if(method.getMethodName().equals(methodName) &&
                    method.getVersion().getIndex()>= iv.getIndex() &&
                    method.getVersion().getIndex()< fv.getIndex()){
                method.getMetricsList().setBugged(true);
            }
        }

    }
    private List<String> getModifiedMethodsName(RevCommit commit){
        List<String> modifiedMethods = new ArrayList<>();
        try {
            List<MethodIdentifier> methodsTemp = getTouchedMethodsInCommit(commit);
            for(MethodIdentifier method : methodsTemp){
                modifiedMethods.add(method.getMethodName());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return modifiedMethods;
    }

    public List<MethodIdentifier> getTouchedMethods(List<Version> versionList) throws IOException, GitAPIException {
            List<MethodIdentifier> allTouched = new ArrayList<>();
            for (Version version : versionList) {
                for (RevCommit commit : version.getCommitList()) {
                    List<MethodIdentifier> methods = getTouchedMethodsInCommit(commit);
                    allTouched.addAll(methods);
                }
            }
            return allTouched;
    }


    private List<MethodIdentifier> getTouchedMethodsInCommit(RevCommit commit) throws IOException {
        List<MethodIdentifier> touchedMethods = new ArrayList<>();

        try (var revWalk = new org.eclipse.jgit.revwalk.RevWalk(repository)) {
            RevCommit parent = commit.getParentCount() > 0 ? revWalk.parseCommit(commit.getParent(0).getId()) : null;

            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();

            try (var reader = repository.newObjectReader()) {
                newTreeIter.reset(reader, commit.getTree().getId());
                if (parent != null) {
                    oldTreeIter.reset(reader, parent.getTree().getId());
                }

                try (DiffFormatter diffFormatter = new DiffFormatter(new ByteArrayOutputStream())) {
                    diffFormatter.setRepository(repository);
                    List<DiffEntry> diffs = parent == null
                            ? List.of()
                            : diffFormatter.scan(oldTreeIter, newTreeIter);

                    for (DiffEntry diff : diffs) {
                        String newPath = diff.getNewPath();
                        String oldPath = diff.getOldPath();

                        if (!newPath.endsWith(".java")) continue;

                        String newContent = readFileAtCommit(commit, newPath);
                        String oldContent = parent != null ? readFileAtCommit(parent, oldPath) : "";

                        EditList edits = diffFormatter.toFileHeader(diff).toEditList();

                        List<MethodIdentifier> methods = findTouchedMethodsIncludingDeletedLines(newPath, newContent, oldContent, edits);

                        // Per ogni MethodIdentifier trovato (senza version/commit), crea il nuovo MethodIdentifier con campi completi
                        for (MethodIdentifier method : methods) {
                            // Trova la versione più recente non successiva alla data del commit
                            Version version = null;
                            for (Version v : versionRetriever.getVersionList()) {
                                if (v.getCommitList().contains(commit)) {
                                    version = v;
                                    break;
                                }
                            }
                            // Crea la lista commit con il commit corrente
                            List<RevCommit> commitList = new ArrayList<>();
                            commitList.add(commit);

                            method.setVersion(version);
                            method.setCommitList(commitList);
                            touchedMethods.add(method);
                        }
                    }
                }
            }
        }

        return touchedMethods;
    }



    private List<MethodIdentifier> findTouchedMethodsIncludingDeletedLines(
            String filePath,
            String newContent,
            String oldContent,
            EditList edits) {

        Map<String, int[]> methodToCounts = new HashMap<>();

        JavaParser parser = new JavaParser();
        ParseResult<CompilationUnit> result = parser.parse(newContent);
        if (result.isSuccessful() && result.getResult().isPresent()) {
            CompilationUnit cu = result.getResult().get();
            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
            methods.sort(Comparator.comparingInt(m -> m.getRange().map(r -> r.begin.line).orElse(Integer.MAX_VALUE)));

            for (MethodDeclaration method : methods) {
                Optional<Range> maybeRange = method.getRange();
                if (maybeRange.isEmpty()) continue;
                Range range = maybeRange.get();
                int begin = range.begin.line;
                int end = range.end.line;

                int addedCount = 0;
                int deletedCount = 0;
                for (Edit edit : edits) {
                    switch (edit.getType()) {
                        case INSERT:
                            if (edit.getBeginB() + 1 >= begin && edit.getEndB() <= end) {
                                addedCount += edit.getLengthB();
                            }
                            break;
                        case DELETE:
                            if (edit.getBeginA() + 1 >= begin && edit.getEndA() <= end) {
                                deletedCount += edit.getLengthA();
                            }
                            break;
                        case REPLACE:
                            if (edit.getBeginB() + 1 >= begin && edit.getEndB() <= end) {
                                addedCount += edit.getLengthB();
                            }
                            if (edit.getBeginA() + 1 >= begin && edit.getEndA() <= end) {
                                deletedCount += edit.getLengthA();
                            }
                            break;
                        default:
                            break;
                    }
                }
                if (addedCount == 0 && deletedCount == 0) continue;

                String key = filePath + "#" + method.getNameAsString() + "#" + begin + "#" + end;
                int[] counts = methodToCounts.getOrDefault(key, new int[2]);
                counts[0] += addedCount;    // sum added lines
                counts[1] += deletedCount;  // sum deleted lines
                methodToCounts.put(key, counts);
            }
        }

        List<MethodIdentifier> touchedMethods = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : methodToCounts.entrySet()) {
            String[] parts = entry.getKey().split("#");
            String filePathKey = parts[0];
            String methodName = parts[1];
            int startLine = Integer.parseInt(parts[2]);
            int endLine = Integer.parseInt(parts[3]);
            int[] counts = entry.getValue();

            MethodIdentifier m = new MethodIdentifier(filePathKey, methodName, startLine, endLine);
            m.addAddedLineCount(counts[0]);
            m.addDeletedLineCount(counts[1]);
            touchedMethods.add(m);
        }
        return touchedMethods;
    }

    // Retained for backward compatibility (unused by new logic)
    private Set<MethodIdentifier> findMethodsWithAddedDeletedLines(
            String filePath,
            String newContent,
            String oldContent,
            Set<Integer> newChangedLines,
            Set<Integer> oldChangedLines) {
        // ... original code unchanged ...
        // This method is now unused by the new counting logic
        return Collections.emptySet();
    }



    private boolean allLinesAreCommentsOrWhitespace(Set<Integer> linesToCheck, String[] fileLines) {
        for (int lineNumber : linesToCheck) {
            // Attenzione: lineNumber è 1-based, gli array sono 0-based
            if (lineNumber < 1 || lineNumber > fileLines.length) continue;
            String lineText = fileLines[lineNumber - 1].trim();

            if (lineText.isEmpty()) continue;

            // Semplice check commenti (linea singola e inizio blocco)
            if (lineText.startsWith("//")) continue;
            if (lineText.startsWith("/*") || lineText.startsWith("*") || lineText.endsWith("*/")) continue;

            // Se linea non è commento né vuota, ritorna false
            return false;
        }
        return true;
    }

    private Set<MethodIdentifier> removeDuplicatesIgnoringParent(Set<MethodIdentifier> methods) {
        Map<String, MethodIdentifier> uniqueMethods = new LinkedHashMap<>();
        for (MethodIdentifier m : methods) {
            String normalizedFilePath = m.getFilePath().replace(" (parent)", "");
            String key = normalizedFilePath + "|" + m.getMethodName() + "|" + m.getStartLine() + "|" + m.getEndLine();
            if (!uniqueMethods.containsKey(key)) {
                MethodIdentifier normalizedMethod = new MethodIdentifier(normalizedFilePath, m.getMethodName(), m.getStartLine(), m.getEndLine());
                uniqueMethods.put(key, normalizedMethod);
            }
        }
        return new LinkedHashSet<>(uniqueMethods.values());
    }


    public String readFileAtCommit(RevCommit commit, String path) throws IOException {
        try (var reader = repository.newObjectReader()) {
            var treeWalk = org.eclipse.jgit.treewalk.TreeWalk.forPath(reader, path, commit.getTree());
            if (treeWalk == null) {
                System.out.println("DEBUG: File not found in commit: " + path);
                return "";
            }
            var blobId = treeWalk.getObjectId(0);
            var loader = repository.open(blobId);
            byte[] bytes = loader.getBytes();
         //   System.out.println("DEBUG: Read file '" + path + "' with size: " + bytes.length);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

}
