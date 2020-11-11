package net.kibblelands.patcher.utils.logger;

import net.kibblelands.patcher.utils.ConsoleColors;

public class Logger {

    private final String name;

    public Logger(String name) {
        this.name = name;
    }

    private void log(LogLevel level, String message) {
        switch (level) {
            case INFO:
                System.out.println(ConsoleColors.PURPLE + " ▶ " + ConsoleColors.GREEN + message + ConsoleColors.RESET);
                break;
            case WARN:
                System.out.println(ConsoleColors.PURPLE + " ▶ " + ConsoleColors.YELLOW + message + ConsoleColors.RESET);
                break;
            case DEBUG:
                System.out.println(ConsoleColors.PURPLE + " ▶ " + ConsoleColors.PURPLE + message + ConsoleColors.RESET);
                break;
            case ERROR:
                System.out.println(ConsoleColors.PURPLE + " ▶ " + ConsoleColors.RED + message + ConsoleColors.RESET);
                break;
            case STDOUT:
                System.out.println(ConsoleColors.RED + message + ConsoleColors.RESET);
                break;
        }
    }

    public void info(String message) {
        this.log(LogLevel.INFO, message);
    }

    public void warn(String message) {
        this.log(LogLevel.WARN, message);
    }

    public void debug(String message) {
        this.log(LogLevel.DEBUG, message);
    }

    public void error(String message) {
        this.log(LogLevel.ERROR, message);
    }

    public void stdout(String message) {
        this.log(LogLevel.STDOUT, message);
    }
}
