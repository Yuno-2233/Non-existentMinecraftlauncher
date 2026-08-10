package com.github.yuno2233.neml;

import com.github.yuno2233.neml.log.NemlLogger;
import com.github.yuno2233.neml.mod.ModLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Launcher {
    private static final Logger log = NemlLogger.getEngineLogger();

    public static void main(String[] args) {
        String logLevel = System.getenv("NEML_LOG_LEVEL");
        Level level = Level.INFO;
        if (logLevel != null) {
            try { level = Level.parse(logLevel.toUpperCase()); } catch (IllegalArgumentException ignored) {}
        }
        NemlLogger.init(level);

        if (args.length < 1) {
            System.out.println("用法: neml <modid> [参数...]");
            System.exit(1);
        }

        String modId = args[0];
        String[] cmdArgs = new String[args.length - 1];
        System.arraycopy(args, 1, cmdArgs, 0, cmdArgs.length);

        ModLoader loader = new ModLoader();
        try {
            loader.discoverMods();
            loader.loadMods(modId);
            loader.executeCommand(modId, cmdArgs);
        } catch (Exception e) {
            log.log(Level.SEVERE, "执行失败: " + e.getMessage(), e);
            System.exit(1);
        }
    }
}
