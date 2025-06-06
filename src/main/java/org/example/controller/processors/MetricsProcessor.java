package org.example.controller.processors;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.reporting.Report;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.controller.retriever.GitRetriever;
import org.example.model.ComplexityMetrics;
import org.example.model.LOCMetrics;
import org.example.model.MethodIdentifier;
import org.example.model.Metrics;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class MetricsProcessor {
    private final GitRetriever gitRetriever;
    private final List<MethodIdentifier> touchedMethods;

    public MetricsProcessor(GitRetriever gitRetriever, List<MethodIdentifier> touchedMethods) {
        this.gitRetriever = gitRetriever;
        this.touchedMethods = touchedMethods;
    }

    public List<MethodIdentifier> computeAllMetrics() throws IOException {
        List<MethodIdentifier> methodList = new ArrayList<>();
        // 1) Group by signature
        Map<String, MethodIdentifier> aggBySig = new LinkedHashMap<>();
        // DEBUG
        Map<String, Integer> counts = new HashMap<>();
        for (MethodIdentifier touch : touchedMethods) {
            String key = touch.getFilePath() + "|" + touch.getMethodName() + "|" + touch.getVersion() + "|" +
                    touch.getStartLine() + "|" + touch.getEndLine();
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        counts.forEach((k,v) -> System.out.println("Method " + k + " appears " + v + " times in touchedMethods"));
        // DEBUG
        for (MethodIdentifier touch : touchedMethods) {
            String key = touch.getFilePath() + "|" + touch.getMethodName() + "|" + touch.getVersion() + "|" +
                    touch.getStartLine() + "|" + touch.getEndLine();

            MethodIdentifier agg = aggBySig.computeIfAbsent(key, k -> new MethodIdentifier(
                    touch.getFilePath(), touch.getMethodName(), touch.getVersion(),
                    touch.getStartLine(), touch.getEndLine()
            ));

            // Merge added and deleted lines counts
            for (int count : touch.getAddedLinesListCount()) {
                agg.addAddedLineCount(count);  // Aggregate the added line counts
            }

            for (int count : touch.getDeletedLinesListCount()) {
                agg.addDeletedLineCount(count);  // Aggregate the deleted line counts
            }

            // Merge commit lists
            agg.getCommitList().addAll(touch.getCommitList());
        }

        // 2) Compute metrics on aggregated methods
        for (MethodIdentifier method : aggBySig.values()) {
            System.out.println("[DEBUG] Method: " + method);
            System.out.println("[DEBUG] Added lines list: " + method.getAddedLinesListCount());
            System.out.println("[DEBUG] Deleted lines list: " + method.getDeletedLinesListCount());
            computeLocAndChurnMetrics(method);     // unchanged
            computeComplexityMetrics(method.getMetricsList(), method);
            computeNumberOfAuthors(method.getMetricsList(), method);

            int methodHistories = method.getCommitList() == null ? 0 : method.getCommitList().size();
            method.getMetricsList().setMethodHistories(methodHistories);

            methodList.add(method);
        }
        return methodList;
    }



    public void computeLocAndChurnMetrics(MethodIdentifier method) {
        int sumLOC = 0;
        int maxLOC = 0;
        double avgLOC = 0;
        int churn = 0;
        int maxChurn = 0;
        double avgChurn = 0;
        int sumOfTheDeletedLOC = 0;
        int maxDeletedLOC = 0;
        double avgDeletedLOC = 0;

        List<Integer> addedLines = method.getAddedLinesListCount();
        List<Integer> deletedLines = method.getDeletedLinesListCount();

        for (int i = 0; i < addedLines.size(); i++) {
            int currentLOC = addedLines.get(i);
            int currentDeletedLOC = deletedLines.get(i);
            int currentDiff = Math.abs(currentLOC - currentDeletedLOC);

            sumLOC += currentLOC;
            churn += currentDiff;
            sumOfTheDeletedLOC += currentDeletedLOC;

            if (currentLOC > maxLOC) maxLOC = currentLOC;
            if (currentDiff > maxChurn) maxChurn = currentDiff;
            if (currentDeletedLOC > maxDeletedLOC) maxDeletedLOC = currentDeletedLOC;
        }

        if(!addedLines.isEmpty()){
            avgLOC = 1.0 * sumLOC / addedLines.size();
            avgChurn = 1.0 * churn / addedLines.size();
        }
        if(!deletedLines.isEmpty()){
            avgDeletedLOC = 1.0 * sumOfTheDeletedLOC / deletedLines.size();
        }

        LOCMetrics stmtAdded = new LOCMetrics();
        stmtAdded.setVal(sumLOC);
        stmtAdded.setMaxVal(maxLOC);
        stmtAdded.setAvgVal(avgLOC);

        LOCMetrics stmtDeleted = new LOCMetrics();
        stmtDeleted.setVal(sumOfTheDeletedLOC);
        stmtDeleted.setMaxVal(maxDeletedLOC);
        stmtDeleted.setAvgVal(avgDeletedLOC);

        LOCMetrics churnMetrics = new LOCMetrics();
        churnMetrics.setVal(churn);
        churnMetrics.setMaxVal(maxChurn);
        churnMetrics.setAvgVal(avgChurn);

        Metrics metrics = new Metrics();
        metrics.setStmtAdded(stmtAdded);
        metrics.setStmtDeleted(stmtDeleted);
        metrics.setChurnMetrics(churnMetrics);

        method.setMetricsList(metrics);
    }

    private void computeComplexityMetrics(Metrics metrics, MethodIdentifier method) throws IOException {
        List<RevCommit> commitList = method.getCommitList();
        if (commitList != null && !commitList.isEmpty()) {
            RevCommit latestCommit = commitList.stream()
                    .max(Comparator.comparing(c -> c.getCommitterIdent().getWhen()))
                    .orElse(null);

            if (latestCommit != null) {
                String fileContent = gitRetriever.readFileAtCommit(latestCommit, method.getFilePath());
                JavaParser parser = new JavaParser();
                ParseResult<CompilationUnit> result = parser.parse(fileContent);

                if (result.isSuccessful() && result.getResult().isPresent()) {
                    CompilationUnit cu = result.getResult().get();
                    List<MethodDeclaration> methodDecls = cu.findAll(MethodDeclaration.class);
                    for (MethodDeclaration md : methodDecls) {
                        Optional<Range> range = md.getRange();
                        if (range.isPresent()
                                && range.get().begin.line == method.getStartLine()
                                && range.get().end.line == method.getEndLine()
                                && md.getNameAsString().equals(method.getMethodName())) {

                            int statementCount = md.findAll(Statement.class).size();
                            int parameterCount = md.getParameters().size();
                            int nestingDepth = computeMaxNestingDepth(md.getBody().orElse(null));
                            int cognitiveComplexity = computeCognitiveComplexity(md);
                            int codeSmells = computeCodeSmellsForMethod(md, latestCommit, method.getFilePath());
                            int duplication = computeDuplication(md);
                            
                            ComplexityMetrics cm = new ComplexityMetrics(
                                    cognitiveComplexity,
                                    statementCount,
                                    nestingDepth,
                                    parameterCount,
                                    codeSmells,
                                    duplication
                            );

                            metrics.setComplexityMetrics(cm);
                            break;
                        }
                    }
                }
            }
        }
    }

    private int computeDuplication(MethodDeclaration md) {
        if (md.getBody().isEmpty()) {
            return 0;
        }

        List<Statement> statements = md.getBody().get().getStatements();

        Map<String, Integer> stmtStringCounts = new HashMap<>();
        int duplicationCount = 0;

        for (Statement stmt : statements) {
            String stmtStr = stmt.toString().trim();
            if (stmtStr.isEmpty()) continue;

            int count = stmtStringCounts.getOrDefault(stmtStr, 0);
            if (count == 1) {
                duplicationCount++;
            }
            stmtStringCounts.put(stmtStr, count + 1);
        }
        return duplicationCount;
    }


    private int computeCodeSmellsForMethod(MethodDeclaration md,
                                           RevCommit commit,
                                           String filePath) throws IOException {
        // 1) Recupera il sorgente del file al commit specificato
        String fileContent = gitRetriever.readFileAtCommit(commit, filePath);

        // 2) Scrive il sorgente in un file temporaneo per PMD
        Path tmp = Files.createTempFile("pmd-src-", ".java");
        Files.writeString(tmp, fileContent);

        // 3) Configura PMD con i rule set desiderati e il file di input
        PMDConfiguration config = new PMDConfiguration();
        config.setRuleSets(List.of(
                "category/java/codestyle.xml",
                "category/java/design.xml"
        ));
        config.addInputPath(tmp);

        // 4) Esegue l’analisi e raccoglie il report
        try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
            Report report = pmd.performAnalysisAndCollectReport();

            // 5) Conta le violation all’interno del range di righe del metodo
            return (int) report.getViolations().stream()
                    .filter(v -> {
                        int b = v.getBeginLine(), e = v.getEndLine();
                        return md.getRange()
                                .map(r -> b >= r.begin.line && e <= r.end.line)
                                .orElse(false);
                    })
                    .count();
        } finally {
            Files.deleteIfExists(tmp);
        }
    }



    private void computeNumberOfAuthors(Metrics metrics, MethodIdentifier touchedMethod) {
        List<RevCommit> commits = touchedMethod.getCommitList();
        if (commits == null || commits.isEmpty()) {
            System.out.println("No commits found for method " + touchedMethod);
            metrics.setAuthors(0);
            return;
        }

        Set<String> distinctEmails = commits.stream()
                .map(c -> c.getAuthorIdent().getEmailAddress())
                .collect(Collectors.toSet());

        System.out.println("Distinct authors for method " + touchedMethod + ": " + distinctEmails);

        metrics.setAuthors(distinctEmails.size());
    }

    private int computeMaxNestingDepth(Node node) {
        return computeDepthRecursive(node, 0);
    }

    private int computeDepthRecursive(Node node, int currentDepth) {
        if (node == null) return currentDepth;

        int maxDepth = currentDepth;
        for (Node child : node.getChildNodes()) {
            if (child instanceof BlockStmt || child instanceof IfStmt || child instanceof WhileStmt ||
                    child instanceof ForStmt || child instanceof ForEachStmt || child instanceof SwitchStmt ||
                    child instanceof DoStmt || child instanceof TryStmt) {
                int depth = computeDepthRecursive(child, currentDepth + 1);
                maxDepth = Math.max(maxDepth, depth);
            } else {
                maxDepth = Math.max(maxDepth, computeDepthRecursive(child, currentDepth));
            }
        }
        return maxDepth;
    }

    private int computeCognitiveComplexity(Node node) {
        return computeCognitiveComplexityRecursive(node, 0);
    }

    private int computeCognitiveComplexityRecursive(Node node, int nesting) {
        if (node == null) return 0;

        int complexity = 0;
        for (Node child : node.getChildNodes()) {
            if (child instanceof IfStmt || child instanceof WhileStmt || child instanceof ForStmt ||
                    child instanceof ForEachStmt || child instanceof SwitchStmt || child instanceof DoStmt ||
                    child instanceof CatchClause || child instanceof ConditionalExpr) {
                complexity += 1 + nesting;
                complexity += computeCognitiveComplexityRecursive(child, nesting + 1);
            } else {
                complexity += computeCognitiveComplexityRecursive(child, nesting);
            }
        }
        return complexity;
    }
}
