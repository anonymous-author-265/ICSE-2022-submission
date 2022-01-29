package edu.utdallas.seers.text.preprocessing;

import edu.utdallas.seers.file.Files;

import java.util.List;

public class Preprocessing {
    /**
     * Obtained from Lemur project http://www.lemurproject.org/
     *
     * @return List of stop words.
     */
    public static List<String> loadStandardStopWords() {
        return Files.loadResourceLines("edu/utdallas/seers/text/stopwords.txt");
    }

    public static List<String> loadJavaKeywords() {
        return Files.loadResourceLines("edu/utdallas/seers/text/java_keywords.txt");
    }
}
