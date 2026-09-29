package app.qingting.music;
import android.content.*;
import android.net.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import static app.qingting.music.Models.*;

@androidx.media3.common.util.UnstableApi
public final class MusicRepository {
    public static final List<String> DEFAULT_SUBSCRIPTIONS=Collections.unmodifiableList(Arrays.asList(
        "https://musicfreepluginshub.2020818.xyz/plugins.json",
        "https://music.nairocy.com/plugins.json",
        "https://qwerwhr.github.io/musicfree-plugins/plugins.json",
        "https://gitee.com/kevinr/tvbox/raw/master/musicfree/plugins.json"));
    public interface Listener {void changed();}
    public interface Result<T> {void finish(T value,String error);}
    public final OnlineSheets sheets;
    public final Charts charts=new Charts();public final DailyCharts dailyCharts;private volatile int catalogGeneration;private int catalogPending;
    private ChartFilter chartFilter=new ChartFilter();public boolean filteringCharts;public String chartFilterStatus="";private int filterTotal,filterChecked,filterEmpty,filterUnknown,filterSelection,filterCatalogFailed,filterFailed;private JSONObject refreshedSelectionPage;private String refreshedSelectionKey="";private int refreshedSelectionPageNumber=1;
    private final ScheduledExecutorService chartCacheWork=Executors.newSingleThreadScheduledExecutor();private ScheduledFuture<?> chartArchiveWrite;private ChartArchive chartArchive=new ChartArchive();private LocalFileCache chartDisk;private boolean restoringChartCache=true;private int chartCacheEpoch;
    private boolean manualChart;private long dailyChartShown,catalogAttempt;
    private final ExecutorService chartWork=Executors.newFixedThreadPool(2),chartDetailWork=Executors.newSingleThreadExecutor();
    public final List<Source> sources=new ArrayList<>();
    private LibraryTransactions libraryTransactions;private boolean libraryReadOnly;public LibraryState library=new LibraryState();
    public final List<Song> results=new ArrayList<>(),favorites=new ArrayList<>(),recent=new ArrayList<>();
    public final Map<String,String> searchStates=new LinkedHashMap<>();
    private final Map<String,Integer> pages=new HashMap<>();private final Set<String> ended=new HashSet<>();
    public String query="",notice="";public boolean searching,importing;
    public boolean scanningSources,scanningResults,onlyPlayable;
    public String sourceScanStatus="",resultScanStatus="";
    private volatile int sourceScanToken,resultScanToken;private int sourceScanDone,sourceScanTotal,resultScanDone,resultScanTotal;
    private volatile int generation;private int pending;
    private volatile int visibleGeneration;
    private final List<Song> visibleSongs=new ArrayList<>();
    private final Set<Route> autoAttempted=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Route,Integer> autoActive=new IdentityHashMap<>();
    private final ExecutorService fallbackWork=Executors.newFixedThreadPool(2);
    private final Context context;private final SharedPreferences prefs;private final PluginRuntime runtime;
    private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService work=Executors.newFixedThreadPool(3);
    private VerificationCache verificationCache=new VerificationCache();
    private final ResolutionCache mediaCache=new ResolutionCache(24,60000);
    private final ExecutorService playbackWork=Executors.newFixedThreadPool(2),probeWork=Executors.newFixedThreadPool(2);
    private final ExecutorService validationWork=Executors.newFixedThreadPool(2),lyricWork=Executors.newFixedThreadPool(2);
    private final LinkedHashMap<String,Lyrics> lyricCache=new LinkedHashMap<>(20,.75f,true);
    private final MediaVerifier verifier;
    private final List<Listener> listeners=new ArrayList<>();
    public MusicRepository(Context context) {
        this.context=context;prefs=context.getSharedPreferences("library",Context.MODE_PRIVATE);runtime=new PluginRuntime(context);verifier=new MediaVerifier(context);
        try{JSONArray a=new JSONArray(prefs.getString("sources","[]"));for(int i=0;i<a.length();i++)sources.add(Source.from(a.getJSONObject(i)));}catch(Exception ignored){}
        try{verificationCache=VerificationCache.from(new JSONArray(prefs.getString("verificationCache","[]")),System.currentTimeMillis());}catch(Exception ignored){}
        try{chartFilter=ChartFilter.from(new JSONArray(prefs.getString("emptyCharts","[]")));}catch(Exception ignored){}
        loadSongs("favorites",favorites);loadSongs("recent",recent);
        String saved=prefs.getString("libraryV2",null);
        if(saved!=null)try{JSONObject stored=new JSONObject(saved);if(stored.optInt("version")!=1||stored.optJSONArray("favorites")==null||stored.optJSONArray("playlists")==null)throw new JSONException("不支持或损坏的音乐库");library=LibraryState.from(stored);favorites.clear();favorites.addAll(library.favorites);}catch(Exception e){libraryReadOnly=true;library.favorites.addAll(favorites);notice="音乐库读取失败，已保留原数据并暂停写入";}
        else{try{library=LibraryState.migrateLegacy(LibraryState.songsJson(favorites));favorites.clear();favorites.addAll(library.favorites);saved=library.json().toString();if(!prefs.edit().putString("libraryV2",saved).commit())notice="旧收藏迁移未保存，请检查存储空间";}catch(Exception e){libraryReadOnly=true;notice="旧收藏迁移失败，原数据已保留";}}
        libraryTransactions=new LibraryTransactions(saved==null?"{\"version\":1,\"favorites\":[],\"playlists\":[],\"history\":[]}":saved,Executors.newSingleThreadExecutor(),r->main.post(r),json->prefs.edit().putString("libraryV2",json).commit());
        sheets=new OnlineSheets(()->sources,(source,method,args,valid)->runtime.invoke(source,method,args,valid),Executors.newFixedThreadPool(2),Executors.newSingleThreadExecutor(),r->main.post(r),new LocalFileCache(new java.io.File(context.getCacheDir(),"sheets"),8L*1024*1024,1800000),this::changed);
        dailyCharts=new DailyCharts(context,this);
        chartDisk=new LocalFileCache(new java.io.File(context.getCacheDir(),"charts"),8L*1024*1024,1800000);restoreChartArchive();
    }
    public void addListener(Listener l){listeners.add(l);}public void removeListener(Listener l){listeners.remove(l);}
    public void changed(){syncDailyChart();for(Listener l:new ArrayList<>(listeners))l.changed();}
    public Source source(String id){for(Source s:sources)if(s.id.equals(id))return s;return null;}
    public void save(){try{JSONArray a=new JSONArray();for(Source s:sources)a.put(s.json());prefs.edit().putString("sources",a.toString()).apply();}catch(Exception e){notice="保存失败："+e.getMessage();}changed();}
    private void loadSongs(String key,List<Song> list){try{LibraryState.readSongs(new JSONArray(prefs.getString(key,"[]")),list);}catch(Exception ignored){}}
    private void saveSongs(String key,List<Song> list){try{JSONArray a=new JSONArray();for(Song s:list)a.put(s.json());prefs.edit().putString(key,a.toString()).apply();}catch(Exception e){notice="保存失败";}changed();}
    public boolean liked(Song song){for(Song s:favorites)if(s.key.equals(song.key))return true;return false;}
    public interface LibraryEdit {void apply(LibraryState value) throws Exception;}
    public void editLibrary(LibraryEdit edit){editLibrary(edit,(ok,error)->{});}
    public void editLibrary(LibraryEdit edit,Result<Boolean> completion){if(libraryReadOnly){notice="音乐库异常，已保留原数据，暂不能保存";completion.finish(false,notice);changed();return;}libraryTransactions.submit(edit::apply,(state,error)->{if(error==null){library=state;favorites.clear();favorites.addAll(state.favorites);}else{notice=error;android.widget.Toast.makeText(context,error,android.widget.Toast.LENGTH_LONG).show();}completion.finish(error==null,error);changed();});}
    public void favorite(Song song){Route selected=song.fixedRoute();PlaybackService playing=PlaybackService.instance;if(playing!=null&&playing.current!=null&&playing.current.key.equals(song.key)&&playing.route!=null&&playing.player.isPlaying())selected=playing.route;if(selected==null&&song.routes.size()==1)selected=song.routes.get(0);favorite(song,selected);}
    public void favorite(Song song,Route selected){String key=song.key;boolean remove=liked(song);if(!remove&&selected==null){notice="请先选择收藏的播放来源";changed();return;}Song copy=remove?null:song.snapshot(selected);if(copy!=null)copy.addedAt=System.currentTimeMillis();editLibrary(l->{l.favorites.removeIf(v->v.key.equals(key));if(copy!=null)l.favorites.add(0,copy);});}
    public void pinFavorite(Song song,Route selected){pinFavorite(song,selected,(ok,error)->{});}
    public void pinFavorite(Song song,Route selected,Result<Boolean> completion){Song updated=song.snapshot(selected);String key=song.key;editLibrary(l->{for(int i=0;i<l.favorites.size();i++)if(l.favorites.get(i).key.equals(key)){updated.addedAt=l.favorites.get(i).addedAt;l.favorites.set(i,updated);return;}throw new IllegalArgumentException("歌曲已不在收藏中");},completion);}
    public int sortPreference(String key){return prefs.getInt("sort:"+key,0);}
    public void sortPreference(String key,int value){prefs.edit().putInt("sort:"+key,value).apply();}
    public void played(Song song){recent.removeIf(s->s.key.equals(song.key));recent.add(0,song);while(recent.size()>60)recent.remove(recent.size()-1);saveSongs("recent",recent);}
    boolean online(){ConnectivityManager m=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);NetworkCapabilities caps=m==null?null:m.getNetworkCapabilities(m.getActiveNetwork());return caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);}
    private void success(Source s,String status,long started){s.failures=0;s.checkedAt=System.currentTimeMillis();s.latency=s.checkedAt-started;s.message=status;}
    private void failure(Source s,String error){if(online()){s.failures++;s.failedAt=System.currentTimeMillis();}s.message=error;s.checkedAt=System.currentTimeMillis();}
    public void importUrl(String input) {
        importUrl(input,false,null);
    }
    public void ensureDefaultSubscriptions(){ensureSubscriptions(DEFAULT_SUBSCRIPTIONS);}
    void ensureSubscriptions(List<String> urls){
        if(importing)return;
        for(String url:urls)if(!prefs.getBoolean("defaultAttempted:"+url,false)){
            importUrl(url,true,()->ensureSubscriptions(urls));return;
        }
    }
    public String defaultSubscriptionStatus(String url){return prefs.getString("defaultStatus:"+url,"等待首次导入");}
    public void retryDefaultSubscription(String url){importUrl(url,true,this::ensureDefaultSubscriptions);}
    private void importUrl(String input,boolean onlyMissing,Runnable finished) {
        if(importing)return;String url=input.trim();if(!url.startsWith("http://")&&!url.startsWith("https://")){notice="请输入完整 HTTP(S) 订阅地址";changed();return;}
        Set<String> knownUrls=new HashSet<>();if(onlyMissing){for(Source source:sources)knownUrls.add(source.url);prefs.edit().putString("defaultStatus:"+url,"正在导入…").apply();}
        importing=true;notice="正在读取订阅…";changed();
        work.execute(()->{
            List<Source> imported=new ArrayList<>();List<String> errors=new ArrayList<>();int skipped=0;
            try{
                String text=Http.get(url);JSONArray entries;
                if(text.trim().startsWith("{"))entries=new JSONObject(text).getJSONArray("plugins");
                else if(text.trim().startsWith("["))entries=new JSONArray(text);
                else entries=new JSONArray().put(new JSONObject().put("url",url));
                if(entries.length()>80)throw new Exception("单次最多导入 80 个插件，请拆分订阅");
                for(int i=0;i<entries.length();i++){
                    JSONObject entry=entries.getJSONObject(i);String pluginUrl=entry.getString("url");
                    if(onlyMissing&&knownUrls.contains(pluginUrl)){skipped++;continue;}
                    try{Source s=new Source(pluginUrl);s.subscription=url;s.script=pluginUrl.equals(url)?text:Http.get(pluginUrl);
                        // Preserve saved config before validating an update.
                        String saved=prefs.getString("sources","[]");JSONArray old=new JSONArray(saved);
                        for(int k=0;k<old.length();k++){JSONObject o=old.getJSONObject(k);if(pluginUrl.equals(o.optString("url")))s.variables=Source.from(o).variables;}
                        JSONObject meta=(JSONObject)runtime.invoke(s,"metadata",new JSONArray());
                        String types=meta.getJSONArray("supportedSearchType").toString();s.music=meta.optBoolean("search")&&types.contains("\"music\"");s.lyricSearch=meta.optBoolean("search")&&types.contains("\"lyric\"");
                        s.topLists=meta.optBoolean("topLists");if(!s.music&&!s.lyricSearch&&!s.topLists&&!meta.optBoolean("sheets"))throw new Exception("没有歌曲、歌单、歌词或排行榜能力");
                        s.name=meta.getString("platform");s.version=meta.optString("version");s.fields=meta.optJSONArray("userVariables");if(s.fields==null)s.fields=new JSONArray();
                        s.message="插件就绪 · 尚未搜索";imported.add(s);knownUrls.add(pluginUrl);
                        int count=i+1;main.post(()->{notice="正在检查插件 "+count+" / "+entries.length();changed();});
                    }catch(Exception e){errors.add(entry.optString("name",pluginUrl)+"："+e.getMessage());}
                }
            }catch(Exception e){errors.add(e.getMessage());}
            int existing=skipped;
            main.post(()->{
                for(Source s:imported){Source old=source(s.id);if(old!=null){s.enabled=old.enabled;s.variables=old.variables;s.playbackHealth=old.playbackHealth;s.playbackMessage=old.playbackMessage;sources.remove(old);}sources.add(s);}
                String summary=onlyMissing?"新增 "+imported.size()+" · 已有 "+existing+" · 失败 "+errors.size():"已导入 / 更新 "+imported.size()+" 个源";
                if(onlyMissing)prefs.edit().putBoolean("defaultAttempted:"+url,true).putString("defaultStatus:"+url,summary).apply();
                importing=false;notice=summary+(errors.isEmpty()?"":"\n"+String.join("\n",errors));save();if(finished!=null)finished.run();
            });
        });
    }
    public int lyricDelay(String songKey){return prefs.getInt("lyricDelay:"+songKey,0);}
    public void setLyricDelay(String songKey,int delay){prefs.edit().putInt("lyricDelay:"+songKey,Math.max(-10000,Math.min(10000,delay))).apply();changed();}
    public void ensureCharts(){
        if(restoringChartCache)return;
        syncDailyChart();dailyCharts.ensureFresh();
        if((charts.boards.isEmpty()||catalogAttempt==0)&&!charts.loadingCatalog&&System.currentTimeMillis()-catalogAttempt>60_000)refreshCharts();
    }
    private void syncDailyChart(){
        if(dailyCharts==null||manualChart||!dailyCharts.hasUsableSource()||dailyChartShown==dailyCharts.fetchedAt||chartHidden(dailyCharts.current))return;
        dailyCharts.seed(charts);rememberChart();restoreVerification(charts.songs);dailyChartShown=dailyCharts.fetchedAt;
    }
    public boolean isAutomaticChart(){return !manualChart;}
    public void useDailyChart(){manualChart=false;dailyChartShown=0;syncDailyChart();dailyCharts.refresh(true,null);changed();}
    public boolean chartHidden(Charts.Board board){Source source=board==null?null:source(board.sourceId);return source!=null&&chartFilter.hidden(chartFilter.key(source,board));}
    public String hiddenChartReport(){StringBuilder out=new StringBuilder();for(Charts.Board board:charts.boards){Source source=source(board.sourceId);if(!Charts.sourceAvailable(source))continue;String reason=chartFilter.reason(chartFilter.key(source,board),System.currentTimeMillis());if(!reason.isEmpty())out.append(board.sourceName).append(" · ").append(board.title).append("\n").append(reason).append("\n\n");}return out.length()==0?"暂无隐藏榜单":"刷新榜单会重新检查；失败状态最多保留 30 分钟。\n\n"+out;}
    public void refreshAndFilterCharts(){if(filteringCharts){cancelChartFiltering();return;}if(charts.loadingCatalog){catalogGeneration++;charts.loadingCatalog=false;}refreshCharts(true);}
    public void cancelChartFiltering(){if(!filteringCharts)return;catalogGeneration++;catalogPending=0;charts.loadingCatalog=false;filteringCharts=false;chartFilterStatus="已取消 · 已检查 "+filterChecked+" / "+filterTotal+" · 过滤 "+filterEmpty+" 个空榜";finishChartSelection(false);changed();}
    public void refreshCharts(){refreshCharts(false);}
    private void refreshCharts(boolean filter){
        if(charts.loadingCatalog)return;if(!online()){chartFilterStatus="网络不可用，请联网后刷新；保留上次结果";changed();return;}catalogAttempt=System.currentTimeMillis();int token=++catalogGeneration;charts.sourceStates.clear();List<Source> selected=new ArrayList<>();
        filteringCharts=filter;if(filter){invalidateChartArchive(null);charts.clearCache();manualChart=true;filterTotal=filterChecked=filterEmpty=filterUnknown=filterCatalogFailed=filterFailed=0;refreshedSelectionPage=null;refreshedSelectionKey="";filterSelection=charts.token();chartFilterStatus="正在读取榜单目录…";}
        for(Source source:sources)if(Charts.sourceAvailable(source))selected.add(source);
        catalogPending=selected.size();charts.loadingCatalog=catalogPending>0;
        for(Source source:selected){String fingerprint=source.script+source.variables.toString();charts.sourceStates.put(source.id,source.name+" · 读取榜单中");
            chartWork.execute(()->{List<Charts.Board> boards=new ArrayList<>();String error=null;boolean supported=false,cancelled=false;
                for(int attempt=1;attempt<=(filter?3:1);attempt++)try{JSONObject metadata=(JSONObject)runtime.invoke(source,"metadata",new JSONArray(),()->token==catalogGeneration);supported=metadata.optBoolean("topLists");
                    if(supported){Object value=runtime.invoke(source,"getTopLists",new JSONArray(),()->token==catalogGeneration);if(!(value instanceof JSONArray))throw new JSONException("榜单返回结构不兼容");boards=Charts.parseBoards(source,(JSONArray)value);}error=null;break;
                }catch(Exception e){error="检查 "+attempt+" 次："+e.getMessage();cancelled=e instanceof CancellationException;if(cancelled||!online())break;}
                List<Charts.Board> found=boards;String problem=error;boolean capability=supported,interrupted=cancelled;
                main.post(()->{if(token!=catalogGeneration)return;if(filter){filterTotal+=found.size();if(problem!=null)filterCatalogFailed++;}
                    if(source(source.id)==source&&Charts.sourceAvailable(source)&&fingerprint.equals(source.script+source.variables.toString())){
                        if(problem==null)source.topLists=capability;if(filter&&problem!=null&&!interrupted&&online()){for(Charts.Board old:charts.boards)if(old.sourceId.equals(source.id))chartFilter.failure(chartFilter.key(source,old),"目录读取失败："+problem,System.currentTimeMillis());prefs.edit().putString("emptyCharts",chartFilter.json().toString()).apply();}if(problem==null){charts.boards.removeIf(b->b.sourceId.equals(source.id));charts.boards.addAll(found);}charts.sourceStates.put(source.id,source.name+" · "+(problem!=null?"榜单读取失败："+problem:!capability?"未提供排行榜":found.size()+" 个榜单"));
                        if(filter){updateChartFilterStatus(false);}else if(charts.current==null){finishChartSelection(false);}
                    }else charts.sourceStates.put(source.id,source.name+" · 配置已变化，请刷新");changed();
                });
                if(filter&&problem==null)for(Charts.Board board:found){
                    if(token!=catalogGeneration)break;JSONObject page=null;String checkError=null;boolean cancelledCheck=false;int checkedPage=1;
                    try{JSONObject raw=new JSONObject(board.raw.toString());
                        for(int pageNumber=1;pageNumber<=5;pageNumber++){
                            checkedPage=pageNumber;page=null;
                            for(int attempt=1;attempt<=3;attempt++){
                                if(token!=catalogGeneration)throw new CancellationException("已取消");int attemptNumber=attempt,currentPage=pageNumber;
                                main.post(()->{if(token==catalogGeneration){chartFilterStatus="检查 "+board.sourceName+" · "+board.title+" · 第 "+currentPage+" 页 / 第 "+attemptNumber+" 次";changed();}});
                                try{Object value=runtime.invoke(source,"getTopListDetail",new JSONArray().put(raw).put(pageNumber),()->token==catalogGeneration);if(!(value instanceof JSONObject)||((JSONObject)value).optJSONArray("musicList")==null)throw new JSONException("榜单返回结构不兼容");
                                    JSONObject candidate=(JSONObject)value;JSONArray items=candidate.getJSONArray("musicList");if(items.length()>0){boolean valid=false;for(int i=0;i<items.length();i++){JSONObject song=items.optJSONObject(i);if(song!=null&&(song.has("id")||!song.optString("title").isEmpty()))valid=true;}if(!valid)throw new JSONException("榜单歌曲数据异常");}
                                    JSONObject continuation=new JSONObject(raw.toString());JSONObject delta=candidate.optJSONObject("topListItem");if(delta!=null)for(Iterator<String> keys=delta.keys();keys.hasNext();){String key=keys.next();continuation.put(key,delta.get(key));}candidate.put("topListItem",continuation);page=candidate;checkError=null;break;
                                }catch(CancellationException e){throw e;}catch(Exception e){checkError="检查 "+attempt+" 次："+e.getMessage();if(!online())break;}
                            }
                            if(page==null)break;
                            if(page.getJSONArray("musicList").length()>0||page.optBoolean("isEnd",true))break;
                            if(pageNumber==5){page=null;checkError="连续 5 页为空且未结束，请稍后刷新重试";break;}
                            JSONObject supplement=page.optJSONObject("topListItem");if(supplement!=null)for(Iterator<String> keys=supplement.keys();keys.hasNext();){String key=keys.next();raw.put(key,supplement.get(key));}
                        }
                    }catch(Exception e){checkError=e.getMessage();cancelledCheck=e instanceof CancellationException;}
                    JSONObject result=page;String reason=checkError;boolean skipped=cancelledCheck;int resultPage=checkedPage;main.post(()->{if(token!=catalogGeneration)return;filterChecked++;
                        if(source(source.id)==source&&Charts.sourceAvailable(source)&&fingerprint.equals(source.script+source.variables.toString())){int verdict=0;String key=chartFilter.key(source,board);charts.forget(key);if(!skipped&&online()){if(reason!=null){chartFilter.failure(key,reason,System.currentTimeMillis());verdict=-2;}else{verdict=chartFilter.record(key,result,System.currentTimeMillis(),resultPage);if(verdict>0)persistChartPage(board,resultPage,result);if(verdict==0&&(result==null||result.optJSONArray("musicList")==null||result.optJSONArray("musicList").length()>0)){chartFilter.failure(key,"榜单歌曲数据异常",System.currentTimeMillis());verdict=-2;}}}if(verdict==-2)filterFailed++;else if(verdict<0)filterEmpty++;else if(verdict==0)filterUnknown++;else if(charts.token()==filterSelection&&charts.current!=null&&charts.current.sourceId.equals(board.sourceId)&&charts.current.raw.toString().equals(board.raw.toString())){refreshedSelectionPage=result;refreshedSelectionPageNumber=resultPage;refreshedSelectionKey=chartFilter.key(source,board);}prefs.edit().putString("emptyCharts",chartFilter.json().toString()).apply();}else filterUnknown++;
                        updateChartFilterStatus(false);changed();
                    });
                }
                main.post(()->{if(token!=catalogGeneration)return;catalogPending--;charts.loadingCatalog=catalogPending>0;if(!charts.loadingCatalog){if(filter){filteringCharts=false;updateChartFilterStatus(true);}finishChartSelection(filter);}changed();});
            });
        }
        if(!charts.loadingCatalog){if(filter){filteringCharts=false;chartFilterStatus="没有启用的榜单来源";}finishChartSelection(false);}changed();
    }
    private void updateChartFilterStatus(boolean finished){chartFilterStatus=(finished?"刷新完成":"检查榜单 "+filterChecked+" / "+filterTotal)+" · 空榜 "+filterEmpty+" · 暂不可用 "+filterFailed+(filterUnknown>0?" · "+filterUnknown+" 个待重试":"")+(filterCatalogFailed>0?" · "+filterCatalogFailed+" 个来源读取失败":"");}
    private boolean seedFilteredPage(Charts.Board board){Source source=source(board.sourceId);if(source==null)return false;JSONObject page=chartFilter.firstPage(chartFilter.key(source,board),System.currentTimeMillis());if(page==null)return false;try{charts.seedPage(board,page,chartFilter.firstPageNumber(chartFilter.key(source,board)));rememberChart();restoreVerification(charts.songs);return true;}catch(Exception ignored){return false;}}
    private void finishChartSelection(boolean refreshed){
        if(charts.current!=null){Source currentSource=source(charts.current.sourceId);if(!Charts.sourceAvailable(currentSource)||chartHidden(charts.current)){cancelVisibleChecks();charts.clearSelection();}}
        if(charts.current!=null){if(refreshed&&charts.token()==filterSelection){cancelVisibleChecks();if(refreshedSelectionPage!=null&&refreshedSelectionKey.equals(chartFilter.key(source(charts.current.sourceId),charts.current)))try{charts.seedPage(charts.current,refreshedSelectionPage,refreshedSelectionPageNumber);rememberChart();restoreVerification(charts.songs);}catch(Exception ignored){}else seedFilteredPage(charts.current);}return;}
        Charts.Board fallback=null;for(Charts.Board board:charts.boards){Source source=source(board.sourceId);if(!Charts.sourceAvailable(source)||chartHidden(board))continue;if(fallback==null)fallback=board;if(seedFilteredPage(board))return;}
        if(fallback!=null){charts.select(fallback);loadChartPage(false);}
    }
    private void writeChartArchive(){if(chartArchiveWrite!=null)chartArchiveWrite.cancel(false);chartArchiveWrite=null;chartDisk.put("recent",chartArchive.boundedBytes(8*1024*1024),System.currentTimeMillis());}
    private void scheduleChartArchiveWrite(){if(chartArchiveWrite!=null)chartArchiveWrite.cancel(false);chartArchiveWrite=chartCacheWork.schedule(this::writeChartArchive,250,TimeUnit.MILLISECONDS);}
    private void touchChartArchive(String key){chartCacheWork.execute(()->{chartArchive.touch(key);scheduleChartArchiveWrite();});}
    private void persistChartPage(Charts.Board board,int page,JSONObject data){Source s=source(board.sourceId);if(s==null)return;String key=chartFilter.key(s,board);long now=System.currentTimeMillis();chartCacheWork.execute(()->{try{chartArchive.record(key,board,page,data,now);scheduleChartArchiveWrite();}catch(Exception ignored){}});}
    private void invalidateChartArchive(String key){chartCacheEpoch++;chartCacheWork.execute(()->{if(key==null)chartArchive.clear();else chartArchive.remove(key);writeChartArchive();});}
    private void restoreChartArchive(){int epoch=chartCacheEpoch;chartCacheWork.execute(()->{try{byte[] bytes=chartDisk.get("recent",System.currentTimeMillis());if(bytes!=null)chartArchive=ChartArchive.from(new JSONArray(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)),System.currentTimeMillis());}catch(Exception ignored){}List<ChartArchive.Entry> saved=chartArchive.entries(System.currentTimeMillis());main.post(()->{restoringChartCache=false;if(epoch==chartCacheEpoch){ChartArchive.Entry last=null;for(ChartArchive.Entry entry:saved){Charts.Board board=entry.charts.current;Source s=source(board.sourceId);if(!Charts.sourceAvailable(s)||chartHidden(board)||!entry.key.equals(chartFilter.key(s,board)))continue;charts.installCache(entry.key,entry.charts,entry.at);if(charts.boards.stream().noneMatch(b->b.sourceId.equals(board.sourceId)&&b.raw.toString().equals(board.raw.toString())))charts.boards.add(board);last=entry;}if(last!=null&&!manualChart){manualChart=true;charts.restore(last.charts.current,last.key,System.currentTimeMillis());restoreVerification(charts.songs);}}changed();ensureCharts();});});}
    private void rememberChart(){Source s=charts.current==null?null:source(charts.current.sourceId);if(s!=null)charts.remember(chartFilter.key(s,charts.current),System.currentTimeMillis());}
    public void openChart(Charts.Board board){manualChart=true;if(charts.current!=null&&charts.current.sourceId.equals(board.sourceId)&&charts.current.raw.toString().equals(board.raw.toString())&&(charts.loadingSongs||charts.page>0))return;cancelVisibleChecks();Source s=source(board.sourceId);if(Charts.sourceAvailable(s)&&!chartHidden(board)&&(charts.restore(board,chartFilter.key(s,board),System.currentTimeMillis())||seedFilteredPage(board))){touchChartArchive(chartFilter.key(s,board));restoreVerification(charts.songs);changed();return;}charts.select(board);loadChartPage(false);}
    public void closeChart(){charts.clearSelection();changed();}
    public void loadMoreChart(){manualChart=true;loadChartPage(false);}
    public void refreshCurrentChart(){manualChart=true;cancelVisibleChecks();if(charts.current!=null){Source s=source(charts.current.sourceId);if(s!=null){String key=chartFilter.key(s,charts.current);charts.forget(key);invalidateChartArchive(key);}}loadChartPage(true);}
    private void loadChartPage(boolean refresh){
        if(charts.current==null||charts.loadingSongs||(!refresh&&charts.end))return;
        Source s=source(charts.current.sourceId);if(!Charts.sourceAvailable(s)){charts.detailStatus="此来源已停用或过滤，请到订阅源恢复";changed();return;}
        int token=charts.token(),page=refresh?1:charts.page+1;Charts.Board board=charts.current;String fingerprint=s.script+s.variables.toString();JSONObject raw;
        try{raw=new JSONObject((refresh?board.raw:charts.detailRaw).toString());}catch(JSONException e){charts.detailStatus="榜单数据异常";changed();return;}
        charts.loadingSongs=true;charts.detailStatus="正在加载第 "+page+" 页…";changed();
        chartDetailWork.execute(()->{JSONObject result=null;String error=null;
            try{if(token!=charts.token())return;Object value=runtime.invoke(s,"getTopListDetail",new JSONArray().put(raw).put(page),()->token==charts.token());if(!(value instanceof JSONObject))throw new JSONException("榜单详情返回结构不兼容");result=(JSONObject)value;}catch(Exception e){error=e.getMessage();}
            JSONObject value=result;String problem=error;main.post(()->{if(token!=charts.token())return;charts.loadingSongs=false;
                if(source(s.id)!=s||!Charts.sourceAvailable(s)||!fingerprint.equals(s.script+s.variables.toString()))charts.detailStatus="来源配置已变化，请重新打开榜单";
                else if(problem!=null){charts.detailStatus="加载失败，可重试："+problem;if(online()&&!problem.contains("取消")&&!problem.contains("优先播放")){chartFilter.failure(chartFilter.key(s,board),problem,System.currentTimeMillis());prefs.edit().putString("emptyCharts",chartFilter.json().toString()).apply();cancelVisibleChecks();charts.clearSelection();for(Charts.Board candidate:charts.boards)if(Charts.sourceAvailable(source(candidate.sourceId))&&!chartHidden(candidate)&&seedFilteredPage(candidate))break;}}
                else try{if(refresh){Charts staged=new Charts();staged.select(board);staged.append(staged.token(),1,value);charts.seed(board,value,staged.songs);}else charts.append(token,page,value);rememberChart();persistChartPage(board,page,value);restoreVerification(charts.songs);}catch(Exception e){charts.detailStatus="榜单数据不兼容："+e.getMessage();}changed();
            });
        });
    }
    public void check(Source s) {
        s.message="正在复检搜索…";changed();long start=System.currentTimeMillis();
        work.execute(()->{String error=null;try{Object result=runtime.invoke(s,"search",new JSONArray().put(query.isEmpty()?"音乐":query).put(1).put("music"));if(!(result instanceof JSONObject)||((JSONObject)result).optJSONArray("data")==null)throw new Exception("搜索返回结构不兼容");}catch(Exception e){error=e.getMessage();}
            String err=error;main.post(()->{if(err==null)success(s,"搜索正常 · 播放需逐曲检测",start);else failure(s,err);save();});});
    }
    public void pauseSearch(){generation++;pending=0;searching=false;cancelResultScan();}
    public void search(String input){String q=input.trim();if(q.isEmpty()){generation++;cancelVisibleChecks();cancelResultScan();query="";results.clear();searchStates.clear();searching=false;changed();return;}cancelVisibleChecks();autoAttempted.clear();cancelResultScan();resultScanStatus="";onlyPlayable=false;generation++;editLibrary(l->l.remember(q));query=q;results.clear();pages.clear();ended.clear();searchStates.clear();pending=0;searching=false;loadMore();}
    public boolean hasMore(){for(Source s:sources)if(s.music&&s.enabled&&!s.hidden()&&!ended.contains(s.id))return true;return false;}
    public void loadMore(){
        if(searching||query.isEmpty())return;int token=generation;String q=query;
        List<Source> eligible=new ArrayList<>();for(Source s:sources){if(s.music&&s.enabled&&!s.hidden()&&!ended.contains(s.id))eligible.add(s);else if(s.hidden())searchStates.put(s.id,s.name+" · 已过滤，可到订阅源恢复");}
        eligible.sort(Comparator.comparingLong(v->prefs.getLong("searchMs:"+v.id,5000)));pending=eligible.size();searching=pending>0;notice=eligible.isEmpty()?"暂无可搜索的订阅源":"";changed();
        for(Source s:eligible){int page=pages.getOrDefault(s.id,0)+1;searchStates.put(s.id,s.name+" · 搜索中");long start=System.currentTimeMillis();
            work.execute(()->{if(token!=generation)return;JSONObject data=null;String error=null;try{Object value=runtime.invoke(s,"search",new JSONArray().put(q).put(page).put("music"),()->token==generation);if(!(value instanceof JSONObject)||((JSONObject)value).optJSONArray("data")==null)throw new Exception("搜索返回结构不兼容");data=(JSONObject)value;}catch(Exception e){error=e.getMessage();}
                JSONObject response=data;String err=error;main.post(()->{
                    if(token!=generation)return;
                    if(err!=null){if(!err.contains("已取消")&&!err.contains("优先播放"))failure(s,err);searchStates.put(s.id,s.name+" · "+err);ended.add(s.id);}
                    else{JSONArray songs=response.optJSONArray("data");success(s,"搜索正常",start);prefs.edit().putLong("searchMs:"+s.id,System.currentTimeMillis()-start).apply();pages.put(s.id,page);if(response.optBoolean("isEnd")||songs.length()==0)ended.add(s.id);
                        searchStates.put(s.id,s.name+" · "+songs.length()+" 首 · "+String.format(java.util.Locale.ROOT,"%.1fs",s.latency/1000.0));
                        boolean first=results.isEmpty();for(int i=0;i<songs.length();i++){JSONObject raw=songs.optJSONObject(i);if(raw==null)continue;Song song=new Song(new Route(s,raw));Song existing=null;for(Song item:results)if(SongPolicy.sameRecording(item,song)||(item.routes.get(0).sourceId.equals(s.id)&&item.routes.get(0).raw.toString().equals(raw.toString()))){existing=item;break;}
                            if(existing==null){if(results.stream().anyMatch(v->v.key.equals(song.key)))song.key+="\u001f"+s.id+"\u001f"+raw.optString("duration","unknown");results.add(song);}
                            else{boolean duplicate=false;for(Route r:existing.routes)if(r.sourceId.equals(s.id)&&r.raw.toString().equals(raw.toString()))duplicate=true;if(!duplicate){existing.routes.add(song.routes.get(0));if(PlaybackService.instance!=null)PlaybackService.instance.queueContentChanged(existing);}}}if(first)results.sort(Comparator.comparingInt(v->SongPolicy.relevance(v,query)));
                    }
                    pending--;searching=pending>0;save();
                });
            });
        }changed();
    }
    public void resolve(Route route,boolean probe,Result<Media> callback){
        resolve(route,probe,()->true,callback);
    }
    public int playableCount(Song song){int count=0;long now=System.currentTimeMillis();for(Route route:song.routes)if(eligible(route)&&SongPolicy.recentlyPlayable(route,now))count++;return count;}
    public List<Song> sortedResults(){return new ArrayList<>(results);}
    public String availability(Song song){int ready=playableCount(song);if(ready>0)return "✓ 已试播通过 · "+ready+" 条可播线路";for(Route route:song.routes)if(route.verifying)return "正在检查能否播放…";for(Route route:song.routes)if(eligible(route))return "待检查 · 点击播放可自动选线";return "暂未找到可播线路 · 播放时自动补搜";}
    private boolean restoreVerification(List<Song> songs){boolean restored=false;long now=System.currentTimeMillis();for(Song song:songs)for(Route route:song.routes){Source source=source(route.sourceId);if(source!=null){long before=route.verifiedAt;String status=route.status,error=route.error;verificationCache.apply(source,route,now);if(!error.isEmpty()&&route.error.isEmpty())autoAttempted.remove(route);restored|=before!=route.verifiedAt||!status.equals(route.status)||!error.equals(route.error);}}return restored;}
    public void verifyVisible(List<Song> songs){boolean restored=restoreVerification(songs);Set<Route> desired=Collections.newSetFromMap(new IdentityHashMap<>());for(Song song:songs)desired.addAll(song.routes);if(autoActive.keySet().stream().anyMatch(r->!desired.contains(r)))cancelVisibleChecks();visibleSongs.clear();visibleSongs.addAll(songs);pumpVisibleChecks();if(restored)changed();}
    public void cancelVisibleChecks(){visibleGeneration++;visibleSongs.clear();for(Map.Entry<Route,Integer> entry:autoActive.entrySet()){Route route=entry.getKey();if(route.probeVersion==entry.getValue()){route.verifying=false;route.probeVersion++;autoAttempted.remove(route);}}autoActive.clear();}
    private void pumpVisibleChecks(){
        if(!online()||scanningResults||scanningSources)return;
        PlaybackService playing=PlaybackService.instance;if(playing!=null&&playing.isRecovering())return;
        int token=visibleGeneration;
        for(Song song:visibleSongs){
            if(autoActive.size()>=2)return;Route route=SongPolicy.nextProbe(song,this::eligible,autoAttempted,System.currentTimeMillis());if(route==null)continue;
            int version=route.probeVersion+1;autoAttempted.add(route);autoActive.put(route,version);
            deepVerify(route,()->token==visibleGeneration&&route.probeVersion==version,(media,error)->{if(token!=visibleGeneration)return;autoActive.remove(route);if((media!=null&&error==null)||!online())autoAttempted.remove(route);main.post(this::pumpVisibleChecks);});
        }
    }
    public void findAlternatives(Song song,Set<String> excluded,java.util.function.BooleanSupplier valid,Result<Route> found,Runnable finished){
        List<Source> candidates=new ArrayList<>();for(Source s:sources)if(s.music&&s.enabled&&!s.hidden()&&!excluded.contains(s.id))candidates.add(s);
        if(candidates.isEmpty()){finished.run();return;}int[] remaining={candidates.size()};
        for(Source source:candidates){String fingerprint=source.script+source.variables.toString();fallbackWork.execute(()->{
            if(!valid.getAsBoolean())return;Route match=null;
            try{Object response=runtime.invoke(source,"search",new JSONArray().put(song.title+" "+song.artist).put(1).put("music"),valid);JSONArray data=response instanceof JSONObject?((JSONObject)response).optJSONArray("data"):null;
                if(data!=null)for(int i=0;i<data.length();i++){JSONObject raw=data.optJSONObject(i);if(raw==null)continue;Route route=new Route(source,raw);if(SongPolicy.sameRecording(song,new Song(route))){match=route;break;}}
            }catch(Exception ignored){}
            Route result=match;main.post(()->{if(!valid.getAsBoolean())return;remaining[0]--;if(result!=null&&source(source.id)==source&&source.enabled&&!source.hidden()&&fingerprint.equals(source.script+source.variables.toString()))found.finish(result,null);if(remaining[0]==0&&valid.getAsBoolean())finished.run();});
        });}
    }
    public void resolve(Route route,boolean probe,java.util.function.BooleanSupplier valid,Result<Media> callback){
        Source s=source(route.sourceId);if(s==null){callback.finish(null,"订阅源已不存在");return;}
        if(probe&&route.playbackActive){callback.finish(null,null);return;}
        int probeToken=probe?++route.probeVersion:route.probeVersion;
        route.status=probe?"检测中":"解析中";changed();
        (probe?probeWork:playbackWork).execute(()->{if(!valid.getAsBoolean())return;Media media=null;String error=null;try{media=resolveMedia(s,route,probe,false,valid);}catch(Exception e){error=e.getMessage();}
            Media m=media;String err=error;main.post(()->{if(!valid.getAsBoolean())return;if(!probe||(!route.playbackActive&&probeToken==route.probeVersion&&!route.verifiedPlayable)){route.status=err!=null&&!online()?"网络不可用，待重试":err==null?(probe?"链路可达":"等待播放"):"该曲失败";route.error=err==null||!online()?"":err;route.verifiedAt=System.currentTimeMillis();if(m!=null){route.mimeType=m.mimeType;route.quality=m.quality;cacheLyrics(new Song(route),route,m);}changed();}callback.finish(err==null?m:null,err);});});
    }
    public void verify(Song song){for(Route r:song.routes)if(!r.verifying)deepVerify(r,()->true,(m,e)->{});}
    Object dailyInvoke(Source source,String method,JSONArray args) throws Exception{return runtime.invoke(source,method,args);}
    Media dailyVerify(Source source,Route route,java.util.function.BooleanSupplier valid) throws Exception{return resolveMedia(source,route,true,true,valid);}

    private Media resolveMedia(Source source,Route route,boolean inspect,boolean decode,java.util.function.BooleanSupplier valid) throws Exception {
        Exception last=new java.io.IOException("未返回音源");
        for(String quality:route.qualities(decode)){
            if(!valid.getAsBoolean())throw new CancellationException("检测已取消");
            try{String cacheKey=mediaKey(source,route,quality);Media cached=mediaCache.get(cacheKey,System.currentTimeMillis());if(cached!=null){try{if(inspect)Http.probe(cached,valid);if(decode)verifier.verify(cached,valid);return cached;}catch(Exception expired){mediaCache.remove(cacheKey);if(!valid.getAsBoolean())throw new CancellationException("已取消");}}
                Object value=runtime.invoke(source,"getMediaSource",new JSONArray().put(route.raw).put(quality),valid);if(!(value instanceof JSONObject))throw new java.io.IOException("该音质未返回音源");
                Media media=new Media((JSONObject)value);media.quality=quality;if(media.mimeType.isEmpty())media.mimeType=route.mimeType;
                if(inspect)Http.probe(media,valid);if(decode)verifier.verify(media,valid);if(valid.getAsBoolean())mediaCache.put(cacheKey,media,System.currentTimeMillis());return media;
            }catch(CancellationException canceled){throw canceled;}catch(Exception e){last=e;}
        }throw last;
    }
    private String mediaKey(Source source,Route route,String quality){return source.id+":"+source.script.hashCode()+":"+source.variables.toString()+":"+route.raw+":"+quality;}
    public void invalidateMedia(Route route){Source s=source(route.sourceId);if(s!=null){verificationCache.remove(s,route);persistVerification();}if(s!=null)for(String q:new String[]{"standard","low"})mediaCache.remove(mediaKey(s,route,q));}
    public void prioritize(Route route){if(route!=null)runtime.prioritize(route.sourceId);}
    public long sourceRank(Song song,Route route){String best=prefs.getString("best:"+song.key,"");long age=System.currentTimeMillis()-prefs.getLong("bestAt:"+song.key,0);Source source=source(route.sourceId);String config=source==null?"":Integer.toHexString((source.script+source.variables.toString()).hashCode());if(!config.equals(prefs.getString("playConfig:"+route.sourceId,"")))return 5000;if(best.equals(route.sourceId)&&age<7L*86400000)return -100000;return prefs.getLong("playMs:"+route.sourceId,5000);}
    public void recordStart(Song song,Route route,long elapsed){if(route==null)return;Source source=source(route.sourceId);android.content.SharedPreferences.Editor edit=prefs.edit().putString("best:"+song.key,route.sourceId).putLong("bestAt:"+song.key,System.currentTimeMillis()).putLong("playMs:"+route.sourceId,elapsed);if(source!=null)edit.putString("playConfig:"+route.sourceId,Integer.toHexString((source.script+source.variables.toString()).hashCode()));try{JSONArray old=new JSONArray(prefs.getString("bestKeys","[]")),next=new JSONArray();next.put(song.key);for(int i=0;i<old.length();i++){String key=old.optString(i);if(key.equals(song.key))continue;if(next.length()<200)next.put(key);else edit.remove("best:"+key).remove("bestAt:"+key);}edit.putString("bestKeys",next.toString());}catch(Exception ignored){}edit.apply();recordMetric(elapsed,true);}
    public void recordMetric(long elapsed,boolean success){try{JSONArray a=new JSONArray(prefs.getString("startMetrics","[]"));JSONArray out=new JSONArray();for(int i=Math.max(0,a.length()-99);i<a.length();i++)out.put(a.get(i));out.put(new JSONObject().put("ms",elapsed).put("success",success).put("at",System.currentTimeMillis()));prefs.edit().putString("startMetrics",out.toString()).apply();}catch(Exception ignored){}}
    public String metrics(){try{JSONArray a=new JSONArray(prefs.getString("startMetrics","[]"));List<Long> times=new ArrayList<>();int failed=0;for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);if(v.optBoolean("success"))times.add(v.optLong("ms"));else failed++;}Collections.sort(times);return "本地最近 "+a.length()+" 次起播 · 失败 "+failed+(times.isEmpty()?"":"\n成功样本中位数 "+times.get(times.size()/2)+"ms · P95 "+times.get(Math.min(times.size()-1,(int)Math.ceil(times.size()*.95)-1))+"ms");}catch(Exception e){return "暂无起播统计";}}
    public void playbackResult(Song song,Route route,boolean playable){
        if(!playable&&!online()){route.error="";route.status="网络不可用，待重试";changed();return;}route.verifiedPlayable=playable;route.verifiedAt=System.currentTimeMillis();Source s=source(route.sourceId);
        if(s!=null){verificationCache.put(s,route);persistVerification();s.healthVersion++;s.playbackHealth.record(song.key,playable,online());s.playbackMessage=playable?"实际播放通过":s.playbackHealth.filtered()?"3 首不同歌曲播放失败，已过滤":"该曲播放失败，保留渠道";save();}
    }
    public void restore(Source s){s.healthVersion++;s.playbackHealth.restore();s.failures=0;s.playbackMessage="已恢复，可重新检测";save();}
    public boolean visible(Route r,boolean showFailed){Source s=source(r.sourceId);return showFailed||((s!=null&&s.enabled&&!s.hidden())&&r.error.isEmpty()&&(!onlyPlayable||r.verifiedPlayable));}
    public boolean eligible(Route r){Source s=source(r.sourceId);return s!=null&&s.enabled&&!s.hidden()&&r.error.isEmpty();}
    public void cancelSourceScan(){sourceScanToken++;scanningSources=false;sourceScanStatus="已取消，保留已完成的检测结果";changed();}
    public void cancelResultScan(){resultScanToken++;scanningResults=false;onlyPlayable=false;for(Song song:results)for(Route r:song.routes)if(r.verifying){r.verifying=false;r.probeVersion++;}resultScanStatus="已取消，保留已完成的检测结果";changed();}
    public void scanSources(){List<Source> selected=new ArrayList<>();for(Source s:sources)if(s.enabled&&s.music)selected.add(s);scanSources(selected);}
    public void scanSources(List<Source> selected){
        if(scanningSources)return;int token=++sourceScanToken;sourceScanDone=0;sourceScanTotal=selected.size();scanningSources=!selected.isEmpty();sourceScanStatus=selected.isEmpty()?"没有启用的音乐渠道":"正在抽检 0 / "+sourceScanTotal+" 个渠道";changed();
        for(Source s:selected){int healthVersion=s.healthVersion;String fingerprint=s.script+s.variables.toString();String keyword=query.isEmpty()?"音乐":query;LinkedHashMap<String,Route> seeds=new LinkedHashMap<>();
            for(Song song:results)for(Route r:song.routes)if(r.sourceId.equals(s.id))seeds.putIfAbsent(song.key,r);
            validationWork.execute(()->{
                if(token!=sourceScanToken)return;List<String> failed=new ArrayList<>();boolean passed=false,searchFailed=false;String reason="";
                try{
                    for(int page=1;seeds.size()<3&&page<=2;page++){
                        if(token!=sourceScanToken)return;Object value=runtime.invoke(s,"search",new JSONArray().put(keyword).put(page).put("music"));
                        if(!(value instanceof JSONObject)||((JSONObject)value).optJSONArray("data")==null)throw new java.io.IOException("搜索结果不兼容");JSONArray data=((JSONObject)value).optJSONArray("data");
                        for(int i=0;i<data.length()&&seeds.size()<3;i++){JSONObject raw=data.optJSONObject(i);if(raw!=null){Route route=new Route(s,raw);seeds.putIfAbsent(new Song(route).key,route);}}
                        if(((JSONObject)value).optBoolean("isEnd")||data.length()==0)break;
                    }
                }catch(Exception e){reason=e.getMessage();searchFailed=seeds.isEmpty();}
                int tested=0;
                for(Map.Entry<String,Route> entry:seeds.entrySet()){
                    if(token!=sourceScanToken)return;if(tested++>=3)break;
                    try{resolveMedia(s,entry.getValue(),true,true,()->token==sourceScanToken);passed=true;break;}catch(Exception e){reason=e.getMessage();if(online())failed.add(entry.getKey());}
                }
                boolean ok=passed,badSearch=searchFailed;String last=reason;
                main.post(()->{if(token!=sourceScanToken)return;sourceScanDone++;
                    if(fingerprint.equals(s.script+s.variables.toString())&&source(s.id)==s&&s.healthVersion==healthVersion){
                        if(ok){s.playbackHealth.restore();s.failures=0;s.playbackMessage="抽检可播 · 已保留渠道";}
                        else if(online()&&badSearch){s.failures=Math.max(3,s.failures);s.failedAt=System.currentTimeMillis();s.playbackMessage="搜索失败，临时过滤 15 分钟："+last;}
                        else if(online()&&failed.size()>=3){for(String key:failed)s.playbackHealth.record(key,false,true);s.playbackMessage="3 首不同歌曲均试播失败，已过滤："+last;}
                        else s.playbackMessage=!online()?"网络断开，未改变过滤状态":"可测歌曲不足或无匹配结果，未新增过滤"+(last.isEmpty()?"":"："+last);
                    }
                    scanningSources=sourceScanDone<sourceScanTotal;long filtered=sources.stream().filter(a->a.enabled&&a.music&&a.hidden()).count();sourceScanStatus=(scanningSources?"检测中 ":"检测完成 ")+sourceScanDone+" / "+sourceScanTotal+" · 已过滤 "+filtered+" 个渠道";save();
                });
            });
        }
    }
    public void scanResults(){
        if(scanningResults)return;if(!online()){resultScanStatus="网络不可用，请联网后检测";changed();return;}int token=++resultScanToken;List<Route> routes=new ArrayList<>();for(Song song:results)for(Route r:song.routes){Source s=source(r.sourceId);if(s!=null&&s.enabled&&!s.hidden())routes.add(r);}
        onlyPlayable=true;resultScanDone=0;resultScanTotal=routes.size();scanningResults=!routes.isEmpty();resultScanStatus=routes.isEmpty()?"没有可检测的结果":"静音试播 0 / "+resultScanTotal;changed();
        for(Route route:routes)deepVerify(route,()->token==resultScanToken,(media,error)->{if(token!=resultScanToken)return;if(error!=null&&!online()){cancelResultScan();resultScanStatus="网络中断，已停止检测并保留原状态";changed();return;}resultScanDone++;scanningResults=resultScanDone<resultScanTotal;resultScanStatus=(scanningResults?"静音试播 ":"检测完成 ")+resultScanDone+" / "+resultScanTotal+" · 仅显示试播通过结果";changed();});
    }
    private void persistVerification(){prefs.edit().putString("verificationCache",verificationCache.json().toString()).apply();}
    public void deepVerify(Route route,java.util.function.BooleanSupplier valid,Result<Media> callback){
        Source s=source(route.sourceId);if(s==null){callback.finish(null,"渠道已不存在");return;}
        if(route.playbackActive&&route.verifiedPlayable){callback.finish(null,null);return;}
        String fingerprint=s.script+s.variables.toString();int version=++route.probeVersion;route.verifying=true;changed();
        validationWork.execute(()->{Media media=null;String error=null;boolean cancelled=false;try{if(!valid.getAsBoolean())throw new CancellationException("检测已取消");media=resolveMedia(s,route,true,true,valid);}catch(Exception e){error=e.getMessage();cancelled=e instanceof CancellationException;}
            Media value=media;String problem=error;boolean interrupted=cancelled;main.post(()->{if(!valid.getAsBoolean()){if(version==route.probeVersion)route.verifying=false;callback.finish(null,"检测已取消");return;}
                if(version==route.probeVersion)route.verifying=false;
                if(source(s.id)==s&&s.enabled&&!s.hidden()&&fingerprint.equals(s.script+s.variables.toString())&&!route.playbackActive&&version==route.probeVersion&&!interrupted&&(problem==null||online())){route.verifiedPlayable=problem==null;route.status=problem==null?"试播通过":"试播失败";route.error=problem==null?"":problem;route.verifiedAt=System.currentTimeMillis();if(value!=null){route.mimeType=value.mimeType;route.quality=value.quality;cacheLyrics(new Song(route),route,value);}verificationCache.put(s,route);persistVerification();}
                changed();callback.finish(value,problem);
            });});
    }
    public void loadLyrics(Song song,Route preferred,java.util.function.BooleanSupplier valid,boolean refresh,Result<Lyrics> callback){
        if(!refresh&&lyricCache.containsKey(song.key)){callback.finish(lyricCache.get(song.key),null);return;}
        List<Route> routes=new ArrayList<>(song.routes);if(preferred!=null){routes.remove(preferred);routes.add(0,preferred);}Map<String,Source> sourceSnapshot=new HashMap<>();for(Source s:sources)sourceSnapshot.put(s.id,s);
        lyricWork.execute(()->{Lyrics result=null;String error="该歌曲暂无歌词";
            for(Route r:routes){if(!valid.getAsBoolean())return;Source s=sourceSnapshot.get(r.sourceId);if(s==null||!s.enabled)continue;try{Lyrics candidate=lyricFrom(s,r.raw);if(!candidate.lines.isEmpty()){result=candidate;break;}}catch(Exception e){error="歌词获取失败："+e.getMessage();}}
            if(result==null)for(Source s:sourceSnapshot.values()){
                if(!valid.getAsBoolean())return;if(!s.enabled||!s.lyricSearch)continue;
                try{Object response=runtime.invoke(s,"search",new JSONArray().put(song.title+" "+song.artist).put(1).put("lyric"));JSONArray items=response instanceof JSONObject?((JSONObject)response).optJSONArray("data"):null;if(items==null)continue;
                    for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;
                        if(!CatalogPolicy.key(song.title,song.artist,"").equals(CatalogPolicy.key(SongText.clean(item.optString("title")),SongText.clean(item.optString("artist")),"")))continue;
                        Lyrics candidate=lyricFrom(s,item);if(!candidate.lines.isEmpty()){result=candidate;break;}}
                    if(result!=null)break;
                }catch(Exception e){error="歌词源暂不可用，可稍后重试";}
            }
            Lyrics value=result;String problem=result==null?error:null;main.post(()->{if(!valid.getAsBoolean())return;if(value!=null){lyricCache.put(song.key,value);while(lyricCache.size()>20)lyricCache.remove(lyricCache.keySet().iterator().next());}callback.finish(value,problem);});
        });
    }
    public void cacheLyrics(Song song,Route route,Media media){
        Lyrics lyrics=Lyrics.parse(media.rawLrc,media.translation);if(lyrics.lines.isEmpty())return;
        lyrics.sourceName=route.sourceName;lyricCache.put(song.key,lyrics);while(lyricCache.size()>20)lyricCache.remove(lyricCache.keySet().iterator().next());
    }
    private Lyrics lyricFrom(Source s,JSONObject raw) throws Exception {
        Lyrics embedded=Lyrics.parse(raw.optString("rawLrc",""),raw.optString("translation",""));
        if(!embedded.lines.isEmpty()){embedded.sourceName=s.name;return embedded;}
        Object value=runtime.invoke(s,"getLyric",new JSONArray().put(raw));Lyrics lyrics;
        if(value instanceof JSONObject)lyrics=Lyrics.parse(((JSONObject)value).optString("rawLrc",""),((JSONObject)value).optString("translation",""));
        else lyrics=Lyrics.parse(value instanceof String?(String)value:"","");lyrics.sourceName=s.name;return lyrics;
    }
}
