const readline = require('readline');
const engine = require('./Engine');
const Logger = require('./Logger');

const initInput = () => {
    readline.emitKeypressEvents(process.stdin);
    if (process.stdin.isTTY) process.stdin.setRawMode(true);

    process.stdin.on('keypress', (str, key) => {
        if (!key) return;

        let keyName = key.name;
        if (key.ctrl && key.name === 'c') {
            Logger.info('Ctrl+C pressed, shutting down');
            engine.shutdown();
            return;
        }
        if (key.ctrl) keyName = 'ctrl+' + keyName;
        if (key.shift && key.name.length > 1) keyName = 'shift+' + keyName;
        if (keyName === 'return') keyName = 'enter';

        Logger.logKeyPress(keyName);
        engine.safeEmit('key_press', keyName);
    });
};

module.exports = { initInput };
