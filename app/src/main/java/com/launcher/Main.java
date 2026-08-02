package com.launcher;

import com.launcher.core.LauncherEngine;
import com.launcher.log.LogManager;
import com.launcher.log.Logger;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        LOGGER.info("正在启动 Non-existent Minecraft Launcher (TUI)...");
        
        LauncherEngine engine = new LauncherEngine();
        engine.initializeEnvironment();
        
        LOGGER.info("引擎启动完成，等待后续 TUI 界面接入...");
    }
}
