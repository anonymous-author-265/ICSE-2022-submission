package edu.utdallas.seers.lasso.ast.matcher;

import java.nio.file.Path;
import java.nio.file.Paths;

public class IfChainMatcherTest extends PatternMatcherTest {

    private final Path TEST_FILE = Paths.get("sample/src/main/java/sample/matcher/ifchain/IfChain.java");
    private final PatternMatcher matcher = new IfChainMatcher();

    @Override
    protected Object[] parametersForTestPositive() {
        return new TestCaseParser().parseTestCases(TEST_FILE, "P").toArray();
    }

    @Override
    protected Object[] parametersForTestNegative() {
        return new TestCaseParser().parseTestCases(TEST_FILE, "N").toArray();
    }

    @Override
    protected PatternMatcher getMatcher() {
        return matcher;
    }
}