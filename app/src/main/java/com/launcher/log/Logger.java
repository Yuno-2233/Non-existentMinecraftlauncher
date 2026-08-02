package com.launcher.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {

    private final String tag;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public Logger(String tag) {
        this.tag = tag;
    }

    private void log(LogLevel level, String message) {
        String time = LocalDateTime.now().format(FORMATTER);
        String logMessage = String.format("[%s] [%-5s] [%s] %s", time, level, tag, message);

        switch (level) {
            case ERROR -> System.err.println(logMessage);
            default -> System.out.println(logMessage);
        }
    }

    public void info(String message) { log(LogLevel.INFO, message); }
    public void warn(String message) { log(LogLevel.WARN, message); }
    public void error(String message) { log(LogLevel.ERROR, message); }
    public void debug(String message) { log(LogLevel.DEBUG, message); }
}
