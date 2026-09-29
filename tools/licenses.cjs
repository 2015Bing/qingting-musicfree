const fs=require('node:fs'),path=require('node:path');
const lock=JSON.parse(fs.readFileSync('package-lock.json','utf8'));
let output='轻听 JavaScript runtime — third-party notices\n\n';
for(const [dir,entry] of Object.entries(lock.packages)){
  if(!dir||entry.dev)continue;
  const pkg=JSON.parse(fs.readFileSync(path.join(dir,'package.json'),'utf8'));
  output+=`\n===== ${pkg.name} ${pkg.version} (${pkg.license || 'see license'}) =====\n`;
  const license=fs.readdirSync(dir).find(f=>/^licen[sc]e(\.|$)/i.test(f));
  if(license)output+=fs.readFileSync(path.join(dir,license),'utf8')+'\n';
}
fs.writeFileSync('app/src/main/assets/THIRD_PARTY_NOTICES.txt',output);
