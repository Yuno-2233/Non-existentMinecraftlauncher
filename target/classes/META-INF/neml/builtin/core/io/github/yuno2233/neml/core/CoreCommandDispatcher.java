package io.github.yuno2233.neml.core;

import com.github.yuno2233.neml.api.CommandProvider;
import com.github.yuno2233.neml.mod.ModCandidate;
import com.github.yuno2233.neml.mod.ModLoader;
import com.github.yuno2233.neml.log.NemlLogger;

import java.util.*;

public class CoreCommandDispatcher implements CommandProvider {
    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            printHelp(new String[0]);
            return;
        }
        switch (args[0]) {
            case "list":
                listMods();
                break;
            case "reload":
                reloadMods();
                break;
            case "help":
                String[] helpArgs = new String[args.length - 1];
                System.arraycopy(args, 1, helpArgs, 0, helpArgs.length);
                printHelp(helpArgs);
                break;
            default:
                System.out.println("未知命令: " + args[0] + "，输入 'neml core help' 查看帮助");
        }
    }

    private void listMods() {
        ModLoader loader = ModLoader.getCurrentInstance();
        if (loader == null) {
            System.out.println("引擎未初始化");
            return;
        }
        Map<String, ModCandidate> mods = loader.getCandidateMap();
        if (mods.isEmpty()) {
            System.out.println("没有发现任何 mod");
            return;
        }
        System.out.println("已发现的 mod:");
        for (ModCandidate mod : mods.values()) {
            System.out.printf("  - %s (%s) [%s]\n",
                    mod.getId(),
                    mod.getMetadata().getVersion(),
                    mod.getSource());
        }
    }

    private void reloadMods() {
        ModLoader loader = ModLoader.getCurrentInstance();
        if (loader == null) {
            System.out.println("引擎未初始化");
            return;
        }
        try {
            NemlLogger.reload(); // 重新加载日志配置
            loader.reset();
            loader.discoverMods();
            System.out.println("已重新扫描 mod。");
            listMods();
        } catch (Exception e) {
            System.out.println("重新加载失败: " + e.getMessage());
        }
    }

    /**
     * 分级帮助：
     *   - 无参数：列出所有 mod 及其简介
     *   - 一个参数：列出指定 mod 的所有命令
     *   - 两个参数：显示指定命令的详细用法
     */
    private void printHelp(String[] path) {
        ModLoader loader = ModLoader.getCurrentInstance();
        if (loader == null) {
            System.out.println("引擎未初始化");
            return;
        }
        Map<String, ModCandidate> mods = loader.getCandidateMap();

        if (path.length == 0) {
            // 顶层：所有 mod
            System.out.println("可用 Mod：");
            for (ModCandidate mod : mods.values()) {
                String desc = mod.getMetadata().getDescription();
                System.out.printf("  %-15s - %s\n", mod.getId(), desc.isEmpty() ? "(无简介)" : desc);
            }
            System.out.println("\n输入 'neml core help <modid>' 查看该 Mod 的命令。");
        } else if (path.length == 1) {
            // 查看某个 mod 的命令列表
            String modId = path[0];
            ModCandidate mod = mods.get(modId);
            if (mod == null) {
                System.out.println("未找到 Mod: " + modId);
                return;
            }
            Map<String, Object> commands = getCommandsMap(mod);
            if (commands.isEmpty()) {
                System.out.println("Mod " + modId + " 没有提供命令。");
            } else {
                System.out.println("Mod " + modId + " 可用命令：");
                for (Map.Entry<String, Object> entry : commands.entrySet()) {
                    String cmdName = entry.getKey();
                    Object cmdInfo = entry.getValue();
                    String brief = "";
                    if (cmdInfo instanceof String) {
                        brief = (String) cmdInfo;
                    } else if (cmdInfo instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> infoMap = (Map<String, String>) cmdInfo;
                        brief = infoMap.getOrDefault("description", "");
                    }
                    System.out.printf("  %-25s - %s\n", cmdName, brief);
                }
            }
            System.out.println("\n输入 'neml core help " + modId + " <命令>' 查看具体命令的详细用法。");
        } else if (path.length >= 2) {
            // 查看具体命令的详细帮助
            String modId = path[0];
            String cmdName = path[1];
            ModCandidate mod = mods.get(modId);
            if (mod == null) {
                System.out.println("未找到 Mod: " + modId);
                return;
            }
            Map<String, Object> commands = getCommandsMap(mod);
            Object cmdInfo = commands.get(cmdName);
            if (cmdInfo == null) {
                System.out.println("命令 " + cmdName + " 不存在于 Mod " + modId);
                return;
            }
            System.out.println("命令: neml " + modId + " " + cmdName);
            if (cmdInfo instanceof String) {
                System.out.println("描述: " + cmdInfo);
                System.out.println("用法: neml " + modId + " " + cmdName + " [参数...]");
            } else if (cmdInfo instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> infoMap = (Map<String, Object>) cmdInfo;
                if (infoMap.containsKey("description")) {
                    System.out.println("描述: " + infoMap.get("description"));
                }
                if (infoMap.containsKey("usage")) {
                    System.out.println("用法: " + infoMap.get("usage"));
                } else {
                    System.out.println("用法: neml " + modId + " " + cmdName + " [参数...]");
                }
                if (infoMap.containsKey("args")) {
                    System.out.println("参数说明:");
                    Object argsObj = infoMap.get("args");
                    if (argsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> argsList = (List<Map<String, String>>) argsObj;
                        for (Map<String, String> arg : argsList) {
                            String argName = arg.getOrDefault("name", "");
                            String argDesc = arg.getOrDefault("description", "");
                            boolean optional = Boolean.parseBoolean(arg.getOrDefault("optional", "false"));
                            System.out.printf("  %-20s %s %s\n", argName, optional ? "(可选)" : "(必填)", argDesc);
                        }
                    }
                }
            }
        }
    }

    /**
     * 从 Mod 元数据中获取命令映射，兼容字符串和对象
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getCommandsMap(ModCandidate mod) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> rawCommands = mod.getMetadata().getCommandsRaw();
        if (rawCommands != null) {
            result.putAll(rawCommands);
        }
        return result;
    }
}