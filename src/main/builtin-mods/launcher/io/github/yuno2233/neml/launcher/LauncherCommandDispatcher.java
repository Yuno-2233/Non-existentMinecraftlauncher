package io.github.yuno2233.neml.launcher;

import com.github.yuno2233.neml.api.CommandProvider;
import com.github.yuno2233.neml.log.NemlLogger;
import com.google.gson.*;

import io.github.yuno2233.neml.java.JavaCommandDispatcher;
import io.github.yuno2233.neml.account.AccountCommandDispatcher;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public class LauncherCommandDispatcher implements CommandProvider {
    private static final Logger log = NemlLogger.getModLogger("launcher");
    private static final Path LAUNCHER_CONFIG = Paths.get("neml", "config", "launcher", "config.json");
    private static final Path VERSIONS_CONFIG = Paths.get("neml", "config", "versions", "config.json");
    private static final Path ACCOUNT_CONFIG = Paths.get("neml", "config", "account", "config.json");
    private static final Gson gson = new Gson();
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        switch (args[0]) {
            case "set-default":
                if (args.length < 2) {
                    System.out.println("用法: neml launcher set-default <版本ID>");
                } else {
                    setDefaultVersion(args[1]);
                }
                break;
            case "launch":
                String version = args.length >= 2 ? args[1] : getDefaultVersion();
                if (version == null || version.isEmpty()) {
                    System.out.println("未指定版本且没有默认版本。请先用 'neml launcher set-default <版本>' 设置。");
                    return;
                }
                launchGame(version);
                break;
            default:
                System.out.println("未知命令: " + args[0]);
        }
    }

    private void printUsage() {
        System.out.println("用法: neml launcher <命令>");
        System.out.println("  set-default <版本ID>  设置默认启动版本");
        System.out.println("  launch [版本ID]       启动游戏");
    }

    // ---------- 配置读取 ----------
    private Path getMinecraftPath() {
        if (!Files.exists(VERSIONS_CONFIG)) return Paths.get(".minecraft");
        try {
            JsonObject config = gson.fromJson(Files.readString(VERSIONS_CONFIG), JsonObject.class);
            return Paths.get(config.has("minecraftPath") ? config.get("minecraftPath").getAsString() : ".minecraft");
        } catch (Exception e) {
            return Paths.get(".minecraft");
        }
    }

    private String[] getCurrentAccount() {
        JsonObject acc = AccountCommandDispatcher.getCurrentAccount();
        if (acc == null) {
            return new String[]{"Player", "0", "0"};
        }
        String name = acc.get("name").getAsString();
        String uuid = acc.has("uuid") ? acc.get("uuid").getAsString() : "0";
        String accessToken = "0";
        if (acc.has("type") && acc.get("type").getAsString().equals("microsoft")) {
            accessToken = acc.get("accessToken").getAsString();
        }
        return new String[]{name, uuid, accessToken};
    }

    private String getDefaultVersion() {
        if (!Files.exists(LAUNCHER_CONFIG)) return null;
        try {
            JsonObject config = gson.fromJson(Files.readString(LAUNCHER_CONFIG), JsonObject.class);
            return config.has("defaultVersion") ? config.get("defaultVersion").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void setDefaultVersion(String version) {
        JsonObject config = new JsonObject();
        config.addProperty("defaultVersion", version);
        try {
            Files.createDirectories(LAUNCHER_CONFIG.getParent());
            Files.writeString(LAUNCHER_CONFIG, gson.toJson(config));
            System.out.println("默认版本已设置为: " + version);
        } catch (Exception e) {
            System.out.println("设置默认版本失败: " + e.getMessage());
        }
    }

    // ---------- 启动核心 ----------
    private void launchGame(String versionId) {
        Path mcDir = getMinecraftPath();
        Path versionsDir = mcDir.resolve("versions");
        Path versionDir = versionsDir.resolve(versionId);
        Path jsonFile = versionDir.resolve(versionId + ".json");

        if (!Files.exists(jsonFile)) {
            System.out.println("错误: 版本 " + versionId + " 不存在。");
            return;
        }

        try (Reader reader = Files.newBufferedReader(jsonFile)) {
            JsonObject versionJson = gson.fromJson(reader, JsonObject.class);
            String mainClass = versionJson.get("mainClass").getAsString();
            JsonObject accJson = AccountCommandDispatcher.getCurrentAccount();
            String username;
            String uuid;
            String accessToken = "0";

            if (accJson == null) {
                username = "Player";
                uuid = "0";
            } else {
                username = accJson.get("name").getAsString();
                String type = accJson.has("type") ? accJson.get("type").getAsString() : "offline";
                if (type.equals("microsoft")) {
                    uuid = accJson.get("uuid").getAsString();
                    accessToken = accJson.get("accessToken").getAsString();
                } else if (type.equals("thirdparty")) {
                    // 使用选定角色的 UUID
                    if (accJson.has("characterUuid") && !accJson.get("characterUuid").getAsString().equals("0")) {
                        uuid = accJson.get("characterUuid").getAsString();
                    } else {
                        uuid = "0";
                    }
                    // 角色名作为游戏内用户名
                    username = accJson.has("characterName") ? accJson.get("characterName").getAsString() : username;
                } else {
                    // 离线账号
                    uuid = accJson.has("uuid") ? accJson.get("uuid").getAsString() : "0";
                }
            }
    
    
            // 变量表
            Map<String, String> variables = new HashMap<>();
            
            // 读取版本单独配置
            Path versionConfigFile = versionDir.resolve("neml-version.json");
            JsonObject versionConfig = new JsonObject();
            if (Files.exists(versionConfigFile)) {
                try {
                    String content = Files.readString(versionConfigFile);
                    versionConfig = new Gson().fromJson(content, JsonObject.class);
                } catch (Exception e) {
                    log.warning("读取版本配置失败: " + versionConfigFile);
                }
            }

            boolean isolate = versionConfig.has("isolate") ? versionConfig.get("isolate").getAsBoolean() : true;
            Path gameDir;
            if (isolate) {
                gameDir = versionDir;  // 版本文件夹作为游戏目录
            } else {
                gameDir = mcDir;       // .minecraft 根目录
            }
            
            variables.put("game_directory", gameDir.toAbsolutePath().toString());
            variables.put("auth_player_name", username);
            variables.put("auth_uuid", uuid);
            variables.put("user_type", "mojang");
            variables.put("version_name", versionId);
            variables.put("launcher_name", "NEML");
            variables.put("launcher_version", "1.0.0");
            variables.put("game_directory", mcDir.toAbsolutePath().toString());
            variables.put("assets_root", mcDir.resolve("assets").toAbsolutePath().toString());
            variables.put("assets_index_name", getAssetIndex(versionJson));
            variables.put("auth_access_token", "0");
            variables.put("clientid", "0");
            variables.put("auth_xuid", "0");
            variables.put("version_type", versionJson.has("type") ? versionJson.get("type").getAsString() : "release");
            variables.put("resolution_width", "854");
            variables.put("resolution_height", "480");
            variables.put("quickPlayPath", "");
            variables.put("quickPlaySingleplayer", "");
            variables.put("quickPlayMultiplayer", "");
            variables.put("quickPlayRealms", "");

            Path nativesDir = versionDir.resolve("natives");
            variables.put("natives_directory", nativesDir.toAbsolutePath().toString());
            Path librariesBase = mcDir.resolve("libraries");
            variables.put("library_directory", librariesBase.toAbsolutePath().toString());
            variables.put("classpath_separator", File.pathSeparator);

            String classpathStr = buildClasspath(versionDir, versionId, librariesBase, versionJson);
            variables.put("classpath", classpathStr);

            // 提取 JVM 参数（不自动添加）
            List<String> jvmArgs = new ArrayList<>();
            if (versionJson.has("arguments")) {
                JsonObject arguments = versionJson.getAsJsonObject("arguments");
                if (arguments.has("jvm")) {
                    jvmArgs = extractArguments(arguments.getAsJsonArray("jvm"), variables);
                }
            }

            // 游戏参数
            List<String> gameArgs = new ArrayList<>();
            if (versionJson.has("arguments") && versionJson.getAsJsonObject("arguments").has("game")) {
                gameArgs = extractArguments(versionJson.getAsJsonObject("arguments").getAsJsonArray("game"), variables);
            } else if (versionJson.has("minecraftArguments")) {
                for (String s : versionJson.get("minecraftArguments").getAsString().split(" ")) {
                    if (!s.isEmpty()) gameArgs.add(replaceVariables(s, variables));
                }
            }
             
            // 确定使用的 Java 路径：优先使用版本配置，其次使用 java mod 的全局配置，最后使用当前运行时的 java
            Path javaHomePath = null;
            // 1. 从版本配置中读取 javaPath（如果有）
            if (versionConfig.has("javaPath")) {
                javaHomePath = Paths.get(versionConfig.get("javaPath").getAsString());
            }
            // 2. 否则尝试从 java mod 的全局配置读取
            if (javaHomePath == null) {
                Path globalJavaPath = io.github.yuno2233.neml.java.JavaCommandDispatcher.getCurrentJavaPath();
                if (globalJavaPath != null) {
                    javaHomePath = globalJavaPath;
                }
            }
            // 3. 如果还没有，使用当前 JRE
            if (javaHomePath == null || !Files.isDirectory(javaHomePath)) {
                javaHomePath = Paths.get(System.getProperty("java.home"));
            }
            String javaBin = javaHomePath.resolve("bin/java").toString();
            if (!Files.isExecutable(Paths.get(javaBin))) {
                javaBin = javaHomePath.resolve("bin/java.exe").toString();  // Windows
            }
            
            // 如果是第三方账号，添加 authlib-injector 代理
            if (accJson != null && accJson.has("type") && accJson.get("type").getAsString().equals("thirdparty")) {
                String serverUrl = accJson.get("server").getAsString();
                String authlibJar = System.getenv("NEML_AUTHLIB_JAR");
                if (authlibJar == null || authlibJar.isEmpty()) {
                    authlibJar = mcDir.resolve("authlib-injector.jar").toAbsolutePath().toString();
                }
                // 如果 jar 文件不存在，给出警告
                if (!Files.exists(Paths.get(authlibJar))) {
                    System.out.println("警告: authlib-injector.jar 不存在: " + authlibJar);
                }
                jvmArgs.add("-javaagent:" + authlibJar + "=" + serverUrl);
                jvmArgs.add("-Dauthlibinjector.debug=all"); // 可选
            }
            
            List<String> command = new ArrayList<>();
            command.add(javaBin);
            jvmArgs.add("-Djava.library.path=" + nativesDir.toAbsolutePath().toString());
            command.addAll(jvmArgs);
            command.add(mainClass);
            command.addAll(gameArgs);
            
            // 如果 JVM 参数中没有指定 classpath，自动添加
            boolean hasClasspath = false;
            for (String arg : jvmArgs) {
                if (arg.equals("-cp") || arg.equals("-classpath")) {
                    hasClasspath = true;
                    break;
                }
            }
            if (!hasClasspath) {
                command.add("-cp");
                command.add(classpathStr); // classpathStr 已在前面通过 buildClasspath 计算
            }
            command.add(mainClass);
            
            // 从版本配置中添加自定义 JVM 参数
            if (versionConfig.has("jvmArgs")) {
                JsonArray customJvm = versionConfig.getAsJsonArray("jvmArgs");
                for (JsonElement e : customJvm) {
                    jvmArgs.add(replaceVariables(e.getAsString(), variables));
                }
            }

            // 从版本配置中添加自定义游戏参数
            if (versionConfig.has("gameArgs")) {
                JsonArray customGame = versionConfig.getAsJsonArray("gameArgs");
                for (JsonElement e : customGame) {
                    gameArgs.add(replaceVariables(e.getAsString(), variables));
                }
            }
     
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            pb.directory(mcDir.toFile());

            System.out.println("启动游戏 " + versionId + " 以玩家 " + username + "...");
            Process process = pb.start();
            int exitCode = process.waitFor();
            System.out.println("游戏退出，代码: " + exitCode);

        } catch (Exception e) {
            System.out.println("启动失败: " + e.getMessage());
            log.log(java.util.logging.Level.SEVERE, "启动异常", e);
        }
    }

    // 提取参数（规则过滤 + 变量替换）
    private List<String> extractArguments(JsonArray argsArray, Map<String, String> variables) {
        List<String> result = new ArrayList<>();
        for (JsonElement elem : argsArray) {
            if (elem.isJsonPrimitive()) {
                result.add(replaceVariables(elem.getAsString(), variables));
            } else if (elem.isJsonObject()) {
                JsonObject obj = elem.getAsJsonObject();
                if (shouldIncludeArgument(obj)) {
                    if (obj.has("value")) {
                        JsonElement val = obj.get("value");
                        if (val.isJsonPrimitive()) {
                            result.add(replaceVariables(val.getAsString(), variables));
                        } else if (val.isJsonArray()) {
                            for (JsonElement v : val.getAsJsonArray()) {
                                if (v.isJsonPrimitive()) result.add(replaceVariables(v.getAsString(), variables));
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private String replaceVariables(String input, Map<String, String> vars) {
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            input = input.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return input;
    }

    private boolean shouldIncludeArgument(JsonObject ruleObj) {
        if (!ruleObj.has("rules")) return true;
        JsonArray rules = ruleObj.getAsJsonArray("rules");
        for (JsonElement elem : rules) {
            JsonObject rule = elem.getAsJsonObject();
            String action = rule.get("action").getAsString();
            if (action.equals("allow") && matchOsRule(rule)) return true;
            if (action.equals("disallow") && matchOsRule(rule)) return false;
        }
        return false;
    }

    private boolean matchOsRule(JsonObject rule) {
        if (!rule.has("os")) return true;
        JsonObject os = rule.getAsJsonObject("os");
        String name = os.has("name") ? os.get("name").getAsString() : "";
        if (name.equalsIgnoreCase("windows")) return IS_WINDOWS;
        if (name.equalsIgnoreCase("osx")) return IS_MAC;
        if (name.equalsIgnoreCase("linux")) return !IS_WINDOWS && !IS_MAC;
        return false;
    }

    // ---------- classpath 构建 ----------
    private String buildClasspath(Path versionDir, String versionId, Path librariesBase, JsonObject versionJson) {
        Set<String> paths = new LinkedHashSet<>();
        // 版本 jar
        Path versionJar = versionDir.resolve(versionId + ".jar");
        if (Files.exists(versionJar)) paths.add(versionJar.toAbsolutePath().toString());

        JsonArray libraries = versionJson.getAsJsonArray("libraries");
        for (JsonElement lib : libraries) {
            JsonObject libObj = lib.getAsJsonObject();
            if (!shouldIncludeArgument(libObj)) continue;
            String name = libObj.get("name").getAsString();

            // 主库 jar（无 classifier）
            Path mainLib = resolveLibraryPath(librariesBase, name, null);
            if (mainLib != null) {
                if (Files.exists(mainLib)) {
                    paths.add(mainLib.toAbsolutePath().toString());
                } else {
                    log.warning("库文件缺失，将跳过: " + mainLib);
                }
            }

            // 如果有 natives 字段，添加对应平台的 native jar
            if (libObj.has("natives")) {
                JsonObject natives = libObj.getAsJsonObject("natives");
                String osKey = IS_MAC ? "osx" : (IS_WINDOWS ? "windows" : "linux");
                if (natives.has(osKey)) {
                    String classifier = natives.get(osKey).getAsString();
                    Path nativeLib = resolveLibraryPath(librariesBase, name, classifier);
                    if (nativeLib != null) paths.add(nativeLib.toAbsolutePath().toString());
                }
            }
        }
        return String.join(File.pathSeparator, paths);
    }

    // ---------- 库文件路径解析（支持备用路径） ----------
    private Path resolveLibraryPath(Path librariesDir, String name, String classifier) {
        String[] parts = name.split(":");
        if (parts.length < 3) return null;
        String group = parts[0];
        String artifact = parts[1];
        String version = parts[2];

        // 标准路径
        Path standardPath = buildLibPath(librariesDir, group, artifact, version, classifier);
        if (Files.exists(standardPath)) return standardPath;

        // 备用路径：如果 group 以 "org." 开头，尝试去掉 "org."
        if (group.startsWith("org.")) {
            Path altPath = buildLibPath(librariesDir, group.substring(4), artifact, version, classifier);
            if (Files.exists(altPath)) {
                log.fine("使用备用路径: " + altPath);
                return altPath;
            }
        }

        // 可选：你也可以尝试将 group 整个替换为小写（某些非标准路径）
        return null;
    }

    private Path buildLibPath(Path librariesDir, String group, String artifact, String version, String classifier) {
        String groupPath = group.replace('.', '/');
        String fileName;
        if (classifier != null && !classifier.isEmpty()) {
            fileName = artifact + "-" + version + "-" + classifier + ".jar";
        } else {
            fileName = artifact + "-" + version + ".jar";
        }
        return librariesDir.resolve(groupPath).resolve(artifact).resolve(version).resolve(fileName);
    }

    private String getAssetIndex(JsonObject versionJson) {
        if (versionJson.has("assetIndex")) {
            return versionJson.getAsJsonObject("assetIndex").get("id").getAsString();
        }
        return "legacy";
    }
}