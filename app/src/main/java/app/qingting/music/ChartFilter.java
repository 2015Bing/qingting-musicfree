package app.qingting.music;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Main-thread empty-board evidence and bounded, short-lived first pages. */
public final class ChartFilter {
    private final LinkedHashMap<String,JSONObject> failures=new LinkedHashMap<>();
    private final Set<String> empty=new LinkedHashSet<>();
    private final LinkedHashMap<String,JSONObject> pages=new LinkedHashMap<>(24,.75f,true);
    private final Map<Models.Source,String[]> fingerprints=new WeakHashMap<>();
    public String key(Models.Source source,Charts.Board board){
        String vars=source.variables.toString();String[] old=fingerprints.get(source);
        if(old==null||!Objects.equals(old[0],source.script)||!old[1].equals(vars)){old=new String[]{source.script,vars,hash(source.id+"\n"+source.script+"\n"+vars)};fingerprints.put(source,old);}
        return hash(old[2]+"\n"+board.raw.toString());
    }
    private static String hash(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    public boolean hidden(String key){return hidden(key,System.currentTimeMillis());}
    public boolean hidden(String key,long now){return !reason(key,now).isEmpty();}
    public String reason(String key,long now){if(empty.contains(key))return "空榜";JSONObject f=failures.get(key);if(f==null)return "";long at=f.optLong("at");if(now<at||now-at>1800000){failures.remove(key);return "";}return f.optString("reason","加载失败");}
    public void failure(String key,String reason,long now){empty.remove(key);pages.remove(key);try{failures.put(key,new JSONObject().put("key",key).put("reason",reason).put("at",now));while(failures.size()>2000)failures.remove(failures.keySet().iterator().next());}catch(JSONException e){throw new IllegalArgumentException(e);}}
    /** -1: confirmed empty; 1: contains songs; 0: unknown/error, retained. */
    public int record(String key,JSONObject data,long now){return record(key,data,now,1);}
    public int record(String key,JSONObject data,long now,int firstPage){
        empty.remove(key);failures.remove(key);pages.remove(key);JSONArray songs=data==null?null:data.optJSONArray("musicList");if(songs==null)return 0;
        if(songs.length()==0&&data.optBoolean("isEnd",true)){empty.add(key);while(empty.size()>2000)empty.remove(empty.iterator().next());return -1;}
        boolean found=false;for(int i=0;i<songs.length();i++){JSONObject song=songs.optJSONObject(i);if(song!=null&&(song.has("id")||!song.optString("title").isEmpty()))found=true;}
        if(!found)return 0;
        try{pages.put(key,new JSONObject().put("at",now).put("firstPage",firstPage).put("page",new JSONObject(data.toString())));while(pages.size()>24)pages.remove(pages.keySet().iterator().next());}catch(JSONException e){throw new IllegalArgumentException(e);}return 1;
    }
    public JSONObject firstPage(String key,long now){JSONObject saved=pages.get(key);if(saved==null)return null;long at=saved.optLong("at");if(now<at||now-at>300000){pages.remove(key);return null;}try{return new JSONObject(saved.getJSONObject("page").toString());}catch(JSONException e){return null;}}
    public int firstPageNumber(String key){JSONObject saved=pages.get(key);return saved==null?1:saved.optInt("firstPage",1);}
    public JSONArray json(){JSONArray data=new JSONArray(empty);for(JSONObject failure:failures.values())data.put(failure);return data;}
    public static ChartFilter from(JSONArray data){ChartFilter f=new ChartFilter();for(int i=Math.max(0,data.length()-4000);i<data.length();i++){if(data.opt(i) instanceof String)f.empty.add(data.optString(i));else{JSONObject failure=data.optJSONObject(i);if(failure!=null&&!failure.optString("key").isEmpty())f.failures.put(failure.optString("key"),failure);}}return f;}
}
