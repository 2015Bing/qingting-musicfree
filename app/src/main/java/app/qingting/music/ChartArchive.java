package app.qingting.music;
import java.util.*;import org.json.*;
/** Worker-owned raw chart pages; never serializes live mutable playback routes. */
public final class ChartArchive {
 private final LinkedHashMap<String,JSONObject> records=new LinkedHashMap<>();
 public static final class Entry{public final String key;public final long at;public final Charts charts;Entry(String key,long at,Charts charts){this.key=key;this.at=at;this.charts=charts;}}
 public void record(String key,Charts.Board board,int page,JSONObject data,long now)throws JSONException{
  JSONObject entry=records.get(key);JSONArray pages=entry==null?null:entry.getJSONArray("pages");
  if(page==1||entry==null||pages.getJSONObject(pages.length()-1).getInt("number")+1!=page){pages=new JSONArray();entry=new JSONObject().put("sourceId",board.sourceId).put("sourceName",board.sourceName).put("group",board.group).put("board",new JSONObject(board.raw.toString())).put("pages",pages).put("key",key);}
  pages.put(new JSONObject().put("number",page).put("data",new JSONObject(data.toString())));entry.put("at",now);records.remove(key);records.put(key,entry);while(records.size()>24)records.remove(records.keySet().iterator().next());
 }
 public void remove(String key){records.remove(key);}public void clear(){records.clear();}
 public void touch(String key){JSONObject value=records.remove(key);if(value!=null)records.put(key,value);}
 public byte[] boundedBytes(int limit){byte[] bytes=json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);while(bytes.length>limit&&!records.isEmpty()){records.remove(records.keySet().iterator().next());bytes=json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);}return bytes;}
 public JSONArray json(){JSONArray result=new JSONArray();for(JSONObject entry:records.values())result.put(entry);return result;}
 public List<Entry> entries(long now){List<Entry> result=new ArrayList<>();for(JSONObject entry:records.values())try{long at=entry.getLong("at");if(now<at||now-at>1800000)continue;Models.Source source=new Models.Source("cached");source.id=entry.getString("sourceId");source.name=entry.getString("sourceName");Charts.Board board=new Charts.Board(source,entry.optString("group"),entry.getJSONObject("board"));Charts charts=new Charts();JSONArray pages=entry.getJSONArray("pages");for(int i=0;i<pages.length();i++){JSONObject page=pages.getJSONObject(i);if(i==0)charts.seedPage(board,page.getJSONObject("data"),page.getInt("number"));else if(!charts.append(charts.token(),page.getInt("number"),page.getJSONObject("data")))throw new JSONException("分页不连续");}if(charts.page>0)result.add(new Entry(entry.getString("key"),at,charts));}catch(Exception ignored){}return result;}
 public static ChartArchive from(JSONArray json,long now){ChartArchive archive=new ChartArchive();for(int i=Math.max(0,json.length()-24);i<json.length();i++){JSONObject e=json.optJSONObject(i);if(e!=null&&!e.optString("key").isEmpty()&&now>=e.optLong("at")&&now-e.optLong("at")<=1800000)archive.records.put(e.optString("key"),e);}return archive;}
}
