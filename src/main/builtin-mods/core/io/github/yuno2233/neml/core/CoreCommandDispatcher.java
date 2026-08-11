package io.github.yuno2233.neml.core;

import com.github.yuno2233.neml.api.CommandProvider;
import com.github.yuno2233.neml.mod.ModCandidate;
import com.github.yuno2233.neml.mod.ModLoader;

import java.util.Map;

public class CoreCommandDispatcher implements CommandProvider {
    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            printHelp(null);
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
                String filter = args.length > 1 ? args[1] : null;
                printHelp(filter);
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
            loader.reset();
            loader.discoverMods();
            System.out.println("已重新扫描 mod。");
            listMods();
        } catch (Exception e) {
            System.out.println("重新加载失败: " + e.getMessage());
        }
    }

    private void printHelp(String filterModId) {
        ModLoader loader = ModLoader.getCurrentInstance();
        if (loader == null) {
            System.out.println("引擎未初始化");
            return;
        }
        Map<String, ModCandidate> mods = loader.getCandidateMap();

        if (filterModId != null) {
            ModCandidate target = mods.get(filterModId);
            if (target == null) {
                System.out.println("未找到 mod: " + filterModId);
                return;
            }
            System.out.println("Mod: " + filterModId + " 可用命令：");
            printCommandsForMod(target);
        } else {
            System.out.println("可用命令：");
            for (ModCandidate mod : mods.values()) {
                printCommandsForMod(mod);
            }
        }
    }

    private void printCommandsForMod(ModCandidate mod) {
        Map<String, String> commands = mod.getMetadata().getCommands();
        if (commands.isEmpty()) return;
        System.out.println(" [" + mod.getId() + "]");
        for (Map.Entry<String, String> entry : commands.entrySet()) {
            System.out.printf("   %-25s - %s\n", entry.getKey(), entry.getValue());
        }
    }
}