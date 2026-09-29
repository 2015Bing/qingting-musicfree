package app.qingting.music;
import org.json.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import static app.qingting.music.Models.*;
/** Plugin sheet data stays separate from recordings and local playlists. */
public final class Sheets {
 public static String fingerprint(Source source){return digest(source.id+"\n"+source.script+"\n"+source.variables);}
 private static String digest(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 static String identity(JSONObject raw,JSONArray keys){JSONArray values=new JSONArray();if(keys==null||keys.length()==0)keys=new JSONArray().put("id");for(int i=0;i<keys.length();i++){String key=keys.optString(i);if(!raw.has(key)||raw.isNull(key))return raw.toString();values.put(raw.opt(key));}return values.toString();}
 public static final class Item {
  public final String sourceId,sourceName,key,config;public final JSONObject raw;public final JSONArray primaryKeys;
  public Item(Source source,JSONObject raw,JSONArray keys)throws JSONException{this(source,raw,keys,fingerprint(source));}
  Item(Source source,JSONObject raw,JSONArray keys,String config)throws JSONException{sourceId=source.id;sourceName=source.name;this.config=config;this.raw=new JSONObject(raw.toString());primaryKeys=keys==null?new JSONArray().put("id"):new JSONArray(keys.toString());key=sourceId+":"+identity(raw,primaryKeys);}
  public String title(){return SongText.clean(raw.optString("title",raw.optString("name","未命名歌单")));}
  public String subtitle(){List<String> parts=new ArrayList<>();Object creator=raw.opt("artist");if(creator==null)creator=raw.opt("creator");if(creator instanceof JSONObject)creator=((JSONObject)creator).optString("nickname",((JSONObject)creator).optString("name"));if(creator instanceof String&&!((String)creator).trim().isEmpty())parts.add(SongText.clean((String)creator));int count=raw.optInt("worksNum",raw.optInt("trackCount",-1));if(count>=0)parts.add(count+" 首");parts.add(sourceName);return String.join(" · ",parts);}
  public String artwork(){for(String key:new String[]{"artwork","cover","picUrl","coverImgUrl"}){String value=raw.optString(key);if(value.startsWith("https://")||value.startsWith("http://"))return value;}return "";}
 }
 public static final class Detail {
  public final Item item;public final JSONObject raw;public final List<Song> songs;public final int page;public final boolean end;
  public Detail(Item item)throws JSONException{this(item,new JSONObject(item.raw.toString()),new ArrayList<>(),0,false);}
  private Detail(Item item,JSONObject raw,List<Song> songs,int page,boolean end){this.item=item;this.raw=raw;this.songs=Collections.unmodifiableList(songs);this.page=page;this.end=end;}
  public Detail append(JSONObject result)throws JSONException{
   JSONArray entries=result.getJSONArray("musicList");JSONObject nextRaw=new JSONObject(raw.toString()),extra=result.optJSONObject("sheetItem");if(extra!=null)for(Iterator<String> keys=extra.keys();keys.hasNext();){String key=keys.next();nextRaw.put(key,extra.get(key));}
   List<Song> next=new ArrayList<>(songs);Set<String> seen=new HashSet<>();for(Song song:songs)seen.add(identity(song.routes.get(0).raw,item.primaryKeys));
   for(int i=0;i<entries.length();i++){JSONObject song=entries.getJSONObject(i);if(seen.add(identity(song,item.primaryKeys)))next.add(new Song(new Route(item.sourceId,item.sourceName,new JSONObject(song.toString()))));}
   return new Detail(item,nextRaw,next,page+1,result.optBoolean("isEnd",true));
  }
  public JSONObject json()throws JSONException{return new JSONObject().put("raw",raw).put("songs",LibraryState.songsJson(songs)).put("page",page).put("end",end);}
  public static Detail from(Item item,JSONObject value)throws JSONException{List<Song> songs=new ArrayList<>();LibraryState.readSongs(value.getJSONArray("songs"),songs);return new Detail(item,value.getJSONObject("raw"),songs,value.getInt("page"),value.getBoolean("end"));}
 }
}
