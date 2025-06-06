package org.example.model;

import com.github.javaparser.quality.NotNull;
import org.example.enums.CsvEnum;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

public class DatasetFile {
    private final String projName;
    private final CsvEnum csvName;
    private final int iterationIndex;

    private final int versionIndex;
    private final List<MethodIdentifier> methodIdentifierList;

    public DatasetFile(String projName, CsvEnum csvName, int iterationIndex, int versionIndex, List<MethodIdentifier> methodIdentifierList){
        this.projName = projName;
        this.csvName = csvName;
        this.iterationIndex = iterationIndex;
        this.versionIndex = versionIndex;
        this.methodIdentifierList = methodIdentifierList;

    }

    private String enumToFilename() {

        return switch (csvName) {
            case TRAINING -> "_TR" + versionIndex;
            case TESTING -> "_TE" + versionIndex;
            default -> null;
        };

    }
    private String enumToWFDirectoryName() {

        return this.projName+"_WF_"+this.iterationIndex;

    }

    private File createANewFile(String projName, String endPath) throws IOException {

        String dirPath = "./retrieved_data/datasets/"+this.projName+ File.separator  + enumToWFDirectoryName()+ File.separator;

        String pathname = dirPath + projName + enumToFilename() + endPath;

        File dir = new File(dirPath);
        File file = new File(pathname);

        if(!dir.exists() && !file.mkdirs()) {
            throw new IOException(); //Exception: dir creation impossible
        }

        if(file.exists() && !file.delete()) {
            throw new IOException(); //Exception: file deletion impossible
        }

        return file;
    }

    /** write all the ClassInfo data on a CSV file*/
    public void writeOnCsv() throws IOException {

        File file = createANewFile(projName, ".csv");

        try(FileWriter fw = new FileWriter(file)) {

            fw.write("METHOD," +
                    "VERSION," +
                    "STMT_ADDED," +
                    "MAX_STMT_ADDED," +
                    "AVG_STMT_ADDED," +
                    "STMT_DELETED," +
                    "MAX_STMT_DELETED," +
                    "AVG_STMT_DELETED," +
                    "CHURN," +
                    "MAX_CHURN," +
                    "AVG_CHURN," +
                    "COGNITIVE_COMPLEXITY," +
                    "STMT_COUNT," +
                    "NESTING_DEPTH," +
                    "PARAMETER_COUNT," +
                    "NUM_CODE_SMELLS," +
                    "NUM_AUTHORS," +
                    "DUPLICATION," +
                    "METHOD_HISTORIES,"+
                    "BUGGYNESS\n");



            writeDataOnFile( fw, false);
        }
    }

    /**Write for all the ClassInfo elements the data into a ARFF file */
    public void writeOnArff() throws IOException {

        String fileNameStr = enumToFilename();

        writeOnCsv();
        File file = createANewFile(projName, ".arff");
        try (FileWriter wr = new FileWriter(file)) {


            wr.write("@relation " + this.projName + fileNameStr + "\n");
            wr.write("@attribute STMT_ADDED numeric\n");
            wr.write("@attribute MAX_STMT_ADDED numeric\n");
            wr.write("@attribute AVG_STMT_ADDED numeric\n");
            wr.write("@attribute STMT_DELETED numeric\n");
            wr.write("@attribute MAX_STMT_DELETED numeric\n");
            wr.write("@attribute AVG_STMT_DELETED numeric\n");
            wr.write("@attribute CHURN numeric\n");
            wr.write("@attribute MAX_CHURN numeric\n");
            wr.write("@attribute AVG_CHURN numeric\n");
            wr.write("@attribute COGNITIVE_COMPLEXITY numeric\n");
            wr.write("@attribute STMT_COUNT numeric\n");
            wr.write("@attribute NESTING_DEPTH numeric\n");
            wr.write("@attribute PARAMETER_COUNT numeric\n");
            wr.write("@attribute NUM_CODE_SMELLS numeric\n");
            wr.write("@attribute NUM_AUTHORS numeric\n");
            wr.write("@attribute DUPLICATION numeric\n");
            wr.write("@attribute METHOD_HISTORIES numeric\n");
            wr.write("@attribute BUGGYNESS {'true', 'false'}\n");
            wr.write("@data\n");

            writeDataOnFile(wr,true);

        }

    }

    private void writeDataOnFile(FileWriter fw, boolean isArff) throws IOException {

        for (MethodIdentifier method : this.methodIdentifierList) {

            if (!isArff) {
                fw.write(method.getMethodName() + ","); //JAVA_CLASS
                fw.write(method.getVersion().getName() + ","); //VERSION
            }
            Metrics metrics = method.getMetricsList();
            ComplexityMetrics complexityMetrics = metrics.getComplexityMetrics();

            fw.write(metrics.getStmtAdded().getVal() + ",");
            fw.write(metrics.getStmtAdded().getMaxVal() + ",");
            fw.write(metrics.getStmtAdded().getAvgVal() + ",");
            fw.write(metrics.getStmtDeleted().getVal() + ",");
            fw.write(metrics.getStmtDeleted().getMaxVal() + ",");
            fw.write(metrics.getStmtDeleted().getAvgVal() + ",");
            fw.write(metrics.getChurnMetrics().getVal() + ",");
            fw.write(metrics.getChurnMetrics().getMaxVal() + ",");
            fw.write(metrics.getChurnMetrics().getAvgVal() + ",");

            fw.write(complexityMetrics.getCognitiveComplexity() + ",");
            fw.write(complexityMetrics.getStatementCount() + ",");
            fw.write(complexityMetrics.getNestingDepth() + ",");
            fw.write(complexityMetrics.getParameterCount() + ",");
            fw.write(complexityMetrics.getNumCodeSmells() + ",");
            fw.write(metrics.getAuthors()+ ",");
            fw.write(complexityMetrics.getDuplication() + ",");
            fw.write(metrics.getMethodHistories() + ",");

            fw.write(metrics.isBugged());


            fw.write("\n");



        }
    }
}
