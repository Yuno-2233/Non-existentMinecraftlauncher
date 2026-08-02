package com.launcher.mod;

import com.launcher.core.EventBus;
import com.launcher.log.LogManager;
import com.launcher.log.Logger;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ModLoader {
    
    private static final Logger LOGGER = LogManager.getLogger(ModLoader.class);
    private final List<ModContainer> loadedMods = new ArrayList<>();
    private final EventBus eventBus;

    public ModLoader(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 加载内置 Mod
     */
    public void loadBuiltinMods(Object... builtinMods) {
        for (Object mod : builtinMods) {
            LauncherMod annotation = mod.getClass().getAnnotation(LauncherMod.class);
            if (annotation != null) {
                loadedMods.add(new ModContainer(annotation.value(), mod));
                eventBus.register(mod);
                LOGGER.info("✔ 成功加载内置 Mod: " + annotation.value());
            } else {
                LOGGER.warn("⚠ 对象 " + mod.getClass().getSimpleName() + " 缺少 @LauncherMod 注解，已跳过");
            }
        }
    }

    /**
     * 扫描指定目录下的所有 jar 文件并加载外部 Mod
     */
    public void loadMods(File modsDir) {
        if (!modsDir.exists() || !modsDir.isDirectory()) return;

        File[] jarFiles = modsDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null) return;

        for (File jarFile : jarFiles) {
            try {
                URL[] urls = { jarFile.toURI().toURL() };
                try (URLClassLoader classLoader = new URLClassLoader(urls, getClass().getClassLoader());
                     JarFile jar = new JarFile(jarFile)) {

                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (entry.getName().endsWith(".class")) {
                            String className = entry.getName()
                                    .replace("/", ".")
                                    .replace(".class", "");

                            try {
                                Class<?> clazz = classLoader.loadClass(className);
                                
                                if (clazz.isAnnotationPresent(LauncherMod.class)) {
                                    LauncherMod annotation = clazz.getAnnotation(LauncherMod.class);
                                    Object modInstance = clazz.getDeclaredConstructor().newInstance();
                                    loadedMods.add(new ModContainer(annotation.value(), modInstance));
                                    eventBus.register(modInstance);
                                    LOGGER.info("✔ 成功加载外部 Mod: " + annotation.value());
                                }
                            } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                                // 忽略非 Mod 的普通类或依赖缺失的类
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("✘ 加载外部 Mod 失败: " + jarFile.getName());
                e.printStackTrace();
            }
        }
    }

    public List<ModContainer> getLoadedMods() {
        return loadedMods;
    }
}
