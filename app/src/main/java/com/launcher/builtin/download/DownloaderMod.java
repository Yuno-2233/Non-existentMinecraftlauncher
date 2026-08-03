package com.launcher.builtin.download;

import com.launcher.mod.ModEventBusSubscriber;
import com.launcher.core.InitializeEvent;
import com.launcher.log.LogManager;
import com.launcher.log.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@ModEventBusSubscriber
public class DownloaderMod implements IDownloadService {

    private static final Logger LOGGER = LogManager.getLogger("DownloaderMod");
    private JavaDownloadEngine engine;

    // 注意：这里移除了 @Override 注解，因为 DownloaderMod 不再实现 LauncherMod 接口
    // 但方法名保持不变，以便 @ModEventBusSubscriber 能够通过反射找到并调用它
    public void onInitialize(InitializeEvent event) {
        this.engine = new JavaDownloadEngine(3);
        LOGGER.info("Built-in Java Download Engine initialized.");
    }

    @Override
    public CompletableFuture<DownloadResult> download(String url, String savePath, Consumer<DownloadProgress> onProgress) {
        return CompletableFuture.supplyAsync(() -> {
            String taskId = engine.submitTask(url, savePath, onProgress);
            return new DownloadResult(true, savePath, taskId, "Download started");
        });
    }
}
