package org.example.utils;

import org.example.model.MethodIdentifier;

import java.util.ArrayList;
import java.util.List;

public class MethodUtil {
    /** Return all the classes that contained in a specific version */
    public static List<MethodIdentifier> filterJavaClassesByVersion(List<MethodIdentifier> methodIdentifierList, int versionID) {

        List<MethodIdentifier> methodList = new ArrayList<>();

        for (MethodIdentifier methodIdentifier : methodIdentifierList) {
            if (methodIdentifier.getVersion().getIndex() == versionID) {
                methodList.add(methodIdentifier);

            }

        }
        return methodList;
    }
}
