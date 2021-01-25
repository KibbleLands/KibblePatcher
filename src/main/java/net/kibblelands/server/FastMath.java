package net.kibblelands.server;

/**
 * This class is edited automatically by the patcher
 * to use the Minecraft implementation of sin and cos
 */
public final class FastMath {
    private FastMath() {}

    public static double sin(double value) {
        return Math.sin(value);
    }

    public static float sin(float value) {
        return (float) Math.sin(value);
    }

    public static double cos(double value) {
        return Math.cos(value);
    }

    public static float cos(float value) {
        return (float) Math.cos(value);
    }

    public static double tan(double value) {
        return sin(value) / cos(value);
    }

    public static float tan(float value) {
        return sin(value) / cos(value);
    }
}
