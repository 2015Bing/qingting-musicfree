package app.qingting.music;
import org.json.*;
import java.util.*;

public final class Models {
    public static class Source {
        public String id, name, url, subscription, script, version="", message="尚未检测";
        public boolean enabled=true; public int failures,healthVersion; public long failedAt, checkedAt, latency;
        public boolean music=true,lyricSearch,topLists;public PlaybackHealth playbackHealth=new PlaybackHealth();public String playbackMessage="尚未试播检测";
        public JSONObject variables=new JSONObject(); public JSONArray fields=new JSONArray();
        public Source(String url) { this.url=url; this.id=UUID.nameUUIDFromBytes(url.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(); this.name="未命名源"; }
        public boolean hidden() { return playbackHealth.filtered()||CatalogPolicy.hidden(failures,failedAt,System.currentTimeMillis()); }
        public JSONObject json() throws JSONException {
            return new JSONObject().put("id",id).put("name",name).put("url",url).put("subscription",subscription)
                .put("script",script).put("version",version).put("enabled",enabled).put("failures",failures)
                .put("failedAt",failedAt).put("checkedAt",checkedAt).put("message",message).put("variables",variables).put("fields",fields)
                .put("music",music).put("lyricSearch",lyricSearch).put("topLists",topLists).put("playbackHealth",playbackHealth.json()).put("playbackMessage",playbackMessage);
        }
        public static Source from(JSONObject j) {
            Source s=new Source(j.optString("url")); s.name=j.optString("name");s.subscription=j.optString("subscription");s.script=j.optString("script");
            s.version=j.optString("version");s.enabled=j.optBoolean("enabled",true);s.failures=j.optInt("failures");s.failedAt=j.optLong("failedAt");
            s.checkedAt=j.optLong("checkedAt");s.message=j.optString("message");s.variables=j.optJSONObject("variables");if(s.variables==null)s.variables=new JSONObject();
            s.topLists=j.optBoolean("topLists");s.music=j.optBoolean("music",true);s.lyricSearch=j.optBoolean("lyricSearch");s.playbackHealth=PlaybackHealth.from(j.optJSONArray("playbackHealth"));s.playbackMessage=j.optString("playbackMessage","尚未试播检测");
            s.fields=j.optJSONArray("fields");if(s.fields==null)s.fields=new JSONArray();return s;
        }
    }
    public static class Route {
        public final String sourceId, sourceName; public final JSONObject raw;
        public String status="待验证", error="",mimeType="",quality="standard"; public long verifiedAt;public boolean playbackActive,verifiedPlayable,verifying,lowOnly;public int probeVersion;
        public Route(Source source,JSONObject raw) { this.sourceId=source.id; this.sourceName=source.name; this.raw=raw; }
        public Route(String id,String name,JSONObject raw) {this.sourceId=id;this.sourceName=name;this.raw=raw;}
        public String[] qualities(boolean decoding){return lowOnly&&!decoding?new String[]{"low"}:quality.equals("low")?new String[]{"low","standard"}:new String[]{"standard","low"};}
        public String label() { if(verifying)return "静音试播中";return System.currentTimeMillis()-verifiedAt>5*60*1000L && (status.equals("链路可达")||status.equals("播放通过")) ? "待复检" : status; }
    }
    public static class Song {
        public final String title,artist,album;public String key; public final List<Route> routes=new ArrayList<>();
        public String pinnedSource="",pinnedRaw="";public boolean sourceChoiceRequired;public long addedAt;
        public Route fixedRoute(){for(Route r:routes)if(r.sourceId.equals(pinnedSource)&&(pinnedRaw.isEmpty()||r.raw.toString().equals(pinnedRaw)))return r;return null;}
        public boolean fixed(){return !pinnedSource.isEmpty()||sourceChoiceRequired;}
        public Song snapshot(Route pin){try{Song copy=from(json());if(pin!=null){copy.pinnedSource=pin.sourceId;copy.pinnedRaw=pin.raw.toString();copy.sourceChoiceRequired=false;if(copy.fixedRoute()==null)copy.routes.add(new Route(pin.sourceId,pin.sourceName,new JSONObject(pin.raw.toString())));}return copy;}catch(JSONException e){throw new IllegalArgumentException("歌曲数据无法保存",e);}}

        public Song(Route route) {
            title=SongText.clean(route.raw.optString("title","未命名歌曲"));artist=SongText.clean(route.raw.optString("artist",""));album=SongText.clean(route.raw.optString("album",""));
            key=CatalogPolicy.canGroup(title,artist)?CatalogPolicy.key(title,artist,album):route.sourceId+":"+route.raw.toString();routes.add(route);
        }
        public JSONObject json() throws JSONException {
            JSONArray r=new JSONArray();for(Route route:routes)r.put(new JSONObject().put("sourceId",route.sourceId).put("sourceName",route.sourceName).put("raw",route.raw));
            return new JSONObject().put("routes",r).put("key",key).put("pinnedSource",pinnedSource).put("pinnedRaw",pinnedRaw).put("sourceChoiceRequired",sourceChoiceRequired).put("addedAt",addedAt);
        }
        public static Song from(JSONObject j) throws JSONException {
            JSONArray a=j.getJSONArray("routes"); Song song=null;
            for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);Route route=new Route(r.getString("sourceId"),r.getString("sourceName"),r.getJSONObject("raw"));if(song==null)song=new Song(route);else song.routes.add(route);}if(song!=null){song.key=j.optString("key",song.key);song.pinnedSource=j.optString("pinnedSource");song.pinnedRaw=j.optString("pinnedRaw");song.sourceChoiceRequired=j.optBoolean("sourceChoiceRequired");song.addedAt=j.optLong("addedAt");}return song;
        }
    }
    public static class Media {
        public long expiresAt;public String url,mimeType,rawLrc,translation,quality="standard"; public Map<String,String> headers=new HashMap<>();
        public Media(JSONObject j) throws JSONException {
            expiresAt=j.optLong("expiresAt",0);if(expiresAt>0&&expiresAt<100000000000L)expiresAt*=1000;url=j.getString("url");mimeType=j.optString("mimeType","");rawLrc=j.optString("rawLrc","");translation=j.optString("translation","");if(!url.startsWith("https://")&&!url.startsWith("http://"))throw new JSONException("音源未返回 HTTP(S) 地址");
            JSONObject h=j.optJSONObject("headers");if(h!=null)for(Iterator<String> i=h.keys();i.hasNext();){String k=i.next();headers.put(k,h.optString(k));}
        }
    }
}
