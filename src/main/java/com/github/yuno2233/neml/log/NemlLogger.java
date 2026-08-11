package com.github.yuno2233.neml.log;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * NEML 统一日志系统。
 * 控制台输出：[前缀] 消息
 * 文件输出：  yyyy-MM-dd HH:mm:ss LEVEL [前缀] 消息
 * 级别由环境变量 NEML_LOG_LEVEL 控制，默认 INFO。
 */
public class NemlLogger {
    private static final Path LOG_DIR = Paths.get("neml", "logs");
    private static final int MAX_LOG_FILES = 7;
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // 解析环境变量中的日志级别
        Level level = parseLevel(System.getenv("NEML_LOG_LEVEL"));

        // 创建日志目录
        try {
            Files.createDirectories(LOG_DIR);
            cleanOldLogs();
        } catch (IOException e) {
            System.err.println("[NEML] 无法创建日志目录: " + e.getMessage());
        }

        // 重置根日志器
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(level);
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        // 控制台处理器
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(level);
        consoleHandler.setFormatter(new NemlFormatter(false));
        rootLogger.addHandler(consoleHandler);

        // 文件处理器
        try {
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            FileHandler fileHandler = new FileHandler(
                    LOG_DIR.resolve("neml_" + date + ".log").toString(), true);
            fileHandler.setLevel(level);
            fileHandler.setFormatter(new NemlFormatter(true));
            rootLogger.addHandler(fileHandler);
        } catch (IOException e) {
            System.err.println("[NEML] 无法创建文件日志处理器: " + e.getMessage());
        }
    }

    /**
     * 获取引擎专用日志器（前缀 [NEML]）
     */
    public static Logger getEngineLogger() {
        return Logger.getLogger("NEML");
    }

    /**
     * 获取 Mod 专用日志器（前缀 [modId]）
     */
    public static Logger getModLogger(String modId) {
        return Logger.getLogger("mod." + modId);
    }

    // 解析日志级别
    private static Level parseLevel(String levelStr) {
        if (levelStr == null) return Level.INFO;
        switch (levelStr.toUpperCase()) {
            case "TRACE": return Level.FINEST;
            case "DEBUG": return Level.FINE;
            case "INFO":  return Level.INFO;
            case "WARN":  return Level.WARNING;
            case "ERROR": return Level.SEVERE;
            default:
                try {
                    return Level.parse(levelStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    System.err.println("[NEML] 未知日志级别: " + levelStr + "，使用默认 INFO");
                    return Level.INFO;
                }
        }
    }

    // 清理旧日志
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

    /**
     * 自定义格式化器
     */
    private static class NemlFormatter extends Formatter {
        private final boolean forFile;
        private static final DateTimeFormatter TIME_FORMATTER =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        NemlFormatter(boolean forFile) {
            this.forFile = forFile;
        }

        @Override
        public String format(LogRecord record) {
            // 确定前缀
            String loggerName = record.getLoggerName();
            String prefix;
            if ("NEML".equals(loggerName)) {
                prefix = "[NEML]";
            } else if (loggerName.startsWith("mod.")) {
                prefix = "[" + loggerName.substring(4) + "]";
            } else {
                prefix = "[" + loggerName + "]";
            }

            String message = formatMessage(record);
            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(sw));
                message += "\n" + sw.toString();
            }

            if (forFile) {
                // 使用 LocalDateTime 而不是 LocalTime
                return String.format("%s %s %s %s%n",
                        LocalDateTime.now().format(TIME_FORMATTER),
                        record.getLevel().getLocalizedName(),
                        prefix,
                        message);
            } else {
                return String.format("%s %s%n", prefix, message);
            }
        }
    }
}