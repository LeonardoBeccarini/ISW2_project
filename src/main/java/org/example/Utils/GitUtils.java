package org.example.Utils;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;

public class GitUtils {
    public static LocalDate castToLocalDate(Date date){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return LocalDate.parse(sdf.format(date));
    }
}
