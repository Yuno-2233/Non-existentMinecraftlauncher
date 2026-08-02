package com.launcher.mod;

import com.launcher.core.EventBus;
import com.launcher.core.SubscribeEvent;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ModLoader {
    
    private final List<ModContainer> loadedMods = new ArrayList<>();
    private final EventBus eventBus;

    public ModLoader(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 扫描指定目录下的所有 jar 文件并加载 Mod
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
                                
                                // 检查是否是 Mod 主类
                                if (clazz.isAnnotationPresent(LauncherMod.class)) {
                                    LauncherMod annotation = clazz.getAnnotation(LauncherMod.class);
                                    Object modInstance = clazz.getDeclaredConstructor().newInstance();
                                    loadedMods.add(new ModContainer(annotation.value(), modInstance));
                                    System.out.println("✔ 成功加载 Mod: " + annotation.value());
                                    
                                    // 直接将 Mod 实例注册到事件总线
                                    eventBus.register(modInstance);
                                }
                            } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                                // 忽略非 Mod 的普通类或依赖缺失的类
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("✘ 加载 Mod 失败: " + jarFile.getName());
                e.printStackTrace();
            }
        }
    }

    public List<ModContainer> getLoadedMods() {
        return loadedMods;
    }
}
