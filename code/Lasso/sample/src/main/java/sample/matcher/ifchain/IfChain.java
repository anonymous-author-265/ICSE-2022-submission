package sample.matcher.ifchain;

@SuppressWarnings("unused")
public class IfChain {
    public void test(int a) {
        // P
        if (a < 0) {
            System.out.println("a");
        } // N
        else if (a == 0) {
            System.out.println("b");
        } else if (a > 10) {
            System.out.println("c");
        } else {
            System.out.println("d");
        }

        // N
        if (a == Integer.MAX_VALUE) {
            System.out.println("v");
        } else throw new IllegalArgumentException();
    }
}