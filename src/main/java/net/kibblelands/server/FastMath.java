package net.kibblelands.server;

/**
 * This class is edited automatically by the patcher
 * to use the Minecraft implementation of sin and cos
 */
public final class FastMath {
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
