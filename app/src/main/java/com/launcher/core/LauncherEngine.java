package com.launcher.core;

import com.launcher.log.LogManager;
import com.launcher.log.Logger;
import com.launcher.mod.ModLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LauncherEngine {

    private static final Logger LOGGER = LogManager.getLogger(LauncherEngine.class);

    private final Path launcherWorkDir;
    private final Path minecraftDir;
    private final EventBus eventBus;
    private final ModLoader modLoader;

    public LauncherEngine() {
        this.launcherWorkDir = Paths.get(System.getProperty("user.dir"), "Non-existent-Launcher");
        this.minecraftDir = getStandardMinecraftDir();
        this.eventBus = new EventBus();
        this.modLoader = new ModLoader(eventBus);
    }

    public void initializeEnvironment() {
        LOGGER.info("正在初始化启动器引擎环境...");

        String[] launcherDirs = {"mods", "config"};
        for (String dirName : launcherDirs) {
            createDirIfNeeded(launcherWorkDir.resolve(dirName));
        }

        Path configPath = launcherWorkDir.resolve("launcher.json");
        if (!Files.exists(configPath)) {
            String defaultConfig = "{\n" +
                    "  \"engine_version\": \"1.0.0\",\n" +
                    "  \"default_jvm_args\": [\"-Xmx2G\"],\n" +
                    "  \"debug_mode\": false\n" +
                    "}";
            try {
                Files.writeString(configPath, defaultConfig);
                LOGGER.info("默认配置文件已生成: launcher.json");
            } catch (IOException e) {
                LOGGER.error("生成配置文件失败");
                e.printStackTrace();
            }
        }

        LOGGER.info("引擎环境初始化完成！");
        LOGGER.info("Minecraft 数据目录: " + minecraftDir);
    }

    /**
     * 启动引擎，按 Forge 标准生命周期执行
     */
    public void start() {
        // 1. CONSTRUCT 阶段：扫描并实例化 Mod
        LOGGER.info("[CONSTRUCT] 开始加载 Mod...");
        File modsDir = launcherWorkDir.resolve("mods").toFile();
        modLoader.loadMods(modsDir);
        eventBus.post(new ConstructEvent());

        // 2. INITIALIZE 阶段：触发初始化事件
        LOGGER.info("[INITIALIZE] Mod 初始化完成");
        eventBus.post(new InitializeEvent());

        // 3. LAUNCH 阶段：准备启动
        LOGGER.info("[LAUNCH] 引擎准备就绪");
        eventBus.post(new LaunchEvent());
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public Path getLauncherWorkDir() {
        return launcherWorkDir;
    }

    public Path getMinecraftDir() {
        return minecraftDir;
    }

    private void createDirIfNeeded(Path path) {
        try {
            Files.createDirectories(path);
            LOGGER.info("目录已就绪: " + path);
        } catch (IOException e) {
            LOGGER.error("创建目录失败: " + path);
            e.printStackTrace();
        }
    }

    private Path getStandardMinecraftDir() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                return Paths.get(appData, ".minecraft");
            }
            return Paths.get(userHome, "AppData", "Roaming", ".minecraft");
        } else if (os.contains("mac")) {
            return Paths.get(userHome, "Library", "Application Support", "minecraft");
        } else {
            return Paths.get(userHome, ".minecraft");
        }
    }
}
