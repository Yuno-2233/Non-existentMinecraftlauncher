package com.launcher.builtin.download;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface IDownloadService {
    CompletableFuture<DownloadResult> download(String url, String savePath, Consumer<DownloadProgress> onProgress);
}
