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

    public Set<MethodIdentifier> getTouchedMethods(List<Version> versionList) throws IOException, GitAPIException {
            Set<MethodIdentifier> allTouched = new HashSet<>();
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

                        Set<MethodIdentifier> methods = findTouchedMethodsIncludingDeletedLines(newPath, newContent, oldContent, edits);

                        // Per ogni MethodIdentifier trovato (senza version/commit), crea il nuovo MethodIdentifier con campi completi
                        for (MethodIdentifier method : methods) {
                            // Trova la versione più recente non successiva alla data del commit
                            Version version = versionRetriever.getVersionList().stream()
                                    .filter(v -> !v.getDate().isAfter(
                                            commit.getCommitterIdent().getWhen()
                                                    .toInstant()
                                                    .atZone(ZoneId.systemDefault())
                                                    .toLocalDate()))
                                    .max(Comparator.comparing(Version::getDate))
                                    .orElse(null);

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



    private Set<MethodIdentifier> findTouchedMethodsIncludingDeletedLines(String filePath,
                                                                          String newContent,
                                                                          String oldContent,
                                                                          EditList edits) {

        // Linee modificate nel file nuovo (INSERT + REPLACE)
        Set<Integer> newChangedLines = new HashSet<>();
        for (Edit edit : edits) {
            if (edit.getType() == Edit.Type.INSERT || edit.getType() == Edit.Type.REPLACE) {
                for (int line = edit.getBeginB(); line < edit.getEndB(); line++) {
                    newChangedLines.add(line + 1);
                }
            }
        }

        // Linee cancellate e sostituite nel file vecchio (DELETE + REPLACE)
        Set<Integer> oldChangedLines = new HashSet<>();
        for (Edit edit : edits) {
            if (edit.getType() == Edit.Type.DELETE || edit.getType() == Edit.Type.REPLACE) {
                for (int line = edit.getBeginA(); line < edit.getEndA(); line++) {
                    oldChangedLines.add(line + 1);
                }
            }
        }

        // Trova metodi nel file nuovo per linee modificate nel nuovo e vecchio con conteggio linee
        Set<MethodIdentifier> touchedMethods = findMethodsWithAddedDeletedLines(
                filePath, newContent, oldContent, newChangedLines, oldChangedLines);

        return touchedMethods;
    }

    private Set<MethodIdentifier> findMethodsWithAddedDeletedLines(
            String filePath,
            String newContent,
            String oldContent,
            Set<Integer> newChangedLines,
            Set<Integer> oldChangedLines) {

        Set<MethodIdentifier> methodsTouched = new HashSet<>();

        String[] newLines = newContent.split("\n");
        String[] oldLines = oldContent != null ? oldContent.split("\n") : new String[0];

        try {
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> result = parser.parse(newContent);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                CompilationUnit cu = result.getResult().get();

                List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
                methods.sort(Comparator.comparingInt(m -> m.getRange().map(r -> r.begin.line).orElse(Integer.MAX_VALUE)));

                for (MethodDeclaration method : methods) {
                    Optional<Range> maybeRange = method.getRange();
                    if (maybeRange.isEmpty()) {
                        System.out.println("DEBUG: Metodo senza range: " + method.getNameAsString());
                        continue;
                    }
                    Range range = maybeRange.get();
                    int begin = range.begin.line;
                    int end = range.end.line;

                    // Count added lines inside method range
                    Set<Integer> addedLinesInMethod = new HashSet<>();
                    for (int line : newChangedLines) {
                        if (line >= begin && line <= end) {
                            addedLinesInMethod.add(line);
                        }
                    }

                    // Count deleted lines inside method range
                    Set<Integer> deletedLinesInMethod = new HashSet<>();
                    for (int line : oldChangedLines) {
                        if (line >= begin && line <= end) {
                            deletedLinesInMethod.add(line);
                        }
                    }

                    if (addedLinesInMethod.isEmpty() && deletedLinesInMethod.isEmpty()) {
                        // Nessuna modifica nel metodo
                        continue;
                    }

                    // Controlla se tutte le linee modificate sono commenti o vuote
                    boolean allAddedComments = allLinesAreCommentsOrWhitespace(addedLinesInMethod, newLines);
                    boolean allDeletedComments = allLinesAreCommentsOrWhitespace(deletedLinesInMethod, oldLines);

                    if (allAddedComments && allDeletedComments) {
                        // Ignora metodo modificato se solo commenti
                        continue;
                    }

                    MethodIdentifier m = new MethodIdentifier(filePath, method.getNameAsString(), begin, end);
                    // Set counts of lines added and deleted inside this method
                    m.addAddedLineCount(addedLinesInMethod.size());
                    m.addDeletedLineCount(deletedLinesInMethod.size());

                    methodsTouched.add(m);
                }
            } else {
                System.out.println("DEBUG: JavaParser non è riuscito a parsare il file: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Errore nel parsing file Java '" + filePath + "': " + e.getMessage());
            e.printStackTrace();
        }

        return methodsTouched;
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
