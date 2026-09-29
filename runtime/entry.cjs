const axios = require('axios');
const modules = { axios, 'crypto-js': require('crypto-js'), dayjs: require('dayjs'),
  'big-integer': require('big-integer'), qs: require('qs'), he: require('he'), cheerio: require('cheerio') };
const pending = new Map(); let counter = 0;
axios.defaults.adapter = config => new Promise((resolve, reject) => {
  const id = String(++counter);
  pending.set(id, {resolve, reject, config});
  Native.request(id, JSON.stringify({url:axios.getUri(config),method:(config.method || 'get').toUpperCase(),
    headers:config.headers || {},body:config.data ?? null,responseType:config.responseType || 'text',timeout:Math.min(config.timeout || 12000,12000)}));
});
globalThis.__httpResult = (id, response) => {
  const task = pending.get(id); if (!task) return; pending.delete(id);
  if (response.error) { task.reject(new Error(response.error)); return; }
  const data=response.dataEncoding==='base64'?Uint8Array.from(atob(response.data),c=>c.charCodeAt(0)).buffer:response.data;
  const value={...response,data,config:task.config,statusText:String(response.status)};
  if (!task.config.validateStatus || task.config.validateStatus(response.status)) task.resolve(value);
  else { const e=new Error('HTTP '+response.status);e.response=value;task.reject(e); }
};
let loadedSource, plugin, env;
globalThis.__invoke = async (id, source, variables, method, args) => {
  try {
    env = {getUserVariables:()=>variables, getMusicFreeVersion:()=> '0.6.0'};
    if (loadedSource !== source) {
      const module={exports:{}};
      new Function('module','exports','require','env',source)(module,module.exports,name=> {
        if (!(name in modules)) throw new Error('不支持的依赖：'+name);
        return modules[name];
      }, {getUserVariables:()=>env.getUserVariables(),getMusicFreeVersion:()=>env.getMusicFreeVersion()});
      const candidate=module.exports.default || module.exports;
      if (!candidate || !candidate.platform) throw new Error('插件缺少 platform');
      plugin=candidate;
      loadedSource=source;
    }
    let value;
    if (method==='metadata') value={platform:plugin.platform,version:plugin.version || '0.0.0',
      userVariables:plugin.userVariables || [],search:typeof plugin.search==='function',getLyric:typeof plugin.getLyric==='function',topLists:typeof plugin.getTopLists==='function'&&typeof plugin.getTopListDetail==='function',supportedSearchType:plugin.supportedSearchType || ['music']};
    else if(method==='getMediaSource' && typeof plugin[method]!=='function') value={url:args[0].url};
    else if(method==='getLyric' && typeof plugin[method]!=='function') value=args[0]?.rawLrc?{rawLrc:args[0].rawLrc,translation:args[0].translation}:null;
    else {
      if(typeof plugin[method]!=='function') throw new Error('插件不支持 '+method);
      value=await plugin[method](...args);
    }
    Native.complete(id,JSON.stringify({value:value ?? null}));
  } catch(error) { Native.complete(id,JSON.stringify({error:String(error.message || error)})); }
};
