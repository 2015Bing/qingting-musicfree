const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
test('chart capabilities require both methods and preserve raw arguments and paging',async()=>{
  const r=runtime(`module.exports={platform:'Charts',getTopLists(){return [{title:'推荐',data:[{id:7,title:'热歌',extra:'keep'}]}]},getTopListDetail(board,page){return {isEnd:page>=2,musicList:[{id:page,title:board.title,extra:board.extra}]}}}`);
  assert.equal((await r.call('metadata')).topLists,true);
  const board=(await r.call('getTopLists'))[0].data[0];
  const page=await r.call('getTopListDetail',[board,2]);assert.equal(page.isEnd,true);assert.equal(page.musicList[0].extra,'keep');assert.equal(page.musicList[0].id,2);
  assert.equal((await runtime(`module.exports={platform:'Partial',getTopLists(){return []}}`).call('metadata')).topLists,false);
});
function runtime(source, variables = {}, response = {status:200, data:'{"value":"ok"}', headers:{'content-type':'application/json'}}) {
  const results = new Map(); const requests = [];
  const context = vm.createContext({ console, setTimeout, clearTimeout, URL, URLSearchParams, TextEncoder, TextDecoder, atob,
    Native: { complete: (id, json) => results.set(id, JSON.parse(json)), request: (id, json) => {
      requests.push(JSON.parse(json));
      queueMicrotask(() => context.__httpResult(id, response));
    }} });
  vm.runInContext(fs.readFileSync('app/src/main/assets/runtime.js', 'utf8'), context);
  return { requests, setSource(value){source=value;}, async call(method, args = []) {
    const id = String(results.size + 1);
    await context.__invoke(id, source, variables, method, args);
    const result = results.get(id); if (result.error) throw new Error(result.error); return result.value;
  }};
}
test('local fixture supports lyric-only metadata and embedded media lyrics',async()=>{
  const fixture=fs.readFileSync('tools/fixture-server.cjs','utf8');
  const context=vm.createContext({require,Buffer});
  vm.runInContext(fixture.slice(0,fixture.indexOf('http.createServer')),context);
  const lyrics=runtime(vm.runInContext("plugin('Lyrics','lyrics')",context));
  assert.deepEqual((await lyrics.call('metadata')).supportedSearchType,['lyric']);
  const found=await lyrics.call('search',['晨间微风',1,'lyric']);
  assert.equal((await lyrics.call('getLyric',[found.data[0]])).rawLrc.split('\n')[0],'[00:00.00]专用歌词源补全');
  const music=runtime(vm.runInContext("plugin('Primary','primary')",context));
  assert.match((await music.call('getMediaSource',[{},'standard'])).url,/invalid$/);
  assert.equal((await music.call('metadata')).topLists,true);assert.equal((await music.call('getTopLists'))[0].data[0].token,'chart-token');
  const low=await music.call('getMediaSource',[{},'low']);
  assert.match(low.url,/tone\.wav$/);assert.equal(low.rawLrc.split('\n')[0],'[00:00.00]音源自带歌词');
});
test('async search preserves fields needed for media resolution', async () => {
  const r = runtime(`module.exports={platform:'Test',async search(q,p,t){return {isEnd:true,data:[{id:1,title:q,aid:17,page:p,type:t}]}},async getMediaSource(m){return {url:'https://example.test/'+m.aid}}}`);
  const result = await r.call('search', ['song', 1, 'music']);
  assert.equal(result.data[0].aid,17);
  assert.equal((await r.call('getMediaSource',[result.data[0],'standard'])).url,'https://example.test/17');
});
test('user variables and axios params flow through actual adapter', async () => {
  const r = runtime(`const axios=require('axios');module.exports={platform:'T',async search(q){ const r=await axios.get('https://example.test/search',{params:{q,token:env.getUserVariables().token}});return r.data;}}`,{token:'abc'});
  assert.equal((await r.call('search',['a b'])).value,'ok');
  assert.match(r.requests[0].url,/q=a\+b/); assert.match(r.requests[0].url,/token=abc/);
});
test('common dependencies execute; unsupported dependencies explain incompatibility', async () => {
  const r = runtime(`module.exports={platform:'T',async search(){return require('crypto-js').MD5('abc').toString()}}`);
  assert.equal(await r.call('search'), '900150983cd24fb0d6963f7d28e17f72');
  await assert.rejects(runtime(`require('unsupported');module.exports={}`).call('search'),/不支持的依赖/);
});
test('missing media method falls back to song URL and HTML parsing works', async () => {
  const r=runtime(`module.exports={platform:'T',search(){return require('cheerio').load('<b>hello</b>')('b').text()}}`);
  assert.equal(await r.call('search'),'hello');
  assert.equal((await r.call('getMediaSource',[{url:'https://example.test/a.mp3'}])).url,'https://example.test/a.mp3');
});
test('arraybuffer preserves non-UTF8 bytes and forwards responseType',async()=>{
  const r=runtime(`module.exports={platform:'T',async search(){const r=await require('axios').get('https://example.test/bin',{responseType:'arraybuffer'});return Array.from(new Uint8Array(r.data));}}`,{}, {status:200,data:'/wCA',dataEncoding:'base64',headers:{}});
  assert.deepEqual(await r.call('search'),[255,0,128]);assert.equal(r.requests[0].responseType,'arraybuffer');
});
test('source session retains module state between search and resolution',async()=>{
  const r=runtime(`let token;module.exports={platform:'Session',search(){token='session-token';return {data:[]}},getMediaSource(){if(!token)throw new Error('missing token');return {url:'https://example.test/'+token}}}`);
  await r.call('search');assert.equal((await r.call('getMediaSource',[{}])).url,'https://example.test/session-token');
});
test('invalid plugin update cannot poison retained working module',async()=>{
  const original=`module.exports={platform:'Working',search(){return 'works'}}`;
  const r=runtime(original);assert.equal(await r.call('search'),'works');
  r.setSource('module.exports={}');await assert.rejects(r.call('metadata'),/platform/);
  r.setSource(original);assert.equal(await r.call('search'),'works');
});
test('lyric API preserves original song keys, text and translation',async()=>{
  const r=runtime(`module.exports={platform:'Lyrics',async getLyric(song){return {rawLrc:'[00:01.00]'+song.extra,translation:'[00:01.00]translated'}}}`);
  const result=await r.call('getLyric',[{id:1,extra:'original'}]);assert.equal(result.rawLrc,'[00:01.00]original');assert.equal(result.translation,'[00:01.00]translated');
});
test('plugins without lyrics return null instead of an unsupported-method error',async()=>{
  assert.equal(await runtime(`module.exports={platform:'No lyrics'}`).call('getLyric',[{}]),null);
});
test('sheet search capabilities follow declarations and require detail support',async()=>{
  const methods="search(q,p,t){return {data:[{id:7,title:q,token:'keep',type:t}],isEnd:p>=2}},getMusicSheetInfo(s,p){return {musicList:[{id:p,title:s.title,token:s.token}],sheetItem:{cursor:'next'},isEnd:p>=2}}";
  const r=runtime(`module.exports={platform:'Sheets',${methods}}`);
  assert.equal((await r.call('metadata')).sheets,true);
  const sheet=(await r.call('search',['通勤',1,'sheet'])).data[0];assert.equal(sheet.type,'sheet');
  assert.equal((await r.call('getMusicSheetInfo',[sheet,2])).musicList[0].token,'keep');
  assert.equal((await runtime(`module.exports={platform:'No',supportedSearchType:['music'],${methods}}`).call('metadata')).sheets,false);
  assert.equal((await runtime(`module.exports={platform:'No',supportedSearchType:['sheet'],search(){}}`).call('metadata')).sheets,false);
});
