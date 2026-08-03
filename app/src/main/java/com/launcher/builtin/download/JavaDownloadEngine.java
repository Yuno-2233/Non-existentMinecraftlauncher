package com.launcher.builtin.download;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class JavaDownloadEngine {
    private final ExecutorService workerPool;
    private final ConcurrentHashMap<String, AtomicBoolean> activeTasks = new ConcurrentHashMap<>();

    public JavaDownloadEngine(int maxThreads) {
        this.workerPool = Executors.newFixedThreadPool(maxThreads, r -> {
            Thread t = new Thread(r, "dl-worker-" + UUID.randomUUID().toString().substring(0, 4));
            t.setDaemon(true);
            return t;
        });
    }

    // ✅ 已修复：直接使用 DownloadProgress
    public String submitTask(String urlStr, String savePath, Consumer<DownloadProgress> progressConsumer) {
        String taskId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        activeTasks.put(taskId, cancelled);

        workerPool.submit(() -> {
            try {
                executeDownload(urlStr, savePath, taskId, cancelled, progressConsumer);
            } catch (Exception e) {
                if (!cancelled.get() && progressConsumer != null) {
                    progressConsumer.accept(new DownloadProgress(0, 0));
                }
            } finally {
                activeTasks.remove(taskId);
            }
        });
        return taskId;
    }

    private void executeDownload(String urlStr, String savePath, String taskId, AtomicBoolean cancelled,
                                 Consumer<DownloadProgress> consumer) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("HEAD");
        long totalLength = conn.getContentLengthLong();
        conn.disconnect();

        Files.createDirectories(Path.of(savePath).getParent());
        AtomicLong downloaded = new AtomicLong(0);
        downloadSingleThread(url, savePath, taskId, cancelled, downloaded, totalLength, consumer);
    }

    private void downloadSingleThread(URL url, String savePath, String taskId, AtomicBoolean cancelled,
                                      AtomicLong downloaded, long totalLength,
                                      Consumer<DownloadProgress> consumer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        File file = new File(savePath);

        long existingSize = file.exists() ? file.length() : 0;
        if (existingSize > 0 && existingSize < totalLength) {
            conn.setRequestProperty("Range", "bytes=" + existingSize + "-");
            downloaded.addAndGet(existingSize);
        }

        try (InputStream in = conn.getInputStream();
             RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(downloaded.get());
            byte[] buffer = new byte[8192];
            int len;
            long lastReport = System.currentTimeMillis();

            while ((len = in.read(buffer)) != -1) {
                if (cancelled.get()) throw new CancellationException("Task cancelled: " + taskId);
                raf.write(buffer, 0, len);
                downloaded.addAndGet(len);

                long now = System.currentTimeMillis();
                if (now - lastReport > 100 && consumer != null) {
                    consumer.accept(new DownloadProgress(downloaded.get(), totalLength));
                    lastReport = now;
                }
            }
            if (consumer != null) {
                consumer.accept(new DownloadProgress(downloaded.get(), totalLength));
            }
        } finally {
            conn.disconnect();
        }
    }

    public boolean cancel(String taskId) {
        AtomicBoolean flag = activeTasks.get(taskId);
        if (flag != null) { flag.set(true); return true; }
        return false;
    }

    public void shutdown() { workerPool.shutdownNow(); }
}
