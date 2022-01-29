package sample.type.resolution;

/**
 * WARNING: Don't modify this class unless also modifying
 * edu.utdallas.seers.lasso.detector.BooleanPropertyMatcherTest
 * <p>
 * "P" and "N" marks are for automatically retrieving the test cases, positive and negative.
 */
@SuppressWarnings({"unused", "PointlessBooleanExpression", "ConstantConditions"})
public class BooleanProperty {
    private static boolean staticBoolean;

    boolean booleanField = true;
    Boolean boxedBoolean = true;

    public boolean returnBoolean() {
        return true;
    }

    public void acceptInt(int a) {

    }

    public void doSomething() {
        if (
                booleanField // P
        ) {
            System.out.println("Yes");
        }

        if (
                boxedBoolean // P
        ) {
            System.out.println("Also yes");
        }

        while (
                returnBoolean() // P
        ) {
            System.out.println("Yeah");
        }

        acceptInt(
                BooleanProperty2.NOT_BOOLEAN // N
        );

        int g = 0;

        acceptInt(
                g // N
        );
    }

    public int doSomething2() {
        boolean m =
                BooleanProperty2.OTHER_BOOLEAN // P
                        ||
                        true;

        if (
                m // P
        ) {
            System.out.println("AA");
        }

        return
                BooleanProperty2.NOT_BOOLEAN // N
                ;
    }

    public static void check(boolean really) {
        really |=
                !
                        staticBoolean // P
        ;

        System.out.println(really);

        boolean a =
                staticBoolean // P
                ;

        int b =
                BooleanProperty2.NOT_BOOLEAN // N
                ;
    }
}
