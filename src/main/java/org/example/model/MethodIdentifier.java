package org.example.model;

import org.eclipse.jgit.revwalk.RevCommit;

import java.util.*;

public class MethodIdentifier {
    private final String filePath;
    private final String methodName;
    private Version version;
    private List<RevCommit> commitList;
    private Metrics metricsList;
    private final int startLine;  // linea iniziale del metodo nel file
    private final int endLine;    // linea finale del metodo nel file
    private List<Integer> addedLinesList = new ArrayList<>();
    private List<Integer> deletedLinesList = new ArrayList<>();



    public MethodIdentifier(String filePath, String methodName, Version version,
                            List<RevCommit> commitList, Metrics metricsList,
                            int startLine, int endLine) {
        this.filePath = filePath;
        this.methodName = methodName;
        this.version = version;
        this.commitList = commitList;
        this.metricsList = metricsList;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    // Costruttore minimale senza version, commitList e metricsList
    public MethodIdentifier(String filePath, String methodName, int startLine, int endLine) {
        this(filePath, methodName, null, null, null, startLine, endLine);
    }

    // Getter finali
    public String getFilePath() {
        return filePath;
    }

    public String getMethodName() {
        return methodName;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    // Getter e Setter per i campi mutabili
    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public List<RevCommit> getCommitList() {
        return commitList;
    }

    public void setCommitList(List<RevCommit> commitList) {
        this.commitList = commitList;
    }

    public Metrics getMetricsList() {
        return metricsList;
    }

    public void setMetricsList(Metrics metricsList) {
        this.metricsList = metricsList;
    }

    // equals e hashCode includono solo i campi finali (filePath, methodName, startLine, endLine)
    // Se vuoi includere anche version, commitList o metricsList, considera la loro mutabilità

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodIdentifier)) return false;
        MethodIdentifier that = (MethodIdentifier) o;
        return startLine == that.startLine &&
                endLine == that.endLine &&
                Objects.equals(filePath, that.filePath) &&
                Objects.equals(methodName, that.methodName);
    }

    public void addAddedLineCount(int count) {
        addedLinesList.add(count);
    }

    public void addDeletedLineCount(int count) {
        deletedLinesList.add(count);
    }

    public List<Integer> getAddedLinesList() {
        return addedLinesList;
    }

    public List<Integer> getDeletedLinesList() {
        return deletedLinesList;
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, methodName, startLine, endLine);
    }

    @Override
    public String toString() {
        return "MethodIdentifier{" +
                "filePath='" + filePath + '\'' +
                ", methodName='" + methodName + '\'' +
                ", version=" + (version != null ? version.getName() : "null") +
                ", startLine=" + startLine +
                ", endLine=" + endLine +
                '}';
    }
}
