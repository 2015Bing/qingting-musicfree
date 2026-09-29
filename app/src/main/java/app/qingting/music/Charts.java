package app.qingting.music;
import java.util.*;
import org.json.*;
import static app.qingting.music.Models.*;

/** Main-thread chart state, with response generations independent of search/playback. */
public final class Charts {
    public static boolean sourceAvailable(Source source){return source!=null&&source.enabled;}
    public static final class Board {
        public final String sourceId,sourceName,group,title;public final JSONObject raw;
        Board(Source source,String group,JSONObject raw){sourceId=source.id;sourceName=source.name;this.group=group;this.raw=raw;title=raw.optString("title","未命名榜单");}
    }
    public final List<Board> boards=new ArrayList<>();public final List<Song> songs=new ArrayList<>();
    public final Map<String,String> sourceStates=new LinkedHashMap<>();
    public Board current;public JSONObject detailRaw;public int page;public boolean loadingCatalog,loadingSongs,end;
    public String detailStatus="";private volatile int generation;private int receivedCount;private final Set<String> songIds=new HashSet<>();
    private final Map<Song,Integer> ranks=new IdentityHashMap<>();
    private final LinkedHashMap<String,Snapshot> cache=new LinkedHashMap<>(24,.75f,true);
    private static final class Snapshot {
        final long at;final List<Song> songs;final Map<Song,Integer> ranks;final Set<String> ids;final String raw;final int page,count;final boolean end;
        Snapshot(Charts c,long now){at=now;songs=new ArrayList<>(c.songs);ranks=new IdentityHashMap<>(c.ranks);ids=new HashSet<>(c.songIds);raw=c.detailRaw.toString();page=c.page;count=c.receivedCount;end=c.end;}
    }
    public void remember(String key,long now){if(current==null||page==0)return;cache.put(key,new Snapshot(this,now));while(cache.size()>24)cache.remove(cache.keySet().iterator().next());}
    public boolean restore(Board board,String key,long now){
        Snapshot saved=cache.get(key);if(saved==null)return false;
        if(now<saved.at||now-saved.at>1800000){cache.remove(key);return false;}
        select(board);try{detailRaw=new JSONObject(saved.raw);}catch(JSONException e){cache.remove(key);return false;}
        songs.addAll(saved.songs);ranks.putAll(saved.ranks);songIds.addAll(saved.ids);page=saved.page;receivedCount=saved.count;end=saved.end;detailStatus="已加载 "+songs.size()+" 首";return true;
    }
    public void forget(String key){cache.remove(key);}
    public void clearCache(){cache.clear();}
    public void installCache(String key,Charts prepared,long at){cache.put(key,new Snapshot(prepared,at));while(cache.size()>24)cache.remove(cache.keySet().iterator().next());}
    public int rank(Song song){Integer rank=ranks.get(song);return rank==null?0:rank;}
    public void seed(Board board,JSONObject firstPage,List<Song> cachedSongs) throws JSONException {
        int token=select(board);append(token,1,firstPage);
        Map<String,Song> cached=new HashMap<>();for(Song song:cachedSongs)cached.put(songId(song),song);
        for(int i=0;i<songs.size();i++){Song parsed=songs.get(i),song=cached.get(songId(parsed));if(song!=null){int rank=ranks.remove(parsed);songs.set(i,song);ranks.put(song,rank);}}
    }
    public void seedPage(Board board,JSONObject data,int firstPage) throws JSONException {int token=select(board);page=Math.max(1,firstPage)-1;append(token,page+1,data);}
    private static String songId(Song song){JSONObject raw=song.routes.get(0).raw;return raw.has("id")&&!raw.isNull("id")?String.valueOf(raw.opt("id")):song.key;}
    public int token(){return generation;}
    public int select(Board board){clearSelection();current=board;try{detailRaw=new JSONObject(board.raw.toString());}catch(JSONException e){throw new IllegalArgumentException(e);}return generation;}
    public void clearSelection(){generation++;current=null;detailRaw=null;songs.clear();songIds.clear();ranks.clear();receivedCount=0;page=0;end=false;loadingSongs=false;detailStatus="";}
    public boolean append(int token,int requestedPage,JSONObject data) throws JSONException {
        if(token!=generation||current==null||requestedPage!=page+1||end)return false;
        JSONArray items=data.getJSONArray("musicList");List<Song> additions=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(int i=0;i<items.length();i++){JSONObject raw=items.optJSONObject(i);if(raw==null)continue;
            Song song=new Song(new Route(current.sourceId,current.sourceName,raw));String id=songId(song);
            if(!songIds.contains(id)&&ids.add(id)){additions.add(song);ranks.put(song,receivedCount+i+1);}
        }
        JSONObject supplement=data.optJSONObject("topListItem");if(supplement!=null)for(Iterator<String> i=supplement.keys();i.hasNext();){String key=i.next();detailRaw.put(key,supplement.get(key));}
        songs.addAll(additions);songIds.addAll(ids);receivedCount+=items.length();page=requestedPage;end=data.optBoolean("isEnd",true)||additions.isEmpty();loadingSongs=false;detailStatus="已加载 "+songs.size()+" 首";return true;
    }
    public static List<Board> parseBoards(Source source,JSONArray groups) throws JSONException {
        List<Board> result=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(int i=0;i<groups.length();i++){JSONObject group=groups.getJSONObject(i);JSONArray items=group.optJSONArray("data");
            if(items==null){if(group.has("id")){if(ids.add(group.optString("id")))result.add(new Board(source,"",group));continue;}throw new JSONException("榜单分组缺少 data");}
            for(int j=0;j<items.length();j++){JSONObject item=items.optJSONObject(j);if(item==null)continue;String id=item.optString("id",item.toString());if(ids.add(id))result.add(new Board(source,group.optString("title",""),item));}
        }return result;
    }
}
