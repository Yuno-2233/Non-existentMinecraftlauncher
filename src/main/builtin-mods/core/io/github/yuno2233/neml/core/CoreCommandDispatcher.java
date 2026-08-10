package io.github.yuno2233.neml.core;

import com.github.yuno2233.neml.api.CommandProvider;
import com.github.yuno2233.neml.mod.ModCandidate;
import com.github.yuno2233.neml.mod.ModLoader;

import java.util.Map;

public class CoreCommandDispatcher implements CommandProvider {
    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            printHelp();
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
                printHelp();
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

    private void printHelp() {
        System.out.println("NEML 引擎命令:");
        System.out.println("  neml core list     列出已发现的 mod");
        System.out.println("  neml core reload   重新扫描 mod 目录");
        System.out.println("  neml core help     显示此帮助");
    }
}