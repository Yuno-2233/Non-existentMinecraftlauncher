const Engine = require('./core/Engine');

// 模拟一些 Mod 数据用于测试
const mockMods = [
    { name: "OptiFine HD U H9" },
    { name: "Sodium 0.5.2" }
];

// 初始化引擎
const engine = new Engine({
    locale: 'en-US',      // 默认语言
    mods: mockMods        // 传入 Mod 列表
});

// 启动
try {
    engine.start();
} catch (err) {
    console.error('Failed to start engine:', err);
    process.exit(1);
}
