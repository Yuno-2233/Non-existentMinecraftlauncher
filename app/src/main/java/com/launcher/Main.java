package com.launcher;

import com.launcher.core.LauncherEngine;
import com.launcher.log.LogManager;
import com.launcher.log.Logger;
import com.launcher.util.ResourceUtils; // 1. 导入新创建的工具类

public class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        LOGGER.info("正在启动 Non-existent Minecraft Launcher (TUI)...");

        // 2. 在引擎启动前，执行资源释放
        // 这里的 "config/launcher.json" 指的是 src/main/resources/config/launcher.json
        // 它会释放到运行目录下的 ./config/launcher.json
        ResourceUtils.releaseResource("config/launcher.json", "./config/launcher.json");
        
        // 如果你还有其他需要释放的内置文件，可以在这里继续添加
        // 例如：ResourceUtils.releaseResource("mods/some_mod.jar", "./mods/some_mod.jar");

        LauncherEngine engine = new LauncherEngine();
        engine.start();
        LOGGER.info("引擎启动完成！");
    }
}
