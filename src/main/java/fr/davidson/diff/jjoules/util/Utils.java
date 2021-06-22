package fr.davidson.diff.jjoules.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

public class Utils {

    public static String readFile(String pathToFileToRead) {
        final String nl = System.getProperty("line.separator");
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(pathToFileToRead)))) {
            reader.lines().forEach(
                    line -> builder.append(line).append(nl)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return builder.toString();
    }

    public static String toFullQualifiedName(String className, String methodName) {
        return className + "#" + methodName;
    }

    public static <T> void addToGivenMap(final String key, T value, Map<String, List<T>> givenMap) {
        if (!givenMap.containsKey(key)) {
            givenMap.put(key, new ArrayList<>());
        }
        givenMap.get(key).add(value);
    }

    public static <T> void addToGivenMap(final String key, List<T> values, Map<String, List<T>> givenMap) {
        if (!givenMap.containsKey(key)) {
            givenMap.put(key, new ArrayList<>());
        }
        givenMap.get(key).addAll(values);
    }

    public static <T> void addToGivenMapSet(final String key, List<T> values, Map<String, Set<T>> givenMap) {
        if (!givenMap.containsKey(key)) {
            givenMap.put(key, new HashSet<>());
        }
        givenMap.get(key).addAll(values);
    }

}
