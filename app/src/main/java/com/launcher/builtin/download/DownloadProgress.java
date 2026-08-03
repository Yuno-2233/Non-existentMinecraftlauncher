package com.launcher.builtin.download;

public class DownloadProgress {
    private final long bytesDownloaded;
    private final long totalBytes;
    private final double percent;

    public DownloadProgress(long bytesDownloaded, long totalBytes) {
        this.bytesDownloaded = bytesDownloaded;
        this.totalBytes = totalBytes;
        this.percent = totalBytes > 0 ? (double) bytesDownloaded / totalBytes * 100.0 : 0.0;
    }

    public long getBytesDownloaded() { return bytesDownloaded; }
    public long getTotalBytes() { return totalBytes; }
    public double getPercent() { return percent; }
}
