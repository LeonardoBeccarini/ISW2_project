package org.example.model;

public class Metrics {
    private LOCMetrics stmtAdded;
    private  LOCMetrics churnMetrics;
    private  LOCMetrics stmtDeleted;
    private ComplexityMetrics complexityMetrics;

    private boolean bugged;
   private int authors;
    // In Metrics.java
    private int methodHistories;


    public Metrics(){
        authors = 0;
        methodHistories = 0;
        stmtAdded = new LOCMetrics();
        churnMetrics = new LOCMetrics();
        stmtDeleted = new LOCMetrics();
        complexityMetrics = new ComplexityMetrics(0,0, 0, 0, 0,0);
        bugged = false;

    }



    public int getMethodHistories() {
        return methodHistories;
    }

    public void setMethodHistories(int methodHistories) {
        this.methodHistories = methodHistories;
    }


    public void setBugged(boolean bugged) {
        this.bugged = bugged;
    }

    public void setAuthors(int authors) {
        this.authors = authors;
    }

    public void setStmtAdded(LOCMetrics stmtAdded) {
        this.stmtAdded = stmtAdded;
    }
    public void setChurnMetrics(LOCMetrics churnMetrics) {
        this.churnMetrics = churnMetrics;
    }
    public void setStmtDeleted(LOCMetrics stmtDeleted) {
        this.stmtDeleted = stmtDeleted;
    }

    public void setComplexityMetrics(ComplexityMetrics complexityMetrics) {
        this.complexityMetrics = complexityMetrics;
    }

    public LOCMetrics getStmtAdded() {
        return stmtAdded;
    }

    public LOCMetrics getChurnMetrics() {
        return churnMetrics;
    }

    public LOCMetrics getStmtDeleted() {
        return stmtDeleted;
    }

    public ComplexityMetrics getComplexityMetrics() {
        return complexityMetrics;
    }

    public boolean getBugged() {
        return bugged;
    }

    public String isBugged(){
        if(bugged){
            return "true";
        }
        return "false";
    }

    public int getAuthors() {
        return authors;
    }


}
