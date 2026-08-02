const I18n = require('./I18n');
const Components = require('./Components');
const Logger = require('./Logger');
const Environment = require('./Environment');

class Engine {
    constructor() {
        // 初始化环境和日志系统
        Environment.init();
        Logger.init(Environment);
        Logger.info('Engine initializing...');

        // 初始化国际化模块，不传参数，使用默认语言
        this.i18n = new I18n();
        
        this.selectedIndex = 0;
        this.modCount = 0; // 这里可以替换成真实的模组扫描逻辑
    }

    start() {
        Logger.info('Engine started.');
        this.setupInput();
        this.render();
    }

    setupInput() {
        process.stdin.setRawMode(true);
        process.stdin.resume();
        process.stdin.setEncoding('utf8');
        process.stdout.write('\x1B[?25l'); // 隐藏光标

        process.stdin.on('data', (key) => {
            // 处理 Ctrl+C 退出
            if (key === '\u0003') {
                this.shutdown();
            }

            // 上下箭头导航
            if (key === '\u001b[A') this.moveCursor(-1); // Up
            if (key === '\u001b[B') this.moveCursor(1);  // Down
            
            // 回车键确认
            if (key === '\r' || key === '\n') {
                this.handleSelection();
            }
        });
    }

    moveCursor(direction) {
        const items = this.getMenuItems();
        this.selectedIndex += direction;
        
        // 循环滚动逻辑
        if (this.selectedIndex < 0) this.selectedIndex = items.length - 1;
        if (this.selectedIndex >= items.length) this.selectedIndex = 0;
        
        this.render();
    }

    handleSelection() {
        const items = this.getMenuItems();
        const selected = items[this.selectedIndex];

        if (selected.action === 'quit') {
            this.shutdown();
        } 
        
        if (selected.action === 'switch_lang') {
            // 【核心修复】使用 toggleLocale 方法，逻辑更清晰
            this.i18n.toggleLocale();
            Logger.info(`Language switched to: ${this.i18n.getLocale()}`);

            this.render(); // 重新渲染界面
        }
    }

    shutdown() {
        Logger.info('Shutting down...');
        process.stdout.write('\x1B[?25h'); // 显示光标
        Logger.shutdown();
        process.exit(0);
    }

    getMenuItems() {
        return [
            { label: this.i18n.t('menu.switch_lang'), action: 'switch_lang' },
            { label: this.i18n.t('menu.quit'), action: 'quit' }
        ];
    }

    render() {
        // 清屏
        process.stdout.write('\x1B[2J\x1B[H');

        const width = 50;
        const title = this.i18n.t('app.title');
        const version = this.i18n.t('app.version');
        const status = this.i18n.t('status.loaded_mods', this.modCount);

        // 使用 Components 绘制漂亮的盒子
        const boxContent = [
            version,
            '',
            ...Components.MenuList(this.getMenuItems(), this.selectedIndex + 1),
            '',
            status
        ];

        console.log(Components.Box(title, boxContent));
    }
}

module.exports = Engine;
