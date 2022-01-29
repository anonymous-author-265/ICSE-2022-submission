package edu.utdallas.seers.testing;

public class TestUtils {
    /**
     * Wrap in array for easier use of JUnitParams. Note that using this method with no parameters
     * will cause errors in the library. Instead create an empty array in test class (not setup
     * method!).
     *
     * <code>
     * // For a test method with signature (String, int, boolean, String...)
     * return a("First param", 2, true, a("more", "params"));
     * </code>
     *
     * @param objects objects
     * @return Array
     */
    @SafeVarargs
    public static <T> T[] a(T... objects) {
        if (objects.length == 0) {
            throw new IllegalArgumentException("Should not be used with no parameters");
        }

        return objects;
    }
}
