package edu.utdallas.seers.lasso.parser;

import com.github.javaparser.ast.CompilationUnit;
import edu.utdallas.seers.lasso.ast.JavaTextExtractor;
import edu.utdallas.seers.lasso.ast.TextSpan;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.javaparser.StaticJavaParser.parse;
import static edu.utdallas.seers.testing.TestUtils.a;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public class JavaTextExtractorTest {

    private final JavaTextExtractor tester = new JavaTextExtractor();

    @Parameters
    @Test
    public void testComments(CompilationUnit node, List<String> expected) {
        var actual = tester.extractText(node).map(TextSpan::getText).collect(Collectors.toList());
        assertThat(actual)
                .containsAll(expected);
    }

    public Object[] parametersForTestComments() {
        return a(
                a(parse("//C1\n//C2\nclass A{//C3\nvoid a()//C4\n{\n//C5\n}\n}\n//C6"),
                        Arrays.asList("C1", "C2", "C3", "C4", "C5", "C6"))
        );
    }
}