const path = require('path');

// 这是一个工具类，提供静态方法
class Components {
    // 绘制一个带边框的盒子
    static Box(title, lines) {
        const width = 50;
        const horizontalBorder = '─'.repeat(width - 2);
        let output = [];

        // 标题行
        output.push(`\x1b[36m┌${title}\x1b[90m${'─'.repeat(width - title.length - 2)}┐\x1b[0m`);

        // 内容行
        lines.forEach(line => {
            const padding = ' '.repeat(width - line.replace(/\x1b\[\d+m/g, '').length - 2);
            output.push(`\x1b[36m│\x1b[0m ${line}${padding} \x1b[36m│\x1b[0m`);
        });

        // 底部边框
        output.push(`\x1b[36m└${horizontalBorder}┘\x1b[0m`);

        return output.join('\n');
    }

    // 绘制搜索框
    static SearchBox(keyword) {
        const width = 46;
        const prompt = '\x1b[90mSearch:\x1b[0m ';
        const input = `\x1b[33m${keyword}\x1b[0m|`;
        const padding = ' '.repeat(width - prompt.length - keyword.length - 1);
        return `\x1b[36m│\x1b[0m ${prompt}${input}${padding}\x1b[36m│\x1b[0m`;
    }

    // 绘制菜单列表 (这是启动器核心需要的函数)
    static MenuList(items, selectedIndex) {
        return items.map((item, index) => {
            const isCurrent = index === selectedIndex - 1; // selectedIndex 是从 1 开始的
            const prefix = isCurrent ? '\x1b[33m>\x1b[0m' : ' ';
            const color = item.disabled ? '\x1b[90m' : (isCurrent ? '\x1b[33m' : '\x1b[37m');
            const label = item.disabled ? `[${item.label}]` : item.label;
            return ` ${prefix} ${color}${label}\x1b[0m`;
        });
    }
}

// 导出整个类
module.exports = Components;
