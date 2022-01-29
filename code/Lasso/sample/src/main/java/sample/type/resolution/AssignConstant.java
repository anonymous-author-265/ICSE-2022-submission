package sample.type.resolution;

import javax.swing.*;

@SuppressWarnings("ALL")
public class AssignConstant {
    int
            something = 1 // P
            ;

    int
            a = (((-90))) // P
            ;

    int
    hh = 1 + 2 // N
    ;

    int
    m =
            a = 56 // P
    ;

    int gh =
            hh = a // N
    ;

    int
    aa = 10 // P
    ,
    bb = 20 // P
    ;

    @SuppressWarnings({"ConstantConditions", "UnnecessaryReturnStatement"})
    void doSomething(int b) {
        b = 0 // P
        ;

        String
                d = "java" // P
                ;

        int
                h = b // N
                ;

        if ((
                b = 1 // P
        ) == 1)
            return;
    }

    private void addConfigCheckboxes(JPanel panel) {
        boolean
                desc = true // P
                ;
        boolean
                chan = true // P
                ;
    }
}
