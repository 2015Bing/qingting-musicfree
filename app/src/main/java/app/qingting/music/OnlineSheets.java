package app.qingting.music;
import org.json.*;import java.util.*;import java.util.concurrent.Executor;import java.util.function.*;import java.nio.charset.StandardCharsets;import static app.qingting.music.Models.*;
/** Main-thread state, bounded worker requests; no sheet failure affects song-source health. */
public final class OnlineSheets {
 public interface Invoker{Object call(Source source,String method,JSONArray args,BooleanSupplier valid)throws Exception;}
 private final Supplier<List<Source>> sources;private final Invoker invoke;private final Executor searchWork,detailWork,main;private final LocalFileCache disk;private final Runnable notify;
 public final List<Sheets.Item> items=new ArrayList<>();public final Map<String,String> states=new LinkedHashMap<>();public final Set<String> supported=new LinkedHashSet<>();
 private final Map<String,Integer> pages=new HashMap<>();private final Set<String> ended=new HashSet<>();private final Map<String,Source> searched=new LinkedHashMap<>();
 public String query="",detailError="";public boolean searching,loadingDetail;public Sheets.Detail detail;public int revision;
 private volatile int searchToken,detailToken;private int pending;private boolean forceSearch,retryFromStart;
 private final LinkedHashMap<String,Cached> details=new LinkedHashMap<>(16,.75f,true);
 private static final class Cached{final Sheets.Detail value;final long at;Cached(Sheets.Detail value){this.value=value;at=System.currentTimeMillis();}}
 public OnlineSheets(Supplier<List<Source>> sources,Invoker invoke,Executor searchWork,Executor detailWork,Executor main,LocalFileCache disk,Runnable notify){this.sources=sources;this.invoke=invoke;this.searchWork=searchWork;this.detailWork=detailWork;this.main=main;this.disk=disk;this.notify=notify;}
 private void changed(){revision++;notify.run();}
 private Source current(String id){for(Source s:sources.get())if(s.id.equals(id))return s;return null;}
 private boolean usable(Source snapshot){Source s=current(snapshot.id);return s!=null&&s.enabled&&Objects.equals(s.script,snapshot.script)&&s.variables.toString().equals(snapshot.variables.toString());}
 private Source snapshot(Source source){try{Source copy=new Source(source.url);copy.id=source.id;copy.name=source.name;copy.script=source.script;copy.version=source.version;copy.enabled=source.enabled;copy.variables=new JSONObject(source.variables.toString());return copy;}catch(Exception e){throw new IllegalArgumentException(e);}}
 public void search(String value,boolean refresh){
  searchToken++;closeDetail();query=value.trim();forceSearch=refresh;items.clear();states.clear();pages.clear();ended.clear();supported.clear();searched.clear();pending=0;searching=false;
  if(!query.isEmpty())for(Source s:sources.get())if(s.enabled&&s.script!=null&&!s.script.isEmpty())searched.put(s.id,snapshot(s));loadMore();
 }
 public boolean hasMore(){for(Source s:searched.values())if(usable(s)&&!ended.contains(s.id))return true;return false;}
 public void pauseSearch(){searchToken++;pending=0;searching=false;for(String id:new ArrayList<>(states.keySet()))if(states.get(id).contains("搜索中"))states.put(id,"已暂停，加载更多可继续");changed();}
 public void loadMore(){
  if(searching)return;int token=searchToken;String q=query;List<Source> selected=new ArrayList<>();for(Source s:searched.values())if(usable(s)&&!ended.contains(s.id))selected.add(s);
  pending=selected.size();searching=pending>0;for(Source s:selected){int page=pages.getOrDefault(s.id,0)+1;states.put(s.id,"识别能力 / 搜索中");boolean refresh=forceSearch;
   searchWork.execute(()->{if(token!=searchToken)return;JSONObject meta=null,result=null;List<Sheets.Item> found=new ArrayList<>();String error=null;
    try{String config=Sheets.fingerprint(s);meta=read("meta:"+config);if(meta==null){meta=(JSONObject)invoke.call(s,"metadata",new JSONArray(),()->token==searchToken);write("meta:"+config,meta);}
     if(meta.optBoolean("sheets")){String cache="search:"+config+":"+q+":"+page;result=refresh?null:read(cache);boolean fetched=result==null;if(fetched)result=(JSONObject)invoke.call(s,"search",new JSONArray().put(q).put(page).put("sheet"),()->token==searchToken);
      JSONArray data=result.getJSONArray("data");for(int i=0;i<data.length();i++)found.add(new Sheets.Item(s,data.getJSONObject(i),meta.optJSONArray("primaryKey"),config));if(fetched&&token==searchToken)write(cache,result);
     }
    }catch(Exception e){error=message(e);}JSONObject metadata=meta,response=result;String failure=error;
    main.execute(()->{if(token!=searchToken)return;pending--;searching=pending>0;if(!usable(s)){states.put(s.id,"来源已停用或更新，请重新搜索");changed();return;}
     if(metadata!=null&&metadata.optBoolean("sheets"))supported.add(s.id);
     if(failure!=null)states.put(s.id,"加载失败："+failure);
     else if(metadata==null||!metadata.optBoolean("sheets")){ended.add(s.id);states.put(s.id,"不支持歌单搜索及详情");}
     else{Set<String> seen=new HashSet<>();for(Sheets.Item item:items)seen.add(item.key);for(Sheets.Item item:found)if(seen.add(item.key))items.add(item);pages.put(s.id,page);if(response.optBoolean("isEnd",true))ended.add(s.id);states.put(s.id,"第 "+page+" 页 · "+found.size()+" 个歌单"+(ended.contains(s.id)?" · 已全部加载":""));}changed();
    });
   });
  }changed();
 }
 private static String key(Sheets.Item item){return "detail:"+item.config+":"+item.key;}
 public void closeDetail(){detailToken++;detail=null;loadingDetail=false;detailError="";changed();}
 public void open(Sheets.Item item,boolean refresh){
  int token=++detailToken;retryFromStart=true;detailError="";Source live=current(item.sourceId);if(live==null||!live.enabled||!Sheets.fingerprint(live).equals(item.config)){try{detail=new Sheets.Detail(item);}catch(Exception ignored){}loadingDetail=false;detailError="来源已停用或更新，请重新搜索";changed();return;}
  Source source=snapshot(live);Cached cached=details.get(key(item));long now=System.currentTimeMillis();if(cached!=null&&(now<cached.at||now-cached.at>1800000)){details.remove(key(item));cached=null;}
  try{boolean keep=refresh&&detail!=null&&detail.item.key.equals(item.key)&&detail.item.config.equals(item.config);if(!keep)detail=cached==null?new Sheets.Detail(item):cached.value;}catch(Exception e){detailError=message(e);changed();return;}
  if(cached!=null&&!refresh){loadingDetail=false;changed();return;}loadingDetail=true;changed();
  detailWork.execute(()->{Sheets.Detail loaded=null;String error=null;try{if(token!=detailToken)return;JSONObject saved=refresh?null:read(key(item));if(saved!=null)try{loaded=Sheets.Detail.from(item,saved);}catch(Exception ignored){}
    if(loaded==null){Sheets.Detail empty=new Sheets.Detail(item);loaded=empty.append((JSONObject)invoke.call(source,"getMusicSheetInfo",new JSONArray().put(empty.raw).put(1),()->token==detailToken));if(token==detailToken)write(key(item),loaded.json());}
   }catch(Exception e){error=message(e);}finishDetail(token,source,loaded,error);
  });
 }
 public void pauseDetail(){if(!loadingDetail)return;detailToken++;loadingDetail=false;detailError="加载已暂停，点重试继续";changed();}
 public void retryDetail(){if(detail==null)return;if(retryFromStart||detail.page==0)open(detail.item,true);else loadDetail();}
 public void loadDetail(){if(detail==null||loadingDetail||detail.end)return;retryFromStart=false;Sheets.Detail before=detail;Source live=current(before.item.sourceId);if(live==null||!live.enabled||!Sheets.fingerprint(live).equals(before.item.config)){detailError="来源已停用或更新，请重新搜索";changed();return;}Source source=snapshot(live);int token=detailToken;loadingDetail=true;detailError="";changed();
  detailWork.execute(()->{Sheets.Detail loaded=null;String error=null;try{if(token!=detailToken)return;loaded=before.append((JSONObject)invoke.call(source,"getMusicSheetInfo",new JSONArray().put(new JSONObject(before.raw.toString())).put(before.page+1),()->token==detailToken));if(token==detailToken)write(key(before.item),loaded.json());}catch(Exception e){error=message(e);}finishDetail(token,source,loaded,error);});
 }
 private void finishDetail(int token,Source source,Sheets.Detail loaded,String error){main.execute(()->{if(token!=detailToken)return;loadingDetail=false;if(!usable(source)){detailError="来源已停用或更新，请重新搜索";}else if(error!=null)detailError=error;else{detail=loaded;detailError="";details.put(key(loaded.item),new Cached(loaded));while(details.size()>12)details.remove(details.keySet().iterator().next());}changed();});}
 private JSONObject read(String key){byte[] value=disk.get(key,System.currentTimeMillis());if(value==null)return null;try{return new JSONObject(new String(value,StandardCharsets.UTF_8));}catch(Exception e){return null;}}
 private void write(String key,JSONObject value){disk.put(key,value.toString().getBytes(StandardCharsets.UTF_8),System.currentTimeMillis());}
 private static String message(Exception e){return e.getMessage()==null?"请求失败":e.getMessage();}
}
