// Seller-side tool. Private delivery codes never belong in the APK or public OSS file.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const count = Number(process.argv[2] || 10);
const directory = path.resolve(process.argv[3] || 'licensing-private');
if (!Number.isInteger(count) || count < 1 || count > 10000) throw new Error('Count must be 1..10000');
fs.mkdirSync(directory, { recursive: true });
const publicPath = path.join(directory, 'licenses.json');
const privatePath = path.join(directory, 'delivery-codes.txt');
if (fs.existsSync(publicPath) || fs.existsSync(privatePath)) throw new Error('Output already exists; use a new directory to avoid losing existing codes.');
const codes = Array.from({ length: count }, () => 'QT-' + crypto.randomBytes(16).toString('hex').toUpperCase());
const hashes = codes.map(code => crypto.createHash('sha256').update(code, 'utf8').digest('hex'));
fs.writeFileSync(privatePath, codes.join('\n') + '\n', { flag: 'wx', mode: 0o600 });
fs.writeFileSync(publicPath, JSON.stringify({ version: 1, keyHashes: hashes }, null, 2) + '\n', { flag: 'wx' });
console.log(`Created ${count} codes. Upload only licenses.json. Keep delivery-codes.txt private. Output: ${directory}`);
