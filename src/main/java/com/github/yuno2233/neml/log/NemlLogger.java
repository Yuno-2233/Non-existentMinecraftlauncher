package com.github.yuno2233.neml.log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.*;

public class NemlLogger {
    private static final Path CONFIG_FILE = Paths.get("neml", "config", "logging.json");
    private static final String DEFAULT_CONFIG = "{\n" +
        "  \"level\": \"INFO\",\n" +
        "  \"console\": {\n" +
        "    \"color\": true,\n" +
        "    \"pattern\": \"[%d{HH:mm:ss}] [%level] [%thread] [%name] %msg%n\"\n" +
        "  },\n" +
        "  \"file\": {\n" +
        "    \"path\": \"neml/logs/neml.log\",\n" +
        "    \"maxFileSize\": \"5MB\",\n" +
        "    \"maxHistory\": 30,\n" +
        "    \"pattern\": \"%d{yyyy-MM-dd HH:mm:ss} [%level] [%thread] [%name] %msg%n\"\n" +
        "  },\n" +
        "  \"modLevels\": {}\n" +
        "}";

    private static boolean initialized = false;
    private static Map<String, Level> modLevels = new HashMap<>();
    private static Level globalLevel = Level.INFO;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        JsonObject config = loadConfig();
        applyConfig(config);
    }

    public static synchronized void reload() {
        if (!initialized) {
            init();
            return;
        }
        Logger rootLogger = Logger.getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }
        JsonObject config = loadConfig();
        applyConfig(config);
    }

    private static JsonObject loadConfig() {
        try {
            if (!Files.exists(CONFIG_FILE)) {
                Files.createDirectories(CONFIG_FILE.getParent());
                Files.writeString(CONFIG_FILE, DEFAULT_CONFIG);
            }
            String content = Files.readString(CONFIG_FILE);
            return new Gson().fromJson(content, JsonObject.class);
        } catch (Exception e) {
            System.err.println("[NEML] 无法加载日志配置，使用默认: " + e.getMessage());
            return new Gson().fromJson(DEFAULT_CONFIG, JsonObject.class);
        }
    }

    private static void applyConfig(JsonObject config) {
        // 全局级别
        String envLevel = System.getenv("NEML_LOG_LEVEL");
        if (envLevel != null) {
            globalLevel = parseLevel(envLevel);
        } else if (config.has("level")) {
            globalLevel = parseLevel(config.get("level").getAsString());
        }

        // Mod 独立级别
        modLevels.clear();
        if (config.has("modLevels")) {
            JsonObject modObj = config.getAsJsonObject("modLevels");
            for (Map.Entry<String, JsonElement> entry : modObj.entrySet()) {
                modLevels.put(entry.getKey(), parseLevel(entry.getValue().getAsString()));
            }
        }

        // 控制台处理器
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(globalLevel);

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(globalLevel);
        boolean color = config.has("console") && 
                        config.getAsJsonObject("console").has("color") && 
                        config.getAsJsonObject("console").get("color").getAsBoolean();
        String consolePattern = config.has("console") && 
                                config.getAsJsonObject("console").has("pattern") ?
                                config.getAsJsonObject("console").get("pattern").getAsString() :
                                "[%d{HH:mm:ss}] [%level] [%thread] [%name] %msg%n";
        consoleHandler.setFormatter(new NemlFormatter(consolePattern, color, true));
        rootLogger.addHandler(consoleHandler);

        // 文件处理器
        try {
            String filePattern = "neml/logs/neml.log";
            int maxFileSize = 5 * 1024 * 1024;
            int maxHistory = 30;
            String fileLogPattern = "%d{yyyy-MM-dd HH:mm:ss} [%level] [%thread] [%name] %msg%n";
            if (config.has("file")) {
                JsonObject fileConf = config.getAsJsonObject("file");
                if (fileConf.has("path")) filePattern = fileConf.get("path").getAsString();
                if (fileConf.has("maxFileSize")) maxFileSize = parseSize(fileConf.get("maxFileSize").getAsString());
                if (fileConf.has("maxHistory")) maxHistory = fileConf.get("maxHistory").getAsInt();
                if (fileConf.has("pattern")) fileLogPattern = fileConf.get("pattern").getAsString();
            }
            Path logDir = Paths.get(filePattern).getParent();
            if (logDir != null) Files.createDirectories(logDir);
            FileHandler fileHandler = new FileHandler(filePattern, maxFileSize, maxHistory, true);
            fileHandler.setLevel(globalLevel);
            fileHandler.setFormatter(new NemlFormatter(fileLogPattern, false, false));
            rootLogger.addHandler(fileHandler);
        } catch (IOException e) {
            System.err.println("[NEML] 无法创建文件日志处理器: " + e.getMessage());
        }
    }

    public static Logger getEngineLogger() {
        return Logger.getLogger("NEML");
    }

    public static Logger getModLogger(String modId) {
        Logger logger = Logger.getLogger("mod." + modId);
        if (modLevels.containsKey(modId)) {
            logger.setLevel(modLevels.get(modId));
        } else {
            logger.setLevel(globalLevel);
        }
        return logger;
    }

    private static Level parseLevel(String levelStr) {
        switch (levelStr.toUpperCase()) {
            case "TRACE": return Level.FINEST;
            case "DEBUG": return Level.FINE;
            case "INFO":  return Level.INFO;
            case "WARN":  return Level.WARNING;
            case "ERROR": return Level.SEVERE;
            case "OFF":   return Level.OFF;
            default:
                try { return Level.parse(levelStr.toUpperCase()); } catch (Exception e) { return Level.INFO; }
        }
    }

    private static int parseSize(String sizeStr) {
        sizeStr = sizeStr.toUpperCase().trim();
        long multiplier = 1;
        if (sizeStr.endsWith("KB")) multiplier = 1024;
        else if (sizeStr.endsWith("MB")) multiplier = 1024 * 1024;
        else if (sizeStr.endsWith("GB")) multiplier = 1024 * 1024 * 1024;
        String numStr = sizeStr.replaceAll("[^0-9]", "");
        try {
            return (int) (Long.parseLong(numStr) * multiplier);
        } catch (NumberFormatException e) {
            return 5 * 1024 * 1024;
        }
    }

    // 自定义格式化器
    private static class NemlFormatter extends java.util.logging.Formatter {
        private final String pattern;
        private final boolean color;
        private final boolean isConsole;
        private final SimpleDateFormat dateFormat;

        NemlFormatter(String pattern, boolean color, boolean isConsole) {
            this.pattern = pattern;
            this.color = color;
            this.isConsole = isConsole;
            // 提取日期格式
            String datePattern = "HH:mm:ss";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("%d\\{([^}]+)\\}").matcher(pattern);
            if (m.find()) {
                datePattern = m.group(1);
            }
            this.dateFormat = new SimpleDateFormat(datePattern);
        }

        @Override
        public String format(LogRecord record) {
            String message = formatMessage(record);
            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(sw));
                message += System.lineSeparator() + sw.toString();
            }
            String levelStr = record.getLevel().getLocalizedName();
            String threadName = Thread.currentThread().getName();
            String loggerName = record.getLoggerName();
            String prefix = loggerName.equals("NEML") ? "NEML" : 
                            loggerName.startsWith("mod.") ? loggerName.substring(4) : loggerName;

            String output = pattern
                    .replaceAll("%d\\{[^}]+\\}", dateFormat.format(new Date(record.getMillis())))
                    .replace("%level", levelStr)
                    .replace("%thread", threadName)
                    .replace("%name", prefix)
                    .replace("%msg", message)
                    .replace("%n", System.lineSeparator());

            if (isConsole && color) {
                output = applyColor(record.getLevel(), output);
            }
            return output;
        }

        private String applyColor(Level level, String text) {
            String colorCode;
            if (level.intValue() >= Level.SEVERE.intValue()) colorCode = "\u001b[31m"; // 红
            else if (level.intValue() >= Level.WARNING.intValue()) colorCode = "\u001b[33m"; // 黄
            else if (level.intValue() >= Level.INFO.intValue()) colorCode = "\u001b[36m"; // 青
            else if (level.intValue() >= Level.FINE.intValue()) colorCode = "\u001b[32m"; // 绿
            else colorCode = "\u001b[37m"; // 白
            return colorCode + text + "\u001b[0m";
        }
    }
}