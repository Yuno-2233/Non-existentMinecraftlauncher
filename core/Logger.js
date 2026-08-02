const fs = require('fs');
const path = require('path');

class Logger {
    constructor() {
        this.logDir = null;
        this.logFile = null;
        this.logStream = null;
        this.initialized = false;
        this.consoleOutput = false; // 默认关闭终端输出
    }

    init(environment) {
        if (this.initialized) return;

        try {
            // 从 Environment 获取日志目录
            this.logDir = environment.getPath('logs');

            // 确保日志目录存在
            if (!fs.existsSync(this.logDir)) {
                fs.mkdirSync(this.logDir, { recursive: true });
            }

            // 生成带时间戳的日志文件名
            const now = new Date();
            const timestamp = now.toISOString().replace(/[:.]/g, '-').slice(0, -5);
            const fileName = `launcher_${timestamp}.log`;
            this.logFile = path.join(this.logDir, fileName);

            // 创建写入流
            this.logStream = fs.createWriteStream(this.logFile, { flags: 'a' });

            // 写入启动信息
            this._write('INFO', '========================================');
            this._write('INFO', 'Non-existent MC Launcher Started');
            this._write('INFO', `Time: ${now.toLocaleString()}`);
            this._write('INFO', `Log File: ${this.logFile}`);
            this._write('INFO', `Platform: ${process.platform} ${process.arch}`);
            this._write('INFO', `Node Version: ${process.version}`);
            this._write('INFO', '========================================');

            this.initialized = true;
        } catch (err) {
            console.error(`[Logger] Failed to initialize: ${err.message}`);
        }
    }

    _write(level, message) {
        const timestamp = new Date().toISOString();
        const logLine = `[${timestamp}] [${level}] ${message}\n`;

        // 写入文件
        if (this.logStream) {
            this.logStream.write(logLine);
        }

        // 只在开启控制台输出时才打印到终端
        if (this.consoleOutput) {
            let color = '\x1b[0m';
            if (level === 'ERROR') color = '\x1b[31m';
            else if (level === 'WARN') color = '\x1b[33m';
            else if (level === 'DEBUG') color = '\x1b[36m';
            else if (level === 'INFO') color = '\x1b[32m';

            process.stdout.write(`${color}[${level}] ${message}\x1b[0m\n`);
        }
    }

    info(message) { this._write('INFO', message); }
    warn(message) { this._write('WARN', message); }
    error(message) { this._write('ERROR', message); }
    debug(message) { this._write('DEBUG', message); }

    logModLoad(modName, success, error) {
        if (success) {
            this.info(`Mod loaded successfully: ${modName}`);
        } else {
            this.error(`Failed to load mod: ${modName} - ${error}`);
        }
    }

    logMenuChange(from, to) {
        this.info(`Menu changed: ${from || 'null'} -> ${to}`);
    }

    logKeyPress(key) {
        this.debug(`Key pressed: ${key}`);
    }

    logEvent(eventName, data) {
        this.debug(`Event triggered: ${eventName}${data ? ' - ' + JSON.stringify(data) : ''}`);
    }

    shutdown() {
        this._write('INFO', 'Launcher shutting down...');
        this._write('INFO', '========================================');
        if (this.logStream) {
            this.logStream.end();
        }
    }

    getLogFile() {
        return this.logFile;
    }
}

module.exports = new Logger();
