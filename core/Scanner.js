const fs = require('fs');
const path = require('path');
class Scanner {
    static scanFolders(dirPath) {
        if (!fs.existsSync(dirPath)) return [];
        return fs.readdirSync(dirPath).filter(item => fs.statSync(path.join(dirPath, item)).isDirectory()).map(item => path.join(dirPath, item));
    }
}
module.exports = Scanner;
