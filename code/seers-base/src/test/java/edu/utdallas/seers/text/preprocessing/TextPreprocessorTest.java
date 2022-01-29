package edu.utdallas.seers.text.preprocessing;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.stream.Collectors;

import static edu.utdallas.seers.testing.TestUtils.a;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public class TextPreprocessorTest {

    private TextPreprocessor textPreprocessor;
    private final String[] emptyInput = new String[]{};

    @Before
    public void setUp() {
        textPreprocessor = new TextPreprocessor.Builder().build();
    }

    @Test
    @Parameters
    public void testTokenize(String string, String... expected) {
        assertThat(textPreprocessor.tokenize(string))
                .as("Tokenize: " + string)
                .containsExactlyInAnyOrder(expected);
    }

    public Object[] parametersForTestTokenize() {
        return a(
                a("", emptyInput),
                a("word", a("word")),
                a("word-word don't words'", a("word", "word", "don't", "words")),
                a("CamelCase snake_case echo123", a("CamelCase", "snake_case", "echo123")),
                a("word\\\\word2++word3", a("word", "word2", "word3"))
        );
    }

    @Test
    @Parameters
    public void testPreprocess(String string, boolean stem, String... expected) {
        String tag = stem ? "stemming" : "no stemming";
        assertThat(textPreprocessor.preprocess(string, stem).collect(Collectors.toList()))
                .as(String.format("Preprocess, %s: %s", tag, string))
                .containsExactlyInAnyOrder(expected);
    }

    public Object[] parametersForTestPreprocess() {
        return a(
                a("", true, emptyInput),
                a("", false, emptyInput),
                a("compoundIdentifier", false,
                        a("compoundidentifier", "compound", "identifier")),
                a("someIdentifier", false, a("someidentifier", "identifier")),
                a("truths greatValues", true,
                        a("truth", "greatvalu", "great", "valu"))
        );
    }
}