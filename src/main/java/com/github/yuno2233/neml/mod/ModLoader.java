package com.github.yuno2233.neml.mod;

import com.github.yuno2233.neml.api.CommandProvider;
import com.github.yuno2233.neml.api.ModInitializer;
import com.github.yuno2233.neml.log.NemlLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModLoader {
    private static final Logger log = NemlLogger.getEngineLogger();
    private static final Path EXTERNAL_MOD_DIR = Paths.get("neml", "mod");

    private final Map<String, ModCandidate> candidateMap = new HashMap<>();
    private final List<ModCandidate> loadedMods = new ArrayList<>();
    private Map<String, ClassLoader> classLoaders;

    public void discoverMods() throws Exception {
        currentInstance = this;
        candidateMap.clear();
        scanBuiltinMods();
        scanExternalMods();
        if (candidateMap.isEmpty()) {
            log.warning("未发现任何 mod");
        }
    }

    private void scanBuiltinMods() throws Exception {
        ClassLoader cl = getClass().getClassLoader();
        if (cl == null) return;

        // 扫描 META-INF/neml/builtin 目录下的所有子目录（每个子目录是一个 mod）
        Enumeration<URL> baseDirs = cl.getResources("META-INF/neml/builtin");
        while (baseDirs.hasMoreElements()) {
            URL baseUrl = baseDirs.nextElement();
            String protocol = baseUrl.getProtocol();

            if ("file".equals(protocol)) {
                // 文件系统（开发时 IDE 或 target/classes）
                Path basePath = Paths.get(baseUrl.toURI());
                try (Stream<Path> modDirs = Files.list(basePath)) {
                    modDirs.filter(Files::isDirectory).forEach(modDir -> {
                        Path metaFile = modDir.resolve("neml-mod.json");
                        if (Files.exists(metaFile)) {
                            try {
                                String content = Files.readString(metaFile);
                                addBuiltinMod(content);
                            } catch (Exception e) {
                                log.warning("读取内置 mod 失败: " + modDir + " - " + e.getMessage());
                            }
                        }
                    });
                }
            } else if ("jar".equals(protocol)) {
                // 打包后的 jar 文件
                String jarPath = baseUrl.getPath().substring(5, baseUrl.getPath().indexOf("!"));
                try (JarFile jarFile = new JarFile(jarPath)) {
                    // 收集所有位于 META-INF/neml/builtin/*/neml-mod.json 的条目
                    Set<String> processedIds = new HashSet<>();
                    var entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith("META-INF/neml/builtin/") && name.endsWith("/neml-mod.json")) {
                            // 提取 mod id（即目录名）
                            int start = "META-INF/neml/builtin/".length();
                            int end = name.lastIndexOf("/neml-mod.json");
                            String modId = name.substring(start, end);
                            if (!processedIds.contains(modId)) {
                                processedIds.add(modId);
                                try (var in = jarFile.getInputStream(entry)) {
                                    String content = new BufferedReader(new InputStreamReader(in))
                                            .lines().collect(Collectors.joining("\n"));
                                    addBuiltinMod(content);
                                } catch (Exception e) {
                                    log.warning("读取内置 mod 失败: " + modId + " - " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void addBuiltinMod(String jsonContent) throws Exception {
        ModMetadata meta = SimpleJsonParser.parseModMetadata(jsonContent);
        ModCandidate candidate = new ModCandidate(null, true);
        candidate.setMetadata(meta);
        candidateMap.put(candidate.getId(), candidate);
        log.info("发现内置 mod: " + candidate.getId());
    }

    private void scanExternalMods() throws Exception {
        if (!Files.exists(EXTERNAL_MOD_DIR)) {
            Files.createDirectories(EXTERNAL_MOD_DIR);
            return;
        }
        try (Stream<Path> files = Files.list(EXTERNAL_MOD_DIR)) {
            files.filter(p -> p.toString().endsWith(".jar"))
                    .forEach(jarPath -> {
                        try {
                            ModCandidate candidate = new ModCandidate(jarPath, false);
                            parseMetadataFromJar(candidate);
                            candidateMap.put(candidate.getId(), candidate);
                        } catch (Exception e) {
                            log.warning("无法加载外部 mod: " + jarPath + " - " + e.getMessage());
                        }
                    });
        }
    }

    private void parseMetadataFromJar(ModCandidate candidate) throws Exception {
        try (JarFile jar = new JarFile(candidate.getJarPath().toFile())) {
            var entry = jar.getJarEntry("neml-mod.json");
            if (entry == null) throw new Exception("未找到 neml-mod.json");
            try (var reader = new BufferedReader(new InputStreamReader(jar.getInputStream(entry)))) {
                String content = reader.lines().collect(Collectors.joining("\n"));
                ModMetadata meta = SimpleJsonParser.parseModMetadata(content);
                candidate.setMetadata(meta);
            }
        }
    }

    public void loadMods(String targetModId) throws Exception {
        List<ModCandidate> toLoad = DependencyResolver.resolve(
                new ArrayList<>(candidateMap.values()), targetModId);

        classLoaders = ClassLoaderBuilder.build(toLoad);

        for (ModCandidate mod : toLoad) {
            List<String> mainClasses = mod.getMetadata().getEntrypoints().get("main");
            if (mainClasses != null) {
                ClassLoader loader = classLoaders.get(mod.getId());
                for (String className : mainClasses) {
                    try {
                        Class<?> clazz = loader.loadClass(className);
                        ModInitializer initializer = (ModInitializer) clazz.getDeclaredConstructor().newInstance();
                        initializer.onInitialize();
                        log.info("初始化 main 入口: " + mod.getId() + " -> " + className);
                    } catch (Exception e) {
                        log.severe("无法初始化 main 入口 " + className + " in mod " + mod.getId() + ": " + e.getMessage());
                        throw e;
                    }
                }
            }
        }
        loadedMods.addAll(toLoad);
    }

    public void executeCommand(String modId, String[] args) throws Exception {
        ModCandidate target = candidateMap.get(modId);
        if (target == null) throw new IllegalArgumentException("未找到 mod: " + modId);
        if (!loadedMods.stream().anyMatch(m -> m.getId().equals(modId))) {
            throw new IllegalStateException("Mod " + modId + " 尚未被加载，无法执行命令");
        }

        List<String> commandClasses = target.getMetadata().getEntrypoints().get("command");
        if (commandClasses == null || commandClasses.isEmpty()) {
            throw new IllegalStateException("Mod " + modId + " 没有提供 command 入口点");
        }

        ClassLoader loader = classLoaders.get(modId);
        for (String className : commandClasses) {
            Class<?> clazz = loader.loadClass(className);
            CommandProvider command = (CommandProvider) clazz.getDeclaredConstructor().newInstance();
            command.execute(args);
        }
    }

    public List<ModCandidate> getLoadedMods() { return Collections.unmodifiableList(loadedMods); }
    public Map<String, ModCandidate> getCandidateMap() { return Collections.unmodifiableMap(candidateMap); }
    
    // 静态实例（单次运行中唯一）
    private static ModLoader currentInstance;

    public static ModLoader getCurrentInstance() {
        return currentInstance;
    }

    public void reset() {
        candidateMap.clear();
        loadedMods.clear();
        classLoaders = null;
    }
}