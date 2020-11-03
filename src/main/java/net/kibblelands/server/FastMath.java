package net.kibblelands.server;

public class FastMath {
    private FastMath() {}

    public static double sin(double value) {
        return StrictMath.sin(value);
    }

    public static float sin(float value) {
        return (float) StrictMath.sin(value);
    }

    public static double cos(double value) {
        return StrictMath.cos(value);
    }

    public static float cos(float value) {
        return (float) StrictMath.cos(value);
    }
}
