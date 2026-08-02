const fs = require('fs');
const path = require('path');
class Config {
    constructor(configName = 'launcher_config.json') {
        this.configPath = path.join(__dirname, '..', configName);
        this.data = {};
        this.load();
    }
    load() {
        try {
            if (fs.existsSync(this.configPath)) {
                this.data = JSON.parse(fs.readFileSync(this.configPath, 'utf-8'));
            }
        } catch (err) { console.error('[Config] Load failed:', err.message); }
    }
    get(key, defaultValue = null) { return this.data[key] !== undefined ? this.data[key] : defaultValue; }
    set(key, value) {
        this.data[key] = value;
        fs.writeFileSync(this.configPath, JSON.stringify(this.data, null, 2), 'utf-8');
    }
}
module.exports = new Config();
