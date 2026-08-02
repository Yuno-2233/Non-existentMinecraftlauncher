const https = require('https');

class Fetcher {
    static get(url) {
        return new Promise((resolve, reject) => {
            https.get(url, (res) => {
                let data = '';
                res.on('data', (chunk) => data += chunk);
                res.on('end', () => {
                    try { resolve(JSON.parse(data)); } 
                    catch (e) { reject(e); }
                });
            }).on('error', reject);
        });
    }
}
module.exports = Fetcher;
