package sample.type.resolution;

@SuppressWarnings("unused")
public class ConstantExtractor {

    public static final int INT_CONST =
            0 // P
            ;

    public static final Object OBJECT_CONST =
            new Object() // N
            ;

    private void action() {
        int local =
                INT_CONST // P
                ;

        Object local2 =
                OBJECT_CONST // P
                ;

        Object nonFinalObject =
                AuxConstants.NON_FINAL_OBJECT // N
                ;

        Object finalObject =
                AuxConstants.FINAL_OBJECT // P
                ;

        boolean constantPrimitive =
                AuxConstants.CONSTANT_PRIMITIVE // P
                ;

        Boolean constantBoxedLiteral =
                AuxConstants.CONSTANT_BOXED_LITERAL // P
                ;

        Integer constantBoxed =
                AuxConstants.CONSTANT_BOXED // P
                ;
    }
}
