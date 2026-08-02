const fs = require('fs');
const path = require('path');

class I18n {
    constructor(locale) {
        this.currentLocale = locale || 'zh-CN';
        
        this.translations = {
            'zh-CN': {
                'app.title': '不存在的 Minecraft 启动器',
                'app.version': '引擎版本: 1.0.0',
                'menu.switch_lang': '切换语言',
                'menu.quit': '退出引擎',
                'status.loaded_mods': (count) => `已加载 ${count} 个模组` 
            },
            'en-US': {
                'app.title': 'Non-existent MC Launcher',
                'app.version': 'Engine Version: 1.0.0',
                'menu.switch_lang': 'Switch Language',
                'menu.quit': 'Quit Engine',
                'status.loaded_mods': (count) => `Loaded ${count} mods`
            }
        };
        this.loadExternalLocales();
    }

    loadExternalLocales() {
        const localesDir = path.join(__dirname, '..', 'locales');
        if (fs.existsSync(localesDir)) {
            const files = fs.readdirSync(localesDir);
            files.forEach(file => {
                if (file.endsWith('.json')) {
                    try {
                        const langCode = file.replace('.json', '');
                        const content = JSON.parse(fs.readFileSync(path.join(localesDir, file), 'utf8'));
                        this.translations[langCode] = { ...this.translations[langCode], ...content };
                    } catch (e) {
                        console.error(`Failed to load locale ${file}:`, e);
                    }
                }
            });
        }
    }

    setLocale(locale) {
        if (this.translations[locale]) {
            this.currentLocale = locale;
        } else {
            console.warn(`Locale ${locale} not found, falling back to zh-CN`);
            this.currentLocale = 'zh-CN';
        }
    }

    toggleLocale() {
        const supported = Object.keys(this.translations);
        const currentIndex = supported.indexOf(this.currentLocale);
        const nextIndex = (currentIndex + 1) % supported.length;
        this.setLocale(supported[nextIndex]);
    }

    // 【关键修复】补上了这个方法
    getLocale() {
        return this.currentLocale;
    }

    t(key, ...args) {
        const translation = this.translations[this.currentLocale]?.[key];
        if (!translation) {
            const fallback = this.translations['zh-CN']?.[key];
            return fallback || key;
        }
        if (typeof translation === 'function') {
            return translation(...args);
        }
        return translation;
    }
}

module.exports = I18n;
