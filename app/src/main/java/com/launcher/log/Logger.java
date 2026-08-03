// Logger.java
package com.launcher.log;

public class Logger {
    // 使用全限定名，避免与当前类名冲突
    private final org.slf4j.Logger logger;

    public Logger(String tag) {
        this.logger = org.slf4j.LoggerFactory.getLogger(tag);
    }

    public void info(String message) {
        logger.info(message);
    }

    public void warn(String message) {
        logger.warn(message);
    }

    public void error(String message) {
        logger.error(message);
    }

    public void debug(String message) {
        logger.debug(message);
    }
}
