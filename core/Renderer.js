const Logger = require('./Logger');

class Renderer {
    constructor(engine) {
        // 【修复点】确保 engine 是有效的 EventEmitter 实例
        if (!engine || typeof engine.on !== 'function') {
            throw new Error('Renderer requires a valid Engine instance (EventEmitter)');
        }
        this.engine = engine;
        
        // 绑定事件时，注意参数顺序：事件名 -> 回调函数
        // 使用箭头函数或 bind 确保 this 指向 Renderer 实例
        this.engine.on('render_request', (data) => this.render(data));
        this.engine.on('clear_screen', () => process.stdout.write('\x1B[2J\x1B[H'));
    }

    render(data) {
        if (!data) return;
        
        // 简单的清屏重绘逻辑
        process.stdout.write('\x1B[H'); // 光标归位
        
        if (data.type === 'fallback_ui') {
            this.drawBox(data.content);
        } else {
            console.log(data.content);
        }
    }

    drawBox(lines) {
        const width = 50; // 固定宽度或动态计算
        const border = '+'.padEnd(width + 2, '-').slice(0, -1) + '+';
        
        console.log(border);
        lines.forEach(line => {
            // 简单的居中或左对齐处理
            const padded = line.padEnd(width).slice(0, width);
            console.log(`| ${padded}|`);
        });
        console.log(border);
    }
}

module.exports = Renderer;
