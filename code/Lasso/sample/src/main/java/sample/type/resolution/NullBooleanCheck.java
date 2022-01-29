package sample.type.resolution;

import java.util.Collections;
import java.util.List;

/**
 * WARNING: Don't modify this class unless also modifying
 * edu.utdallas.seers.lasso.ast.matcher.NullBooleanCheckMatcherTest
 * <p>
 * "P" and "N" marks are for automatically retrieving the test cases, positive and negative.
 */
@SuppressWarnings({"unused", "ConstantConditions"})
public class NullBooleanCheck {
    boolean me = false;
    List<?> things = Collections.emptyList();

    public List<?> getThings() {
        return things;
    }

    public boolean isMe() {
        return me;
    }

    void test(List<?> list) {
        // To test the standard library type resolution
        boolean test1 =
                list == null || list.isEmpty() // P
                ;
        boolean test2 =
                list != null && !list.isEmpty() // P
                ;
    }

    void doSomething(NullBooleanCheck a) {
        boolean test1 =
                a == null || a.me // P
        ;

        boolean test2 =
                a == null || a.isMe() // P
        ;

        Boolean b = false;
        boolean test3 =
                b != null && b // P
        ;

        boolean test4 =
                a.getThings() != null && !a.getThings().isEmpty() // P
                ;
    }
}
