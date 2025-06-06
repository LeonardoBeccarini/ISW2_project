package org.example.model;

public class ComplexityMetrics {
    private final int cognitiveComplexity;
    private final int statementCount;
    private final int nestingDepth;
    private final int numberOfCodeSmells;
    private final int parameterCount;
    private final int duplication;

    public ComplexityMetrics(int cognitiveComplexity, int statementCount) {
        this.cognitiveComplexity = cognitiveComplexity;
        this.statementCount = statementCount;
        this.nestingDepth = 0;
        this.numberOfCodeSmells = 0;
        this.parameterCount = 0;
        this.duplication = 0;
    }

    public ComplexityMetrics(int cognitiveComplexity, int statementCount, int nestingDepth, int parameterCount, int numberOfCodeSmells, int duplication) {
        this.cognitiveComplexity = cognitiveComplexity;
        this.statementCount = statementCount;
        this.nestingDepth = nestingDepth;
        this.parameterCount = parameterCount;
        this.numberOfCodeSmells =numberOfCodeSmells;
        this.duplication = duplication;
    }
    public int getDuplication(){return duplication;}
    public int getCognitiveComplexity() {
        return cognitiveComplexity;
    }

    public int getStatementCount() {
        return statementCount;
    }

    public int getNestingDepth() {
        return nestingDepth;
    }


    public int getParameterCount() {
        return parameterCount;
    }

    public int getNumCodeSmells() {
        return numberOfCodeSmells;
    }
}
