package org.example.controller.processors;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.controller.retriever.GitRetriever;
import org.example.model.ComplexityMetrics;
import org.example.model.LOCMetrics;
import org.example.model.MethodIdentifier;
import org.example.model.Metrics;

import java.io.IOException;
import java.util.*;

public class MetricsProcessor {
    private final GitRetriever gitRetriever;
    private final Set<MethodIdentifier> touchedMethods;

    public MetricsProcessor(GitRetriever gitRetriever, Set<MethodIdentifier> touchedMethods) {
        this.gitRetriever = gitRetriever;
        this.touchedMethods = touchedMethods;
    }

    public void computeAllMetrics() throws IOException {

        for (MethodIdentifier method : touchedMethods) {
            computeLocAndChurnMetrics(method);
            computeComplexityMetrics(method.getMetricsList(), method);
            computeNumberOfAuthors(method.getMetricsList(), method );
        }
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

        List<Integer> addedLines = method.getAddedLinesList();
        List<Integer> deletedLines = method.getDeletedLinesList();

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

        int revisionCount = addedLines.size();
        if (revisionCount > 0) {
            avgLOC = 1.0 * sumLOC / revisionCount;
            avgChurn = 1.0 * churn / revisionCount;
            avgDeletedLOC = 1.0 * sumOfTheDeletedLOC / revisionCount;
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

                            ComplexityMetrics cm = new ComplexityMetrics(
                                    cognitiveComplexity,
                                    statementCount,
                                    nestingDepth,
                                    parameterCount,
                                    0 // placeholder for code smell
                            );

                            metrics.setComplexityMetrics(cm);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void computeNumberOfAuthors(Metrics metrics, MethodIdentifier touchedMethod) {
        Set<String> authors = new HashSet<>();
        List<RevCommit> commitList = touchedMethod.getCommitList();
        if (commitList != null) {
            for (RevCommit commit : commitList) {
                String author = commit.getAuthorIdent().getEmailAddress();
                authors.add(author);
            }
        }
        metrics.setAuthors(authors.size());

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
