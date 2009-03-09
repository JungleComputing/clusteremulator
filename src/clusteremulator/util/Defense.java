package clusteremulator.util;

public class Defense {

    public static void checkNotNull(Object param, String name) {
        if (param == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }

    public static void checkNotNegative(int param, String name) {
        if (param < 0) {
            throw new IllegalArgumentException(name + " cannot be negative (" + 
                    param + ")");
        }
    }

    public static void checkNotNegative(double param, String name) {
        if (param < 0.0) {
            throw new IllegalArgumentException(name + " cannot be negative (" + 
                    param + ")");
        }
    }

}
