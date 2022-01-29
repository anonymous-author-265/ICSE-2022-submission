package edu.utdallas.seers.text.preprocessing;

import opennlp.tools.stemmer.PorterStemmer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TextPreprocessor {
    private final Pattern tokenizer;
    private final List<String> filterList;
    private final PorterStemmer stemmer;
    private final IdentifierSplitter splitter;
    private final int minimumLength;

    private TextPreprocessor(Pattern tokenizer, List<String> filterList, PorterStemmer stemmer,
                             IdentifierSplitter splitter, int minimumLength) {
        this.tokenizer = tokenizer;
        this.filterList = filterList;
        this.stemmer = stemmer;
        this.splitter = splitter;
        this.minimumLength = minimumLength;
    }

    public static TextPreprocessor withStandardStopWords() {
        return new Builder().setFilterList(Preprocessing.loadStandardStopWords()).build();
    }

    public static TextPreprocessor withStandardStopWordsAndJavaKeywords() {
        var builder = new Builder();
        var stopWords = Stream.concat(
                builder.filterList.stream(),
                Preprocessing.loadJavaKeywords().stream()
        ).collect(Collectors.toList());

        return builder.setFilterList(stopWords).build();
    }

    public Stream<String> tokenize(String string) {
        return tokenizer.matcher(string)
                .results()
                .map(MatchResult::group);
    }

    /**
     * Filters stop words and short words.
     *
     * @param words The words. Must be in lower case.
     * @return Filtered words.
     */
    public Stream<String> filterWords(Stream<String> words) {
        return words
                .filter(w -> w.length() >= minimumLength && !filterList.contains(w));
    }

    /**
     * Tokenizes, splits identifiers, filters words, and optionally stems.
     *
     * @param string String for preprocessing
     * @param stem   Whether or not to stem.
     * @return Tokens
     * @see TextPreprocessor#preprocess(Stream, boolean)
     * @see TextPreprocessor#tokenize(String)
     */
    public Stream<String> preprocess(String string, boolean stem) {
        Stream<String> tokens = tokenize(string);

        return preprocess(tokens, stem);
    }

    /**
     * Splits identifiers, filters words, and optionally stems.
     *
     * @param tokens Text tokens.
     * @param stem   Whether or not to stem.
     * @return Preprocessed tokens.
     * @see TextPreprocessor#filterWords(Stream)
     */
    public Stream<String> preprocess(Stream<String> tokens, boolean stem) {
        Stream<String> split = tokens
                .flatMap(w -> {
                    var originalToken = Stream.of(w.toLowerCase());

                    // Only return the components if any split happened
                    var splitTerms = Optional.of(splitter.splitIdentifier(w))
                            .filter(ss -> ss.size() > 1)
                            .stream()
                            .flatMap(Collection::stream);

                    // Include original token along with split components (if any)
                    return Stream.concat(originalToken, splitTerms);
                });

        Stream<String> filtered = filterWords(split);

        if (stem) {
            return filtered
                    .map(stemmer::stem);
        } else {
            return filtered;
        }
    }

    @SuppressWarnings({"unused", "RedundantSuppression"})
    public static class Builder {
        private Pattern tokenizer = Pattern.compile("[\\w]+(?:'\\w)?");
        private List<String> filterList = Collections.emptyList();
        private PorterStemmer stemmer = new PorterStemmer();
        private IdentifierSplitter splitter = new IdentifierSplitter();
        private int minimumLength = 3;

        public TextPreprocessor build() {
            return new TextPreprocessor(tokenizer, filterList, stemmer, splitter, minimumLength);
        }

        public Builder setTokenizer(Pattern tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public Builder setFilterList(List<String> filterList) {
            this.filterList = filterList;
            return this;
        }

        public Builder setStemmer(PorterStemmer stemmer) {
            this.stemmer = stemmer;
            return this;
        }

        public Builder setSplitter(IdentifierSplitter splitter) {
            this.splitter = splitter;
            return this;
        }

        public Builder setMinimumLength(int minimumLength) {
            this.minimumLength = minimumLength;
            return this;
        }
    }
}
