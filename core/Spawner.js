const { spawn } = require('child_process');
const engine = require('./Engine');
class Spawner {
    static run(command, args = [], options = {}) {
        const child = spawn(command, args, options);
        child.stdout.on('data', (data) => engine.emit('log', data.toString()));
        child.stderr.on('data', (data) => engine.emit('log', `[ERROR] ${data.toString()}`));
        child.on('close', (code) => engine.emit('process_exit', code));
        return child;
    }
}
module.exports = Spawner;
