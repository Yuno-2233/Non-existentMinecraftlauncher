package com.github.yuno2233.neml.log;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

public class NemlLogger {
    private static final Path LOG_DIR = Paths.get("neml", "logs");
    private static final int MAX_LOG_FILES = 7;
    private static boolean initialized = false;

    public static void init(Level level) {
        if (initialized) return;
        initialized = true;
        try {
            Files.createDirectories(LOG_DIR);
            cleanOldLogs();
        } catch (IOException e) {
            System.err.println("[NEML] 无法创建日志目录: " + e.getMessage());
        }

        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(level);
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(level);
        consoleHandler.setFormatter(new SimpleFormatter() {
            @Override
            public String format(LogRecord r) {
                String prefix = "[NEML]";
                if (r.getLoggerName().startsWith("mod.")) {
                    prefix = "[" + r.getLoggerName().substring(4) + "]";
                }
                return prefix + " " + formatMessage(r) + "\n";
            }
        });
        rootLogger.addHandler(consoleHandler);

        try {
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Path logFile = LOG_DIR.resolve("neml_" + date + ".log");
            FileHandler fileHandler = new FileHandler(logFile.toString(), true);
            fileHandler.setLevel(level);
            fileHandler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord r) {
                    String prefix = "[NEML]";
                    if (r.getLoggerName().startsWith("mod.")) {
                        prefix = "[" + r.getLoggerName().substring(4) + "]";
                    }
                    return String.format("%s %s: %s%n", r.getInstant(), r.getLevel(), prefix + " " + formatMessage(r));
                }
            });
            rootLogger.addHandler(fileHandler);
        } catch (IOException e) {
            System.err.println("[NEML] 无法创建文件日志: " + e.getMessage());
        }
    }

    public static Logger getEngineLogger() {
        return Logger.getLogger("NEML");
    }

    public static Logger getModLogger(String modId) {
        return Logger.getLogger("mod." + modId);
    }

    private static void cleanOldLogs() {
        try {
            Files.list(LOG_DIR)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("neml_"))
                    .sorted((a, b) -> -a.compareTo(b))
                    .skip(MAX_LOG_FILES)
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }
}
