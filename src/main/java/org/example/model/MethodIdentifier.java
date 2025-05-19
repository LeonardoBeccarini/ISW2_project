package org.example.model;

import java.util.Objects;

public class MethodIdentifier {
    private final String filePath;
    private final String methodName;
    private final int startLine;  // linea iniziale del metodo nel file
    private final int endLine;    // linea finale del metodo nel file

    public MethodIdentifier(String filePath, String methodName, int startLine, int endLine) {
        this.filePath = filePath;
        this.methodName = methodName;
        this.startLine = startLine;
        this.endLine = endLine;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodIdentifier that)) return false;
        return startLine == that.startLine &&
                endLine == that.endLine &&
                Objects.equals(filePath, that.filePath) &&
                Objects.equals(methodName, that.methodName);
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
                ", startLine=" + startLine +
                ", endLine=" + endLine +
                '}';
    }
}

