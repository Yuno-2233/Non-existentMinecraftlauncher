package io.github.yuno2233.neml.java;

import com.github.yuno2233.neml.api.CommandProvider;
import com.github.yuno2233.neml.log.NemlLogger;
import com.google.gson.*;

import java.io.*;
import java.nio.file.*;
import java.util.logging.Logger;

public class JavaCommandDispatcher implements CommandProvider {
    private static final Logger log = NemlLogger.getModLogger("java");
    private static final Path CONFIG_FILE = Paths.get("neml", "config", "java", "config.json");
    private static final Gson gson = new Gson();

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        switch (args[0]) {
            case "list":
                listJavaPaths();
                break;
            case "dir":
                if (args.length < 2) {
                    System.out.println("用法: neml java dir <add|list|switch> [参数]");
                    return;
                }
                switch (args[1]) {
                    case "add":
                        if (args.length < 3) {
                            System.out.println("用法: neml java dir add <路径>");
                        } else {
                            addDirectory(args[2]);
                        }
                        break;
                    case "list":
                        listDirectories();
                        break;
                    case "switch":
                        if (args.length < 3) {
                            System.out.println("用法: neml java dir switch <索引>");
                        } else {
                            switchDirectory(args[2]);
                        }
                        break;
                    default:
                        System.out.println("未知 dir 子命令: " + args[1]);
                }
                break;
            case "info":
                showInfo();
                break;
            default:
                System.out.println("未知命令: " + args[0]);
        }
    }

    private void printUsage() {
        System.out.println("用法: neml java <命令>");
        System.out.println("  list              列出所有 Java 路径及版本");
        System.out.println("  dir add <路径>     添加 Java 目录 (JDK/JRE 根目录)");
        System.out.println("  dir list           列出已添加的目录");
        System.out.println("  dir switch <索引>   切换当前使用的 Java 目录");
        System.out.println("  info               显示当前 Java 详细信息");
    }

    // ========== 配置管理 ==========
    private JsonObject loadConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            JsonObject def = new JsonObject();
            def.addProperty("currentJava", "");
            def.add("directories", new JsonArray());
            saveConfig(def);
            return def;
        }
        try {
            String content = Files.readString(CONFIG_FILE);
            return gson.fromJson(content, JsonObject.class);
        } catch (Exception e) {
            log.warning("读取配置失败，使用默认");
            return new JsonObject();
        }
    }

    private void saveConfig(JsonObject config) {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, gson.toJson(config));
        } catch (Exception e) {
            log.warning("保存配置失败: " + e.getMessage());
        }
    }

    public static Path getCurrentJavaPath() {
        Path configFile = Paths.get("neml", "config", "java", "config.json");
        if (!Files.exists(configFile)) return null;
        try {
            JsonObject config = new Gson().fromJson(Files.readString(configFile), JsonObject.class);
            String path = config.has("currentJava") ? config.get("currentJava").getAsString() : "";
            if (!path.isEmpty()) return Paths.get(path);
        } catch (Exception ignored) {}
        return null;
    }

    // ========== 命令实现 ==========
    private void listJavaPaths() {
        JsonObject config = loadConfig();
        JsonArray dirs = config.has("directories") ? config.getAsJsonArray("directories") : new JsonArray();
        String current = config.has("currentJava") ? config.get("currentJava").getAsString() : "";

        if (dirs.size() == 0) {
            System.out.println("没有已添加的 Java 目录，请使用 'neml java dir add <路径>' 添加。");
            return;
        }
        System.out.println("已添加的 Java 目录:");
        for (int i = 0; i < dirs.size(); i++) {
            String dir = dirs.get(i).getAsString();
            String version = getJavaVersion(Paths.get(dir));
            String mark = dir.equals(current) ? " * 当前" : "";
            System.out.printf("  [%d] %-40s %s%s\n", i, dir, version, mark);
        }
    }

    private void listDirectories() {
        // 与 listJavaPaths 类似，但只显示路径不显示版本（也可以合并）
        listJavaPaths();
    }

    private void addDirectory(String dirPath) {
        Path p = Paths.get(dirPath).toAbsolutePath().normalize();
        // 检查是否存在 java 可执行文件
        if (!Files.isExecutable(p.resolve("bin/java")) && !Files.isExecutable(p.resolve("bin/java.exe"))) {
            System.out.println("错误: 指定目录下未找到 bin/java（或 java.exe），请确保路径是 JDK/JRE 根目录。");
            return;
        }
        JsonObject config = loadConfig();
        JsonArray dirs = config.has("directories") ? config.getAsJsonArray("directories") : new JsonArray();
        for (JsonElement e : dirs) {
            if (e.getAsString().equals(p.toString())) {
                System.out.println("目录已存在: " + p);
                config.addProperty("currentJava", p.toString());
                saveConfig(config);
                System.out.println("已切换到该 Java。");
                return;
            }
        }
        dirs.add(p.toString());
        config.add("directories", dirs);
        config.addProperty("currentJava", p.toString());
        saveConfig(config);
        System.out.println("已添加并切换到 Java: " + p);
        String ver = getJavaVersion(p);
        System.out.println("检测到版本: " + ver);
    }

    private void switchDirectory(String indexStr) {
        JsonObject config = loadConfig();
        JsonArray dirs = config.has("directories") ? config.getAsJsonArray("directories") : new JsonArray();
        if (dirs.size() == 0) {
            System.out.println("没有可切换的 Java 目录，请先添加。");
            return;
        }
        int index = -1;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException ignored) {}
    
        if (index >= 0 && index < dirs.size()) {
            String path = dirs.get(index).getAsString();
            config.addProperty("currentJava", path);
            saveConfig(config);
            System.out.println("已切换到 Java: " + path);
            return;
        }

        // 尝试按路径匹配
        for (int i = 0; i < dirs.size(); i++) {
            if (dirs.get(i).getAsString().equals(indexStr)) {
                config.addProperty("currentJava", indexStr);
                saveConfig(config);
                System.out.println("已切换到 Java: " + indexStr);
                return;
            }
        }
        System.out.println("错误: 无效的索引或路径。");
    }

    private void showInfo() {
        JsonObject config = loadConfig();
        String current = config.has("currentJava") ? config.get("currentJava").getAsString() : "";
        if (current.isEmpty()) {
            System.out.println("未设置当前 Java，请先添加并切换。");
            return;
        }
        Path javaHome = Paths.get(current);
        Path javaBin = javaHome.resolve("bin/java");
        if (!Files.isExecutable(javaBin)) javaBin = Paths.get(current, "bin/java.exe");
        System.out.println("当前 Java 路径: " + javaHome);
        System.out.println("可执行文件: " + javaBin);
        // 运行 java -version 获取详细信息
        try {
            ProcessBuilder pb = new ProcessBuilder(javaBin.toString(), "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            process.waitFor();
        } catch (Exception e) {
            System.out.println("无法获取 Java 版本信息: " + e.getMessage());
        }
    }

    // ========== 辅助方法 ==========
    private String getJavaVersion(Path javaHome) {
        Path javaBin = javaHome.resolve("bin/java");
        if (!Files.isExecutable(javaBin)) javaBin = Paths.get(javaHome.toString(), "bin/java.exe");
        try {
            ProcessBuilder pb = new ProcessBuilder(javaBin.toString(), "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) return line.replace("\"", "").trim();
            }
            process.waitFor();
        } catch (Exception e) {
            return "未知";
        }
        return "未知";
    }
}