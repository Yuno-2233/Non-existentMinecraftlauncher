package com.launcher.core;

import com.launcher.mod.ModLoader;
import java.io.File;
import java.net.URISyntaxException;

public class LauncherEngine {
    
    private ModLoader modLoader;
    private EventBus eventBus; // 假设你有一个 EventBus 单例或在这里管理

    public void start() {
        System.out.println("[Engine] 正在启动 Non-existent Minecraft Launcher...");
        
        // 1. 初始化事件总线
        this.eventBus = new EventBus(); // 或者 EventBus.getInstance()

        // 2. 初始化 Mod 加载器
        this.modLoader = new ModLoader(eventBus);

        // 3. 加载外置 Mod
        File modsDir = new File("mods");
        if (modsDir.exists()) {
            modLoader.loadMods(modsDir);
        }

        // 4. 加载内置 Mod (关键：将 TuiMod 的实例传给加载器)
        // 这样 ModLoader 就能通过反射读取它的 @LauncherMod 注解并注册事件
        modLoader.loadBuiltinMods(new com.launcher.builtin.tui.TuiMod());

        // 5. 触发初始化事件，所有监听了 InitializeEvent 的 Mod (包括 TuiMod) 都会被调用
        eventBus.post(new InitializeEvent());

        System.out.println("[Engine] 引擎准备就绪");
    }
}
