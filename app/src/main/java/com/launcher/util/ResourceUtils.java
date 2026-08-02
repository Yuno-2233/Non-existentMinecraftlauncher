package com.launcher.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 资源释放工具类：用于将 JAR 包内的文件释放到外部目录
 */
public class ResourceUtils {

    /**
     * 将 JAR 内部的资源释放到外部文件系统
     * @param resourcePath JAR 内部的路径 (相对于 classpath，即 src/main/resources)
     * @param targetPath   外部目标路径 (例如 "./config/launcher.json")
     */
    public static void releaseResource(String resourcePath, String targetPath) {
        File targetFile = new File(targetPath);
        
        // 1. 如果外部文件已存在，则跳过，以保留用户的自定义配置
        if (targetFile.exists()) {
            return;
        }

        // 2. 确保目标父目录存在
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // 3. 从 ClassPath 读取并写入外部文件
        try (InputStream is = ResourceUtils.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("无法在 JAR 内找到资源: " + resourcePath);
                return;
            }
            Files.copy(is, targetFile.toPath());
            System.out.println("已释放资源: " + targetPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
