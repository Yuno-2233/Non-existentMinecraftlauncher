package com.launcher.builtin.download;

public class DownloadResult {
    private final boolean success;
    private final String filePath;
    private final String taskId;
    private final String message;

    public DownloadResult(boolean success, String filePath, String taskId, String message) {
        this.success = success;
        this.filePath = filePath;
        this.taskId = taskId;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getFilePath() { return filePath; }
    public String getTaskId() { return taskId; }
    public String getMessage() { return message; }
}
