package io.github.yuno2233.neml.versions;

import com.github.yuno2233.neml.api.CommandProvider;
import com.github.yuno2233.neml.log.NemlLogger;
import com.google.gson.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class VersionsCommandDispatcher implements CommandProvider {
    private static final Logger log = NemlLogger.getModLogger("versions");
    private static final Path CONFIG_FILE = Paths.get("neml", "config", "versions", "config.json");
    private static final Gson gson = new Gson();

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String sub = args[0];
        switch (sub) {
            case "isolate":
                handleIsolate(args);
                break;
            case "config":
                handleConfig(args);
                break;
            case "list":
                listVersions();
                break;
            case "dir":
                if (args.length < 2) {
                    System.out.println("用法: neml versions dir <add|list|switch> [参数]");
                    return;
                }
                switch (args[1]) {
                    case "add":
                        if (args.length < 3) {
                            System.out.println("用法: neml versions dir add <路径>");
                        } else {
                            addDirectory(args[2]);
                        }
                        break;
                    case "list":
                        listDirectories();
                        break;
                    case "switch":
                        if (args.length < 3) {
                            System.out.println("用法: neml versions dir switch <索引>");
                        } else {
                            switchDirectory(args[2]);
                        }
                        break;
                    default:
                        System.out.println("未知 dir 子命令: " + args[1]);
                }
                break;
            default:
                System.out.println("未知命令: " + sub);
        }
    }

    private void printUsage() {
        System.out.println("用法: neml versions <命令>");
        System.out.println("  list           列出当前 .minecraft 中已安装的版本");
        System.out.println("  dir add <路径>  添加一个 .minecraft 目录");
        System.out.println("  dir list        列出所有已添加的目录");
        System.out.println("  dir switch <索引> 切换到指定目录");
    }

    // ---------- 配置管理 ----------
    private JsonObject loadConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            JsonObject def = new JsonObject();
            def.addProperty("minecraftPath", ".minecraft");
            def.add("directories", new JsonArray());
            saveConfig(def);
            return def;
        }
        try {
            String content = Files.readString(CONFIG_FILE);
            return gson.fromJson(content, JsonObject.class);
        } catch (Exception e) {
            log.warning("配置读取失败，使用默认");
            JsonObject def = new JsonObject();
            def.addProperty("minecraftPath", ".minecraft");
            def.add("directories", new JsonArray());
            return def;
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

    private Path getCurrentMinecraftDir() {
        JsonObject config = loadConfig();
        String path = config.has("minecraftPath") ? config.get("minecraftPath").getAsString() : ".minecraft";
        return Paths.get(path);
    }

    // ---------- 目录管理 ----------
    private void addDirectory(String dirPath) {
        Path p = Paths.get(dirPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(p)) {
            System.out.println("错误: 路径不存在或不是目录: " + p);
            return;
        }
        JsonObject config = loadConfig();
        JsonArray dirs = config.has("directories") ? config.getAsJsonArray("directories") : new JsonArray();
        for (JsonElement e : dirs) {
            if (e.getAsString().equals(p.toString())) {
                System.out.println("目录已存在: " + p);
                config.addProperty("minecraftPath", p.toString());
                saveConfig(config);
                System.out.println("已切换到该目录。");
                return;
            }
        }
        dirs.add(p.toString());
        config.add("directories", dirs);
        config.addProperty("minecraftPath", p.toString());
        saveConfig(config);
        System.out.println("已添加目录并切换到: " + p);
    }

    private void listDirectories() {
        JsonObject config = loadConfig();
        JsonArray dirs = config.has("directories") ? config.getAsJsonArray("directories") : new JsonArray();
        String current = config.has("minecraftPath") ? config.get("minecraftPath").getAsString() : null;
        if (dirs.size() == 0) {
            System.out.println("没有已添加的目录，请使用 'neml versions dir add <路径>' 添加。");
            return;
        }
        System.out.println("已添加的 .minecraft 目录:");
        for (int i = 0; i < dirs.size(); i++) {
            String d = dirs.get(i).getAsString();
            String mark = d.equals(current) ? " * 当前" : "";
            System.out.printf("  [%d] %s%s\n", i, d, mark);
        }
    }

    private void switchDirectory(String indexStr) {
        JsonObject config = loadConfig();
        JsonArray dirs = config.has("directories") ? config.getAsJsonArray("directories") : new JsonArray();
        if (dirs.size() == 0) {
            System.out.println("没有可切换的目录，请先添加。");
            return;
        }
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            String target = indexStr;
            for (int i = 0; i < dirs.size(); i++) {
                if (dirs.get(i).getAsString().equals(target)) {
                    index = i;
                    config.addProperty("minecraftPath", target);
                    saveConfig(config);
                    System.out.println("已切换到: " + target);
                    return;
                }
            }
            System.out.println("错误: 无效的索引或路径。");
            return;
        }
        if (index < 0 || index >= dirs.size()) {
            System.out.println("错误: 索引超出范围 (0-" + (dirs.size()-1) + ")");
            return;
        }
        String newPath = dirs.get(index).getAsString();
        config.addProperty("minecraftPath", newPath);
        saveConfig(config);
        System.out.println("已切换到: " + newPath);
    }

    // ---------- 版本扫描（基于当前目录） ----------
    private void listVersions() {
        Path minecraftDir = getCurrentMinecraftDir();
        Path versionsDir = minecraftDir.resolve("versions");

        if (!Files.exists(versionsDir)) {
            System.out.println("Minecraft 版本目录不存在: " + versionsDir.toAbsolutePath());
            System.out.println("当前使用的 .minecraft 路径: " + minecraftDir);
            return;
        }

        System.out.println("当前 .minecraft: " + minecraftDir.toAbsolutePath());
        System.out.println("已安装的版本:");
        // 表头
        System.out.printf("  %-28s %-12s %-10s %s\n", "版本名称", "MC版本", "类型", "状态");

        try (Stream<Path> dirs = Files.list(versionsDir)) {
            dirs.filter(Files::isDirectory)
                .forEach(this::processVersionDir);
        } catch (Exception e) {
            System.out.println("扫描版本失败: " + e.getMessage());
        }
    }

    private void processVersionDir(Path dir) {
        String folderName = dir.getFileName().toString();
        Path jsonFile = dir.resolve(folderName + ".json");
        if (!Files.exists(jsonFile)) {
            System.out.printf("  - %-28s (缺少 %s.json)\n", folderName, folderName);
            return;
        }
        try (Reader reader = Files.newBufferedReader(jsonFile)) {
            JsonObject versionJson = gson.fromJson(reader, JsonObject.class);
            String id = versionJson.has("id") ? versionJson.get("id").getAsString() : null;
            String type = versionJson.has("type") ? versionJson.get("type").getAsString() : "unknown";
            String inheritsFrom = versionJson.has("inheritsFrom") ? versionJson.get("inheritsFrom").getAsString() : null;

            if (id == null || !id.equals(folderName)) {
                System.out.printf("  - %-28s (json id 不匹配: %s)\n", folderName, id != null ? id : "无");
                return;
            }

            boolean hasJar = Files.exists(dir.resolve(folderName + ".jar"));
            String status = hasJar ? "✔" : "✘ jar缺失";

            // 提取 MC 版本号
            String mcVersion = inheritsFrom;
            if (mcVersion == null && versionJson.has("jar")) {
                String jar = versionJson.get("jar").getAsString();
                String[] parts = jar.split("/");
                for (String part : parts) {
                    if (part.matches("\\d+\\.\\d+(\\.\\d+)?")) {
                        mcVersion = part;
                        break;
                    }
                }
            }
            if (mcVersion == null) {
            // 从 id 中提取第一个数字版本号
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+\\.\\d+(\\.\\d+)?(?:-\\w+)?")
                        .matcher(id);
                if (m.find()) {
                    mcVersion = m.group();
                }
            }
            if (mcVersion == null) {
                mcVersion = "-";
            }
            System.out.printf("  %-28s %-12s %-10s %-6s %-4s %s\n", "版本名称", "MC版本", "类型", "状态", "隔离", "配置");
            // 读取版本配置获取隔离状态
            Path versionConfigFile = dir.resolve("neml-version.json");
            boolean isolated = true; // 默认开启
            boolean hasConfig = false;
            if (Files.exists(versionConfigFile)) {
                hasConfig = true;
                JsonObject vConfig = loadVersionConfig(versionConfigFile);
                isolated = vConfig.has("isolate") ? vConfig.get("isolate").getAsBoolean() : true;
            }
            String isolateStr = isolated ? "隔" : "共";
            String configStr = hasConfig ? "cfg" : "   ";
            System.out.printf("  - %-28s %-12s %-10s %-6s %-4s %s\n", id, mcVersion, type, status, isolateStr, configStr);
        } catch (Exception e) {
            System.out.printf("  - %-28s (json 解析失败: %s)\n", folderName, e.getMessage());
        }
    }
    
    private void handleIsolate(String[] args) {
        if (args.length < 2) {
            System.out.println("用法: neml versions isolate <版本ID> [on|off]");
            return;
        }
        String versionId = args[1];
        Path mcDir = getCurrentMinecraftDir();
        Path versionDir = mcDir.resolve("versions").resolve(versionId);
        if (!Files.isDirectory(versionDir)) {
            System.out.println("版本目录不存在: " + versionDir);
            return;
        }

        Path versionConfigFile = versionDir.resolve("neml-version.json");
        JsonObject versionConfig = loadVersionConfig(versionConfigFile);

        if (args.length == 2) {
            // 查看隔离状态
            boolean isolate = versionConfig.has("isolate") ? versionConfig.get("isolate").getAsBoolean() : true;
            System.out.println("版本 " + versionId + " 隔离状态: " + (isolate ? "开启" : "关闭"));
            return;
        }

        String mode = args[2];
        boolean enable;
        if (mode.equalsIgnoreCase("on") || mode.equalsIgnoreCase("true")) {
            enable = true;
        } else if (mode.equalsIgnoreCase("off") || mode.equalsIgnoreCase("false")) {
            enable = false;
        } else {
            System.out.println("参数错误，请使用 on 或 off");
            return;
        }

        versionConfig.addProperty("isolate", enable);
        saveVersionConfig(versionConfigFile, versionConfig);
        System.out.println("版本 " + versionId + " 隔离已" + (enable ? "开启" : "关闭"));
    }
    
    private void handleConfig(String[] args) {
        if (args.length < 2) {
            System.out.println("用法: neml versions config <版本ID> [set <key> <value>]");
            return;
        }
        String versionId = args[1];
        Path mcDir = getCurrentMinecraftDir();
        Path versionDir = mcDir.resolve("versions").resolve(versionId);
        if (!Files.isDirectory(versionDir)) {
            System.out.println("版本目录不存在: " + versionDir);
            return;
        }

        Path versionConfigFile = versionDir.resolve("neml-version.json");
        JsonObject versionConfig = loadVersionConfig(versionConfigFile);

        if (args.length == 2) {
            // 显示当前配置
            System.out.println("版本 " + versionId + " 配置:");
            System.out.println(gson.toJson(versionConfig));
            return;
        }

        if (args.length >= 5 && args[2].equals("set")) {
            String key = args[3];
            String value = args[4];
            // 根据 value 类型尝试存储为数字或布尔值
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                versionConfig.addProperty(key, Boolean.parseBoolean(value));
            } else {
                try {
                    double d = Double.parseDouble(value);
                    versionConfig.addProperty(key, d);
                } catch (NumberFormatException e) {
                    versionConfig.addProperty(key, value);
                }
            }
            saveVersionConfig(versionConfigFile, versionConfig);
            System.out.println("已设置 " + key + " = " + value);
        } else {
            System.out.println("用法: neml versions config <版本ID> [set <key> <value>]");
        }
    }
    
    private JsonObject loadVersionConfig(Path configFile) {
        if (Files.exists(configFile)) {
            try {
                String content = Files.readString(configFile);
                return gson.fromJson(content, JsonObject.class);
            } catch (Exception e) {
                log.warning("读取版本配置失败: " + configFile);
            }
        }
        // 默认配置：隔离开启
        JsonObject def = new JsonObject();
        def.addProperty("isolate", true);
        return def;
    }

    private void saveVersionConfig(Path configFile, JsonObject config) {
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, gson.toJson(config));
        } catch (Exception e) {
            log.warning("保存版本配置失败: " + configFile);
        }
    }
}