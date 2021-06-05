package net.kibblelands.patcher.utils.logger;

import net.kibblelands.patcher.utils.ConsoleColors;
import org.fusesource.jansi.AnsiConsole;

import java.io.PrintStream;
import java.util.Locale;

public class Logger {
    private static final String prefixUTF = "▶ ";
    private static final String prefixASCII = "> ";
    private static final String prefix;

    static {
        Locale.setDefault(Locale.ENGLISH);
        boolean supportUnicode = true;
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                AnsiConsole.systemInstall();
                // Check windows UTF-8 support or if git/cygwin terminal
                supportUnicode =
                        "cygwin".equals(System.getenv("TERM"))
                        || "1".equals(System.getenv("WINUTF8"));
                if (supportUnicode) {
                    System.setOut(new PrintStream(System.out, true, "UTF-8"));
                }
            } else {
                System.setOut(new PrintStream(System.out, true, "UTF-8"));
            }
        } catch (Exception ignored) {
            supportUnicode = false;
        }
        prefix = supportUnicode ? prefixUTF : prefixASCII;
    }

    public static String getPrefix() {
        return prefix;
    }

    private final String name;

    public Logger(String name) {
        this.name = name;
    }

    private void log(LogLevel level, String message) {
        switch (level) {
            case INFO:
                System.out.println(ConsoleColors.PURPLE + prefix + ConsoleColors.GREEN + message + ConsoleColors.RESET);
                break;
            case WARN:
                System.out.println(ConsoleColors.PURPLE + prefix + ConsoleColors.YELLOW + message + ConsoleColors.RESET);
                break;
            case DEBUG:
                System.out.println(ConsoleColors.PURPLE + prefix + ConsoleColors.PURPLE + message + ConsoleColors.RESET);
                break;
            case ERROR:
                System.out.println(ConsoleColors.PURPLE + prefix + ConsoleColors.RED + message + ConsoleColors.RESET);
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
