package app.qingting.music;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;
import static app.qingting.music.Models.*;

/** Local verification evidence, never a cached media URL. Main-thread owned. */
public final class VerificationCache {
    private static final long SUCCESS_MS=5*60*1000L,FAILURE_MS=60*1000L;
    private final LinkedHashMap<String,JSONObject> entries=new LinkedHashMap<>(32,.75f,true);
    private final Map<Source,String[]> fingerprints=new WeakHashMap<>();
    private final Map<Route,String> appliedKeys=new WeakHashMap<>();
    private String key(Source source,Route route){
        String variables=source.variables.toString();String[] previous=fingerprints.get(source);
        if(previous==null||!Objects.equals(previous[0],source.script)||!previous[1].equals(variables)){
            previous=new String[]{source.script,variables,hash(source.script+"\n"+variables)};fingerprints.put(source,previous);
        }
        return hash(source.id+"\n"+previous[2]+"\n"+route.raw.toString());
    }
    private static String hash(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean fresh(JSONObject entry,long now){long at=entry.optLong("at");return at>0&&now>=at&&now-at<=(entry.optBoolean("playable")?SUCCESS_MS:FAILURE_MS);}
    public void put(Source source,Route route){
        if(route.verifiedAt<=0||route.verifying)return;
        try{String key=key(source,route);entries.put(key,new JSONObject().put("key",key).put("at",route.verifiedAt).put("playable",route.verifiedPlayable).put("error",route.error).put("quality",route.quality).put("mimeType",route.mimeType));appliedKeys.put(route,key);trim();}catch(JSONException e){throw new IllegalArgumentException(e);}
    }
    private void trim(){while(entries.size()>500)entries.remove(entries.keySet().iterator().next());}
    public boolean apply(Source source,Route route,long now){
        if(route.verifying||route.playbackActive)return false;
        String key=key(source,route),previous=appliedKeys.put(route,key);
        if(previous!=null&&!previous.equals(key)){route.verifiedPlayable=false;route.verifiedAt=0;route.error="";route.status="待验证";}
        if(previous!=null&&!route.verifiedPlayable&&route.verifiedAt>0&&now>=route.verifiedAt&&now-route.verifiedAt>FAILURE_MS){route.error="";route.status="待验证";}
        JSONObject entry=entries.get(key);
        if(entry==null)return false;
        if(!fresh(entry,now)){
            entries.remove(key);
            if(!entry.optBoolean("playable")&&route.verifiedAt==entry.optLong("at")){route.error="";route.status="待验证";route.verifiedPlayable=false;}
            return false;
        }
        if(route.verifiedAt>entry.optLong("at"))return false;
        route.verifiedAt=entry.optLong("at");route.verifiedPlayable=entry.optBoolean("playable");route.error=entry.optString("error");route.quality=entry.optString("quality","standard");route.mimeType=entry.optString("mimeType");route.status=route.verifiedPlayable?"试播通过":"试播失败";return true;
    }
    public void remove(Source source,Route route){entries.remove(key(source,route));}
    public JSONArray json(){JSONArray data=new JSONArray();for(JSONObject entry:entries.values())data.put(entry);return data;}
    public static VerificationCache from(JSONArray data,long now){VerificationCache cache=new VerificationCache();for(int i=0;i<data.length();i++){JSONObject entry=data.optJSONObject(i);if(entry!=null&&entry.optString("key").length()==64&&fresh(entry,now))cache.entries.put(entry.optString("key"),entry);}cache.trim();return cache;}
}
