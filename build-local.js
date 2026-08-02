const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const os = require('os');

// 1. 检测当前操作系统
const platform = os.platform();
let target, iconFile;

// 【修复点】Termux 环境会被识别为 android，这里将其作为 linux 处理
if (platform === 'win32') {
    target = 'node20-win-x64';
    iconFile = 'icon.ico';
} else if (platform === 'linux' || platform === 'android') {
    target = 'node20-linux-x64';
    iconFile = 'icon.png';
} else if (platform === 'darwin') {
    target = 'node20-macos-x64';
    iconFile = 'icon.png';
} else {
    console.error('Unsupported platform:', platform);
    process.exit(1);
}

// 2. 检查图标文件是否存在
if (!fs.existsSync(iconFile)) {
    console.warn(`Warning: Icon file '${iconFile}' not found. Building without icon.`);
    iconFile = '';
} else {
    iconFile = `--icon ${iconFile}`;
}

// 3. 确保 pkg 已安装
try {
    execSync('pkg --version', { stdio: 'ignore' });
} catch (e) {
    console.log('pkg not found, installing globally...');
    execSync('npm install -g @yao-pkg/pkg', { stdio: 'inherit' });
}

// 4. 创建输出目录
const distDir = 'dist';
if (!fs.existsSync(distDir)) {
    fs.mkdirSync(distDir);
}

// 5. 执行打包命令
const outputFile = path.join(distDir, 'Non-existent-MC-Launcher');
const command = `pkg . --targets ${target} --output ${outputFile} ${iconFile}`;

console.log('Building executable...');
console.log('Command:', command);

try {
    execSync(command, { stdio: 'inherit' });
    console.log('\nBuild successful!');
    console.log(`Output: ${outputFile}`);
} catch (e) {
    console.error('\nBuild failed!');
    process.exit(1);
}
