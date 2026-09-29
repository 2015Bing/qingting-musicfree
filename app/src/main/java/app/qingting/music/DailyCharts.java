package app.qingting.music;

import android.app.job.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static app.qingting.music.Models.*;

/** One last-successful chart; all public state and callbacks belong to the main thread. */
@androidx.media3.common.util.UnstableApi
public final class DailyCharts {
    static final long DAY=24*60*60*1000L,BUDGET=90_000L;
    private static final int JOB_ID=71421,CACHE_VERSION=2;
    private JSONObject cachedDetail;
    public Charts.Board current;
    public final List<Song> songs=new ArrayList<>();
    public boolean loading;
    public String status="尚未获取每日榜单";
    public long fetchedAt,publishedAt;
    private final MusicRepository repo;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Runnable> completions=new ArrayList<>();
    private volatile int generation;
    private Runnable timeout;
    private long lastAttempt;private String cachedFingerprint="";
    public DailyCharts(Context context,MusicRepository repository){
        repo=repository;prefs=context.getSharedPreferences("daily_chart",Context.MODE_PRIVATE);restore();
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler!=null&&(scheduler.getPendingJob(JOB_ID)==null||!scheduler.getPendingJob(JOB_ID).isPersisted()))scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(context,DailyChartJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(DAY,60*60*1000L).setPersisted(true).build());
    }
    public void ensureFresh(){if((!hasUsableSource()||stale(fetchedAt,System.currentTimeMillis()))&&System.currentTimeMillis()-lastAttempt>60_000)refresh(false,null);}
    static boolean stale(long fetched,long now){return fetched<=0||now<fetched||now-fetched>=DAY;}
    static boolean canReplace(long previous,long next,int count){return count>0&&(previous<=0||next>=previous);}
    static List<Source> selectSources(List<Source> sources){
        List<Source> selected=new ArrayList<>();
        for(boolean known:new boolean[]{true,false})for(Source source:sources){
            if(selected.size()>=4)return selected;
            if(Charts.sourceAvailable(source)&&(source.music||source.topLists)&&source.topLists==known)selected.add(source);
        }
        return selected;
    }
    public boolean hasUsableSource(){
        if(current==null||songs.isEmpty())return false;Source source=repo.source(current.sourceId);
        return Charts.sourceAvailable(source)&&cachedFingerprint.equals(source.script+source.variables);
    }
    public void seed(Charts charts){if(current==null||cachedDetail==null)return;try{charts.seed(current,cachedDetail,songs);}catch(JSONException ignored){}}
    static Charts fullPage(Charts.Board board,JSONObject detail) throws JSONException {Charts page=new Charts();int token=page.select(board);page.append(token,1,detail);return page;}
    static boolean fullPageCache(JSONObject data){return data.optInt("version")==CACHE_VERSION&&data.optJSONObject("detail")!=null;}
    static boolean hasUnfailedRoute(List<Song> songs){
        for(Song song:songs)for(Route route:song.routes)if(route.error.isEmpty())return true;
        return false;
    }
    static boolean canReplace(long previous,long next,int count,boolean previousUsable){return count>0&&(!previousUsable||canReplace(previous,next,count));}
    public void refresh(boolean force,Runnable completion){
        if(loading){if(completion!=null)completions.add(completion);return;}
        if(!force&&hasUsableSource()&&!stale(fetchedAt,System.currentTimeMillis())){if(completion!=null)completion.run();return;}
        lastAttempt=System.currentTimeMillis();
        if(!repo.online()){status="网络不可用，保留上次榜单";repo.changed();if(completion!=null)completion.run();return;}
        List<Source> selected=new ArrayList<>();
        for(Source s:selectSources(new ArrayList<>(repo.sources)))try{selected.add(Source.from(s.json()));}catch(Exception ignored){}
        if(completion!=null)completions.add(completion);
        loading=true;status="正在查找近期榜单并静音试播…";int token=++generation;long deadline=SystemClock.elapsedRealtime()+BUDGET;
        timeout=()->{if(token==generation){generation++;finish("本次检测达到 90 秒上限，保留上次榜单");}};main.postDelayed(timeout,BUDGET);repo.changed();
        worker.execute(()->scan(token,deadline,selected));
    }
    public void cancel(){if(!loading)return;generation++;finish("检测已取消，保留上次榜单");}
    private boolean valid(int token,long deadline){return token==generation&&SystemClock.elapsedRealtime()<deadline;}
    private static final class Candidate {
        final Source source;final Charts.Board board;final JSONObject detail;final long published;
        Candidate(Source s,Charts.Board b,JSONObject d){source=s;board=b;detail=d;long t=timestamp(d.optJSONObject("topListItem"));published=t>0?t:Math.max(timestamp(d),timestamp(b.raw));}
    }
    private void scan(int token,long deadline,List<Source> selected){
        List<Candidate> candidates=new ArrayList<>();
        for(Source s:selected){if(!valid(token,deadline)||(!candidates.isEmpty()&&deadline-SystemClock.elapsedRealtime()<65_000))break;
            try{
                Object meta=repo.dailyInvoke(s,"metadata",new JSONArray());if(!(meta instanceof JSONObject)||!((JSONObject)meta).optBoolean("topLists"))continue;
                Object value=repo.dailyInvoke(s,"getTopLists",new JSONArray());if(!(value instanceof JSONArray))continue;
                List<Charts.Board> boards=Charts.parseBoards(s,(JSONArray)value);boards.sort((a,b)->Long.compare(timestamp(b.raw),timestamp(a.raw)));
                int inspected=0;for(Charts.Board board:boards){if(!valid(token,deadline)||inspected++>=3||(!candidates.isEmpty()&&deadline-SystemClock.elapsedRealtime()<45_000))break;
                    try{Object d=repo.dailyInvoke(s,"getTopListDetail",new JSONArray().put(board.raw).put(1));if(d instanceof JSONObject&&((JSONObject)d).optJSONArray("musicList")!=null)candidates.add(new Candidate(s,board,(JSONObject)d));}catch(Exception ignored){}
                }
            }catch(Exception ignored){}
        }
        candidates.sort((a,b)->Long.compare(b.published,a.published));
        for(Candidate c:candidates){
            if(!valid(token,deadline))break;List<Song> full;try{full=fullPage(c.board,c.detail).songs;}catch(JSONException ignored){continue;}
            long initialDeadline=Math.min(deadline,SystemClock.elapsedRealtime()+20_000L);int initialAttempts=0;
            for(Song song:full){
                if(!valid(token,deadline)||!mayProbeCandidate(false,initialAttempts,SystemClock.elapsedRealtime(),initialDeadline))break;
                initialAttempts++;
                try{Route r=song.routes.get(0);Media media=repo.dailyVerify(c.source,r,()->valid(token,initialDeadline));if(!valid(token,deadline))break;r.verifiedPlayable=true;r.verifiedAt=System.currentTimeMillis();r.status="播放通过";r.mimeType=media.mimeType;r.quality=media.quality;main.post(()->publish(token,c,full));return;}catch(Exception ignored){}
            }
        }
        main.post(()->{if(token==generation)finish("本次窗口未找到试播通过的榜单，保留上次榜单（最多 4 个来源、每源 3 个榜单）");});
    }
    static boolean mayProbeCandidate(boolean hasSuccess,int attempts,long now,long initialDeadline){return hasSuccess||(attempts<2&&now<initialDeadline);}
    private void publish(int token,Candidate c,List<Song> full){
        if(token!=generation)return;Source live=repo.source(c.source.id);
        if(!Charts.sourceAvailable(live)||!Objects.equals(live.script,c.source.script)||!live.variables.toString().equals(c.source.variables.toString())){finish("来源配置已变化，保留上次榜单");return;}
        if(!canReplace(publishedAt,c.published,full.size(),hasUsableSource()&&hasUnfailedRoute(songs))){finish("本次榜单日期更早或未注明日期，保留上次榜单");return;}
        current=c.board;cachedDetail=c.detail;cachedFingerprint=live.script+live.variables;songs.clear();songs.addAll(full);publishedAt=c.published;fetchedAt=System.currentTimeMillis();
        try{JSONArray a=new JSONArray();for(Song song:songs)a.put(cacheSong(song));JSONObject data=new JSONObject().put("version",CACHE_VERSION).put("detail",cachedDetail).put("sourceId",current.sourceId).put("sourceName",current.sourceName).put("fingerprint",live.script+live.variables).put("group",current.group).put("board",current.raw).put("songs",a).put("fetchedAt",fetchedAt).put("publishedAt",publishedAt);prefs.edit().putString("cache",data.toString()).apply();}catch(Exception ignored){}
        finish("已缓存 "+songs.size()+" 首榜单歌曲 · 抽样试播通过 · "+(publishedAt>0?"已选检测范围内最新可用榜单":"来源未注明更新时间"));
    }
    private void finish(String message){if(timeout!=null)main.removeCallbacks(timeout);timeout=null;loading=false;status=message;repo.changed();List<Runnable> done=new ArrayList<>(completions);completions.clear();for(Runnable r:done)r.run();}
    private void restore(){try{
        JSONObject j=new JSONObject(prefs.getString("cache","{}"));if(!fullPageCache(j))return;Source source=repo.source(j.optString("sourceId"));if(!Charts.sourceAvailable(source)||!j.optString("fingerprint").equals(source.script+source.variables))return;
        Charts.Board board=new Charts.Board(source,j.optString("group"),j.getJSONObject("board"));JSONArray a=j.getJSONArray("songs");List<Song> restored=new ArrayList<>();for(int i=0;i<a.length();i++){Song song=restoreSong(a.getJSONObject(i));if(song!=null)restored.add(song);}if(restored.isEmpty())return;
        Charts page=new Charts();page.seed(board,j.getJSONObject("detail"),restored);if(page.songs.isEmpty())return;
        current=board;cachedDetail=j.getJSONObject("detail");cachedFingerprint=j.optString("fingerprint");songs.addAll(page.songs);fetchedAt=j.optLong("fetchedAt");publishedAt=j.optLong("publishedAt");status="上次榜单 · 抽样试播通过，播放时检查线路";
    }catch(Exception ignored){}}
    static JSONObject cacheSong(Song song) throws JSONException {
        JSONObject json=song.json();JSONArray routes=json.getJSONArray("routes");
        for(int i=0;i<song.routes.size();i++){Route route=song.routes.get(i);routes.getJSONObject(i).put("quality",route.quality).put("mimeType",route.mimeType).put("verifiedAt",route.verifiedAt).put("verifiedPlayable",route.verifiedPlayable);}
        return json;
    }
    static Song restoreSong(JSONObject json) throws JSONException {
        Song song=Song.from(json);if(song==null)return null;JSONArray routes=json.getJSONArray("routes");
        for(int i=0;i<song.routes.size();i++){Route route=song.routes.get(i);JSONObject raw=routes.getJSONObject(i);route.quality=raw.optString("quality","standard");route.mimeType=raw.optString("mimeType","");route.verifiedAt=raw.optLong("verifiedAt");route.verifiedPlayable=raw.optBoolean("verifiedPlayable");route.status=route.verifiedPlayable?"播放通过":"待验证";}
        return song;
    }
    static long timestamp(JSONObject raw){if(raw==null)return 0;for(String key:new String[]{"updateTime","updatedAt","publishTime","publishedAt","updateDate","date"}){long value=parseTimestamp(raw.optString(key,""));if(value>0)return value;}return 0;}
    static long plausible(long value){return value>0&&value<=System.currentTimeMillis()+DAY?value:0;}
    static long parseTimestamp(String input){
        if(input==null)return 0;String text=input.trim();try{if(text.matches("\\d{10}|\\d{13}")){long n=Long.parseLong(text);return plausible(text.length()==10?n*1000:n);}
            if(text.matches("\\d{4}-\\d{2}-\\d{2}"))return plausible(LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
            return plausible(Instant.parse(text).toEpochMilli());
        }catch(Exception ignored){return 0;}
    }
}
