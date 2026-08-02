const fs = require('fs');
const path = require('path');
const os = require('os');

class Environment {
    constructor() {
        // 定义启动器的工作目录 (放在用户主目录下)
        this.workDir = path.join(os.homedir(), 'non_existent_mc');
        
        // 定义需要自动创建的子目录
        this.dirs = {
            mods: path.join(this.workDir, 'mods'),
            config: path.join(this.workDir, 'config'),
            logs: path.join(this.workDir, 'logs'),
            versions: path.join(this.workDir, 'versions')
        };
    }

    // 初始化环境：检查并创建所有必要的目录
    init() {
        console.log('[Environment] Initializing workspace...');
        
        // 1. 创建主工作目录
        if (!fs.existsSync(this.workDir)) {
            fs.mkdirSync(this.workDir, { recursive: true });
            console.log(`[Environment] Created workspace: ${this.workDir}`);
        }

        // 2. 遍历并创建所有子目录
        for (const [name, dirPath] of Object.entries(this.dirs)) {
            if (!fs.existsSync(dirPath)) {
                fs.mkdirSync(dirPath, { recursive: true });
                console.log(`[Environment] Created directory: ${name}`);
            }
        }
        console.log('[Environment] Workspace ready!');
    }

    // 提供给 Mod 调用的获取路径方法
    getPath(dirName) {
        return this.dirs[dirName] || this.workDir;
    }
}

module.exports = new Environment();
