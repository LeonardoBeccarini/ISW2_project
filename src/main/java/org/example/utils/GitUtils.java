package org.example.utils;

import org.example.model.MethodIdentifier;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

public class GitUtils {
    public static LocalDate castToLocalDate(Date date){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return LocalDate.parse(sdf.format(date));
    }
    public static void initializateBuggyness(List<MethodIdentifier
                > methodList){
        for(MethodIdentifier method: methodList)
            method.getMetricsList().setBugged(false);
    }
}
