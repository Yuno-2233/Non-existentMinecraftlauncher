package io.github.yuno2233.neml.installer;

import com.github.yuno2233.neml.api.CommandProvider;
import com.github.yuno2233.neml.log.NemlLogger;
import com.google.gson.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public class InstallerCommandDispatcher implements CommandProvider {
    private static final Logger log = NemlLogger.getModLogger("installer");
    private static final Path VERSIONS_CONFIG = Paths.get("neml", "config", "versions", "config.json");
    private static final String VERSION_MANIFEST_URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");

    @Override
    public void execute(String[] args) {
        if (args.length == 0 || !args[0].equals("install") || args.length < 2) {
            System.out.println("用法: neml installer install <版本ID> [自定义名称]");
            return;
        }

        String versionId = args[1];
        String customName = args.length >= 3 ? args[2] : versionId;

        Path mcDir = getMinecraftDir();
        System.out.println("当前 .minecraft 目录: " + mcDir.toAbsolutePath());
        System.out.println("正在安装版本: " + versionId + " (存储为: " + customName + ")");

        try {
            downloadVersionJson(mcDir, versionId, customName);
            downloadClientJar(mcDir, versionId, customName);
            downloadLibraries(mcDir, versionId, customName);
            downloadAssets(mcDir, versionId, customName);
            System.out.println("版本 " + customName + " 安装完成！");
        } catch (Exception e) {
            System.out.println("安装失败: " + e.getMessage());
            log.severe("安装异常: " + e.getMessage());
        }
    }

    private void downloadVersionJson(Path mcDir, String versionId, String customName) throws Exception {
        Path versionsDir = mcDir.resolve("versions");
        Path versionDir = versionsDir.resolve(customName);
        Path jsonFile = versionDir.resolve(customName + ".json");

        if (Files.exists(jsonFile)) {
            System.out.println("版本 JSON 已存在: " + jsonFile.toAbsolutePath());
            return;
        }

        System.out.println("正在获取版本清单...");
        String manifestJson = downloadString(VERSION_MANIFEST_URL);
        JsonObject manifest = new Gson().fromJson(manifestJson, JsonObject.class);
        JsonArray versions = manifest.getAsJsonArray("versions");

        String versionUrl = null;
        for (JsonElement e : versions) {
            JsonObject v = e.getAsJsonObject();
            if (v.get("id").getAsString().equals(versionId)) {
                versionUrl = v.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) {
            throw new Exception("未找到版本 " + versionId + " 在 Mojang 清单中");
        }

        System.out.println("正在下载版本 JSON...");
        String versionJsonContent = downloadString(versionUrl);

        // 修改 id 为自定义名称
        JsonObject jsonObj = new Gson().fromJson(versionJsonContent, JsonObject.class);
        jsonObj.addProperty("id", customName);
        versionJsonContent = new Gson().toJson(jsonObj);

        Files.createDirectories(versionDir);
        Files.writeString(jsonFile, versionJsonContent);
        System.out.println("版本 JSON 已保存: " + jsonFile.toAbsolutePath());
    }

    private void downloadClientJar(Path mcDir, String versionId, String customName) throws Exception {
        Path versionsDir = mcDir.resolve("versions");
        Path versionDir = versionsDir.resolve(customName);
        Path jarFile = versionDir.resolve(customName + ".jar");

        if (Files.exists(jarFile)) {
            System.out.println("客户端 jar 已存在: " + jarFile.toAbsolutePath());
            return;
        }

        Path jsonFile = versionDir.resolve(customName + ".json");
        if (!Files.exists(jsonFile)) {
            throw new Exception("版本 JSON 不存在，请先下载");
        }
        String jsonContent = Files.readString(jsonFile);
        JsonObject versionJson = new Gson().fromJson(jsonContent, JsonObject.class);
        JsonObject downloads = versionJson.getAsJsonObject("downloads");
        if (downloads == null || !downloads.has("client")) {
            throw new Exception("版本 JSON 中没有客户端下载信息");
        }
        JsonObject clientInfo = downloads.getAsJsonObject("client");
        String clientUrl = clientInfo.get("url").getAsString();
        long expectedSize = clientInfo.has("size") ? clientInfo.get("size").getAsLong() : -1;

        System.out.println("正在下载客户端 jar...");
        downloadFile(clientUrl, jarFile, expectedSize);
        System.out.println("客户端 jar 已保存: " + jarFile.toAbsolutePath());
    }

    private void downloadLibraries(Path mcDir, String versionId, String customName) throws Exception {
        Path versionsDir = mcDir.resolve("versions");
        Path versionDir = versionsDir.resolve(customName);
        Path jsonFile = versionDir.resolve(customName + ".json");

        String jsonContent = Files.readString(jsonFile);
        JsonObject versionJson = new Gson().fromJson(jsonContent, JsonObject.class);
        JsonArray libraries = versionJson.getAsJsonArray("libraries");
        if (libraries == null || libraries.size() == 0) {
            System.out.println("没有需要下载的库文件");
            return;
        }

        Path librariesDir = mcDir.resolve("libraries");
        int total = 0;
        int downloaded = 0;

        for (JsonElement libElem : libraries) {
            JsonObject libObj = libElem.getAsJsonObject();
            if (!shouldDownloadLibrary(libObj)) continue;
            total++;

            // 主构件下载
            if (libObj.has("downloads") && libObj.getAsJsonObject("downloads").has("artifact")) {
                JsonObject artifactInfo = libObj.getAsJsonObject("downloads").getAsJsonObject("artifact");
                String downloadUrl = artifactInfo.get("url").getAsString();
                String path = artifactInfo.get("path").getAsString();
                Path targetPath = librariesDir.resolve(path);
                if (!Files.exists(targetPath)) {
                    try {
                        System.out.println("下载库: " + path);
                        long expectedSize = artifactInfo.has("size") ? artifactInfo.get("size").getAsLong() : -1;
                        downloadFile(downloadUrl, targetPath, expectedSize);
                        downloaded++;
                    } catch (Exception e) {
                        log.warning("下载库失败: " + path + " - " + e.getMessage());
                    }
                }
            }
        }

        // ========== 新增：无论是否下载，都确保所有原生库被解压 ==========
        Path nativesDir = versionDir.resolve("natives");
        for (JsonElement libElem : libraries) {
            JsonObject libObj = libElem.getAsJsonObject();
            if (!shouldDownloadLibrary(libObj)) continue;
            if (libObj.has("natives")) {
                JsonObject natives = libObj.getAsJsonObject("natives");
                String osKey = IS_MAC ? "osx" : (IS_WINDOWS ? "windows" : "linux");
                if (natives.has(osKey)) {
                    String classifier = natives.get(osKey).getAsString();
                    if (libObj.has("downloads") && libObj.getAsJsonObject("downloads").has("classifiers")) {
                        JsonObject classifiers = libObj.getAsJsonObject("downloads").getAsJsonObject("classifiers");
                        if (classifiers.has(classifier)) {
                            JsonObject nativeInfo = classifiers.getAsJsonObject(classifier);
                            String path = nativeInfo.get("path").getAsString();
                            Path targetPath = librariesDir.resolve(path);
                            if (Files.exists(targetPath)) {
                                try {
                                    extractNatives(targetPath, nativesDir);
                                } catch (Exception e) {
                                    log.warning("解压原生库失败: " + path);
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("库文件处理完成: 总计 " + total + " 个，本次下载 " + downloaded + " 个。");
    }

    private void downloadAssets(Path mcDir, String versionId, String customName) throws Exception {
        Path versionsDir = mcDir.resolve("versions");
        Path versionDir = versionsDir.resolve(customName);
        Path jsonFile = versionDir.resolve(customName + ".json");

        if (!Files.exists(jsonFile)) {
            System.out.println("版本 JSON 不存在，跳过资源下载");
            return;
        }

        String jsonContent = Files.readString(jsonFile);
        JsonObject versionJson = new Gson().fromJson(jsonContent, JsonObject.class);
        if (!versionJson.has("assetIndex")) {
            System.out.println("版本没有资产索引，跳过资源下载");
            return;
        }

        JsonObject assetIndexInfo = versionJson.getAsJsonObject("assetIndex");
        String assetIndexId = assetIndexInfo.get("id").getAsString();
        String assetIndexUrl = assetIndexInfo.get("url").getAsString();

        Path assetsDir = mcDir.resolve("assets");
        Path indexesDir = assetsDir.resolve("indexes");
        Path indexFile = indexesDir.resolve(assetIndexId + ".json");

        if (!Files.exists(indexFile)) {
            System.out.println("下载资产索引: " + assetIndexId);
            downloadFile(assetIndexUrl, indexFile, -1);
        }

        String indexContent = Files.readString(indexFile);
        JsonObject indexJson = new Gson().fromJson(indexContent, JsonObject.class);
        JsonObject objects = indexJson.getAsJsonObject("objects");
        if (objects == null) {
            System.out.println("索引中没有资源对象");
            return;
        }

        Path objectsDir = assetsDir.resolve("objects");
        int total = objects.size();
        int downloaded = 0;

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            String assetName = entry.getKey();
            JsonObject assetObj = entry.getValue().getAsJsonObject();
            String hash = assetObj.get("hash").getAsString();
            String subPath = hash.substring(0, 2) + "/" + hash;
            Path assetFile = objectsDir.resolve(subPath);

            if (Files.exists(assetFile) && Files.size(assetFile) > 0) {
                continue;
            }

            String downloadUrl = "https://resources.download.minecraft.net/" + subPath;
            try {
                Files.createDirectories(assetFile.getParent());
                downloadFile(downloadUrl, assetFile, -1);
                downloaded++;
                if (downloaded % 50 == 0) {
                    System.out.printf("资源下载进度: %d/%d\n", downloaded, total);
                }
            } catch (Exception e) {
                log.warning("资源下载失败: " + assetName + " - " + e.getMessage());
            }
        }
        System.out.printf("资源下载完成: %d/%d\n", downloaded, total);
    }

    // ---------- 工具方法 ----------
    private void extractNatives(Path nativeJar, Path nativesDir) throws Exception {
        Files.createDirectories(nativesDir);
        try (JarFile jar = new JarFile(nativeJar.toFile())) {
            jar.stream().forEach(entry -> {
                String name = entry.getName();
                if (name.startsWith("META-INF")) return;
                if (name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")) {
                    try {
                        Path outFile = nativesDir.resolve(name);
                        Files.createDirectories(outFile.getParent());
                        Files.copy(jar.getInputStream(entry), outFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {}
                }
            });
        }
    }

    private boolean shouldDownloadLibrary(JsonObject libObj) {
        if (libObj.has("rules")) {
            JsonArray rules = libObj.getAsJsonArray("rules");
            boolean allowed = true;
            for (JsonElement ruleElem : rules) {
                JsonObject rule = ruleElem.getAsJsonObject();
                String action = rule.get("action").getAsString();
                boolean match = matchOsRule(rule);
                if (action.equals("allow") && match) {
                    allowed = true;
                } else if (action.equals("disallow") && match) {
                    allowed = false;
                }
            }
            return allowed;
        }
        return true;
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

    private void downloadFile(String urlStr, Path targetPath, long expectedSize) throws Exception {
        Files.createDirectories(targetPath.getParent());
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            if (expectedSize > 0 && Files.size(targetPath) != expectedSize) {
                log.warning("文件大小校验失败: " + targetPath + " 期望 " + expectedSize + " 实际 " + Files.size(targetPath));
            }
        }
    }

    private String downloadString(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        try (InputStream in = url.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private Path getMinecraftDir() {
        if (!Files.exists(VERSIONS_CONFIG)) return Paths.get(".minecraft");
        try {
            String content = Files.readString(VERSIONS_CONFIG);
            JsonObject config = new Gson().fromJson(content, JsonObject.class);
            if (config.has("minecraftPath")) {
                return Paths.get(config.get("minecraftPath").getAsString());
            }
        } catch (Exception e) {
            log.warning("读取 versions 配置失败，使用默认路径 .minecraft");
        }
        return Paths.get(".minecraft");
    }
}