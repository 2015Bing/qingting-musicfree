package app.qingting.music;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.*;
import java.util.*;
import static app.qingting.music.Models.*;

@androidx.media3.common.util.UnstableApi
public final class PlaybackService extends MediaSessionService {
    public static PlaybackService instance;
    public ExoPlayer player;public Song current;public Route route;public String status="选择一首歌，开始听";
    public Lyrics lyrics=Lyrics.parse("","");public String lyricStatus="尚未加载歌词";private volatile int lyricGeneration;private Route lyricRoute;
    private MusicRepository repo;private MediaSession session;private volatile int generation;private int attempt,index;private boolean strictQueue;
    private volatile boolean activePlaying;public int mode;private boolean restored;private long startedAt,lastSave;private boolean measured,prefetched;private int prefetchToken;
    private final SleepTimer sleep=new SleepTimer();private android.content.SharedPreferences playbackPrefs;
    private final Runnable pulse=new Runnable(){public void run(){if(player==null)return;long now=SystemClock.elapsedRealtime();if(sleep.expired(now)){pauseForTimer();}if(current!=null&&!measured&&startedAt>0&&recovery.wantsPlay()&&now-startedAt>=20000){repo.recordMetric(now-startedAt,false);measured=true;generation++;finishRecovery("连接超过 20 秒 · 可重试或手动换源");}if(current!=null&&player.isPlaying()&&!measured&&player.getCurrentPosition()>0){measured=true;repo.recordStart(current,route,now-startedAt);}if(player.isPlaying()&&!prefetched&&now-startedAt>3000)prefetchNext();if(now-lastSave>5000){savePlayback();lastSave=now;}main.postDelayed(this,500);}};
    private List<Route> routes=new ArrayList<>();private List<Song> queue=new ArrayList<>();
    private final PlaybackRecovery recovery=new PlaybackRecovery();private boolean automaticSwitch;private Route preferredRoute;
    private String attemptStatus="";
    private volatile boolean discoveryOpen;private volatile int discoveryGeneration;private boolean searchedAlternatives,waitingAlternative;private Runnable discoveryTimeout;
    private final Handler main=new Handler(Looper.getMainLooper());private Runnable timeout;
    @Override public void onCreate(){
        super.onCreate();instance=this;repo=((MusicApp)getApplication()).repository;
        player=new ExoPlayer.Builder(this).build();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true);
        player.setHandleAudioBecomingNoisy(true);player.setWakeMode(C.WAKE_MODE_NETWORK);
        PendingIntent activity=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Player transport=new ForwardingPlayer(player){
            @Override public Player.Commands getAvailableCommands(){return super.getAvailableCommands().buildUpon().add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_PREVIOUS).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build();}
            @Override public boolean isCommandAvailable(int command){return getAvailableCommands().contains(command);}
            @Override public void seekToNext(){next(1);}@Override public void seekToNextMediaItem(){next(1);}
            @Override public void seekToPrevious(){next(-1);}@Override public void seekToPreviousMediaItem(){next(-1);}
            @Override public void play(){if(restored||recovery.exhausted())toggle();else super.play();}
        };
        session=new MediaSession.Builder(this,transport).setSessionActivity(activity).build();
        playbackPrefs=getSharedPreferences("playback",MODE_PRIVATE);mode=playbackPrefs.getInt("mode",0);if(mode<0||mode>2)mode=0;
        try{org.json.JSONArray saved=new org.json.JSONArray(playbackPrefs.getString("queue","[]"));LibraryState.readSongs(saved,queue);index=Math.max(0,Math.min(queue.size()-1,playbackPrefs.getInt("index",0)));if(!queue.isEmpty()){current=queue.get(index);route=current.fixedRoute();restored=true;recovery.reset(playbackPrefs.getLong("position",0),false);status="已恢复队列 · 点击继续播放";}}catch(Exception ignored){}
        main.post(pulse);
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException error){if(player.getPlayerError()==error)failed(error.getErrorCodeName());}
            @Override public void onIsPlayingChanged(boolean playing){if(playing!=player.isPlaying())return;activePlaying=playing;if(playing){closeDiscovery();cancelTimeout();status=(automaticSwitch?"已自动切换 · ":"正在播放 · ")+(route==null?"":route.sourceName);if(route!=null){route.status="正在播放";route.error="";route.verifiedAt=System.currentTimeMillis();repo.playbackResult(current,route,true);if(lyricRoute!=route)loadLyrics(false);}if(current!=null)repo.played(current);}else if(player.getPlaybackState()==Player.STATE_READY&&!recovery.exhausted()){status="已暂停 · "+(route==null?"":route.sourceName);if(route!=null&&route.error.isEmpty())route.status="播放通过";}repo.changed();}
            @Override public void onPlaybackStateChanged(int state){if(state!=player.getPlaybackState())return;if(state==Player.STATE_ENDED){if(recovery.pending())failed("备用线路无法恢复到原进度");else if(recovery.wantsPlay()){if(sleep.consumeTrackEnd())pauseForTimer();else advance(1,true);}}else if(state==Player.STATE_READY){if(recovery.pending()&&!recovery.canResume(player.getDuration())){failed("备用线路时长不足，无法恢复原进度");return;}recovery.ready();closeDiscovery();searchedAlternatives=false;cancelTimeout();if(route!=null&&lyricRoute!=route)loadLyrics(false);if(!recovery.wantsPlay())status="已暂停 · "+(route==null?"":route.sourceName);}else if(state==Player.STATE_BUFFERING&&recovery.wantsPlay())watchBuffering(generation,route);repo.changed();}
            @Override public void onPlayWhenReadyChanged(boolean ready,int reason){if(reason==Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST||reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS||reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)recovery.setWantsPlay(ready);if(recovery.pending())status=attemptStatus+(recovery.wantsPlay()?"":" · 已暂停");if(player.getPlaybackState()==Player.STATE_BUFFERING){if(!ready)cancelTimeout();else watchBuffering(generation,route);}repo.changed();}
        });
    }
    public void play(Song song,List<Song> songs,Route preferred){
        if(current==null||!current.key.equals(song.key))sleep.cancelTrack();start(song,songs,preferred,0,true);
    }
    private void start(Song song,List<Song> songs,Route preferred,long position,boolean autoplay){
        closeDiscovery();searchedAlternatives=false;waitingAlternative=false;repo.cancelVisibleChecks();prefetchToken++;prefetched=false;restored=false;startedAt=SystemClock.elapsedRealtime();measured=false;
        if(preferred==null&&song.fixed())preferred=song.fixedRoute();
        recovery.reset(position,autoplay);automaticSwitch=false;preferredRoute=preferred;
        generation++;lyricGeneration++;lyrics=Lyrics.parse("","");lyricStatus="歌词加载中…";lyricRoute=null;cancelTimeout();if(route!=null){route.playbackActive=false;if(route.status.equals("正在播放"))route.status="播放通过";}player.stop();route=null;if(songs!=queue)strictQueue=songs==repo.results&&repo.onlyPlayable;current=song;List<Song> selected=new ArrayList<>();for(Song item:songs)selected.add(item);if(selected.stream().noneMatch(item->item.key.equals(song.key)))selected.add(song);if(!queue.equals(selected))queueSnapshot.changed();queue=selected;index=0;for(int i=0;i<queue.size();i++)if(queue.get(i).key.equals(song.key))index=i;
        routes=new ArrayList<>();for(Route r:song.routes){Source source=repo.source(r.sourceId);if(!song.fixed()&&source!=null&&source.enabled&&!source.hidden()){r.lowOnly=false;routes.add(r);}}routes.sort(Comparator.comparingLong(r->(!r.error.isEmpty()?1000000:0)+repo.sourceRank(song,r)));
        if(preferred!=null){preferred.lowOnly=false;routes.remove(preferred);routes.add(0,preferred);}player.setPlayWhenReady(autoplay);attempt=0;repo.prioritize(preferred!=null?preferred:routes.isEmpty()?null:routes.get(0));savePlayback();tryNext(generation);
        if(preferred==null&&!song.fixed()&&routes.size()>1){int token=generation;Route backup=routes.get(1);long click=startedAt;main.postDelayed(()->{if(token!=generation||player.isPlaying()||!recovery.wantsPlay())return;repo.deepVerify(backup,()->token==generation&&!activePlaying&&recovery.wantsPlay(),(media,error)->{if(token!=generation||player.isPlaying()||!recovery.wantsPlay()||media==null||error!=null)return;long checkpoint=displayPosition();start(current,queue,backup,checkpoint,true);startedAt=click;});},2500);}
    }
    private void tryNext(int token){
        if(token!=generation)return;
        cancelTimeout();player.stop();
        if(route!=null)route.playbackActive=false;
        while(attempt<routes.size()){Route r=routes.get(attempt);Source source=repo.source(r.sourceId);if(source!=null&&source.enabled&&(!source.hidden()||r==preferredRoute))break;attempt++;}
        if(attempt>=routes.size()){
            if(!repo.online()){finishRecovery("网络不可用 · 联网后点重试");return;}
            if(current.fixed()){finishRecovery("固定来源暂不可用 · 请手动换源或重试");return;}if(!searchedAlternatives){discoverAlternatives(token);return;}
            if(discoveryOpen){waitingAlternative=true;recovery.beginAttempt();attemptStatus="正在其他订阅中寻找同曲线路…";status=attemptStatus+(recovery.wantsPlay()?"":" · 已暂停");repo.changed();return;}
            finishRecovery("未找到可播的同曲线路 · 点重试或下一首");return;
        }
        lyricGeneration++;lyricRoute=null;lyrics=Lyrics.parse("","");lyricStatus="等待线路就绪后加载歌词";Route candidate=routes.get(attempt++);route=candidate;candidate.playbackActive=true;candidate.verifying=false;candidate.probeVersion++;long request=recovery.beginAttempt();
        attemptStatus=(automaticSwitch?(candidate.lowOnly?"正在尝试低音质 · ":"正在自动切源 · "):"正在连接 · ")+candidate.sourceName+"（"+attempt+"/"+routes.size()+"）";status=attemptStatus+(recovery.wantsPlay()?"":" · 已暂停");repo.changed();
        timeout=()->{if(token==generation&&recovery.valid(request))resolutionFailed(candidate,"解析超时",token);};main.postDelayed(timeout,45000);
        repo.resolve(candidate,false,()->token==generation&&recovery.valid(request),(media,error)->{
            if(token!=generation||!recovery.valid(request))return;cancelTimeout();
            Source source=repo.source(candidate.sourceId);if(source==null||!source.enabled||(source.hidden()&&candidate!=preferredRoute)){tryNext(token);return;}
            if(error!=null){resolutionFailed(candidate,error,token);return;}
            DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("QingTing/0.1 Android").setDefaultRequestProperties(media.headers).setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(10000).setReadTimeoutMs(12000);
            MediaItem item=new MediaItem.Builder().setMediaId(current.key).setUri(media.url).setMimeType(media.mimeType.isEmpty()?null:media.mimeType).setMediaMetadata(new MediaMetadata.Builder().setTitle(current.title).setArtist(current.artist).setAlbumTitle(current.album).build()).build();
            player.setMediaSource(new DefaultMediaSourceFactory(http).createMediaSource(item),recovery.position());player.prepare();player.setPlayWhenReady(recovery.wantsPlay());
            watchBuffering(token,candidate);
        });
    }
    private void failed(String error){
        if(route!=null)repo.invalidateMedia(route);
        if(route==null||recovery.exhausted())return;recovery.capture(player.getCurrentPosition());route.playbackActive=false;automaticSwitch=true;
        if(!repo.online()){repo.playbackResult(current,route,false);finishRecovery("网络不可用 · 联网后点重试");return;}
        if(!route.lowOnly&&!route.quality.equals("low")){route.lowOnly=true;attempt--;tryNext(generation);return;}
        route.status="播放失败";route.error=error;repo.playbackResult(current,route,false);tryNext(generation);
    }
    private void resolutionFailed(Route candidate,String error,int token){candidate.playbackActive=false;candidate.error=error;candidate.status="播放失败";repo.playbackResult(current,candidate,false);automaticSwitch=true;if(!repo.online()){finishRecovery("网络不可用 · 联网后点重试");return;}tryNext(token);}
    private void discoverAlternatives(int token){
        int discovery=++discoveryGeneration;
        searchedAlternatives=true;discoveryOpen=true;waitingAlternative=true;recovery.beginAttempt();lyricGeneration++;lyrics=Lyrics.parse("","");lyricRoute=null;lyricStatus="正在寻找同一首歌的备用线路";attemptStatus="正在其他订阅中寻找同曲线路…";status=attemptStatus+(recovery.wantsPlay()?"":" · 已暂停");repo.changed();
        Set<String> excluded=new HashSet<>();for(Route known:current.routes)excluded.add(known.sourceId);Song expected=current;
        discoveryTimeout=()->{if(token!=generation||discovery!=discoveryGeneration||!discoveryOpen)return;boolean waiting=waitingAlternative;closeDiscovery();if(waiting)finishRecovery("补搜已超时 · 未找到可播线路，可重试");};main.postDelayed(discoveryTimeout,45000);
        repo.findAlternatives(expected,excluded,()->token==generation&&discovery==discoveryGeneration&&discoveryOpen,(candidate,error)->{
            if(token!=generation||discovery!=discoveryGeneration||current!=expected||!discoveryOpen)return;
            boolean duplicate=false;for(Route existing:expected.routes)if(existing.sourceId.equals(candidate.sourceId)&&existing.raw.toString().equals(candidate.raw.toString()))duplicate=true;
            if(!duplicate){expected.routes.add(candidate);queueContentChanged(expected);routes.add(candidate);if(waitingAlternative){waitingAlternative=false;automaticSwitch=true;tryNext(token);}else repo.changed();}
        },()->{if(token!=generation||discovery!=discoveryGeneration||!discoveryOpen)return;boolean waiting=waitingAlternative;closeDiscovery();if(waiting)finishRecovery("未找到可播的同曲线路 · 点重试或下一首");});
    }
    private void closeDiscovery(){discoveryGeneration++;discoveryOpen=false;waitingAlternative=false;if(discoveryTimeout!=null){main.removeCallbacks(discoveryTimeout);discoveryTimeout=null;}}
    private void finishRecovery(String message){if(!measured&&startedAt>0){measured=true;repo.recordMetric(SystemClock.elapsedRealtime()-startedAt,false);}closeDiscovery();cancelTimeout();recovery.exhaust();if(route!=null)route.playbackActive=false;player.stop();player.pause();lyricGeneration++;lyrics=Lyrics.parse("","");lyricStatus="播放暂不可用，可重试或播放下一首";status=message;repo.changed();}
    public boolean isRecovering(){return recovery.pending();}
    public boolean needsRetry(){return recovery.exhausted();}
    public boolean wantsPlayback(){return !recovery.exhausted()&&recovery.wantsPlay();}
    public long displayPosition(){return restored||recovery.pending()||recovery.exhausted()?recovery.position():player.getCurrentPosition();}
    public void loadLyrics(boolean refresh){
        if(current==null||route==null)return;int token=++lyricGeneration;lyricRoute=route;lyrics=Lyrics.parse("","");lyricStatus="歌词加载中…";repo.changed();
        repo.loadLyrics(current,route,()->token==lyricGeneration,refresh,(value,error)->{lyrics=value==null?Lyrics.parse("",""):value;lyricStatus=error==null?"歌词 · "+lyrics.sourceName+(lyrics.timed?"":" · 纯文本"):error;repo.changed();});
    }
    public void switchRoute(Song expected,Route preferred){if(current!=expected)return;Song updated=expected.snapshot(null);boolean found=false;for(Route r:updated.routes)if(r.sourceId.equals(preferred.sourceId)&&r.raw.toString().equals(preferred.raw.toString()))found=true;if(!found)updated.routes.add(preferred);if(expected.fixed()){updated.pinnedSource=preferred.sourceId;updated.pinnedRaw=preferred.raw.toString();updated.sourceChoiceRequired=false;}List<Song> copy=new ArrayList<>(queue);copy.set(index,updated);start(updated,copy,preferred,displayPosition(),recovery.exhausted()||recovery.wantsPlay());}
    public int lyricDelay(){return current==null?0:repo.lyricDelay(current.key);}
    public void adjustLyricDelay(int delta){if(current!=null)repo.setLyricDelay(current.key,lyricDelay()+delta);}
    public void resetLyricDelay(){if(current!=null)repo.setLyricDelay(current.key,0);}
    public void seekLyric(Lyrics expected,int index){if(expected!=lyrics)return;long position=lyrics.seekPosition(index,lyricDelay(),player.getDuration());if(position>=0&&player.isCurrentMediaItemSeekable())player.seekTo(position);}
    public String currentLyric(){int index=lyrics.indexAt(player.getCurrentPosition(),lyricDelay());return index<0?"":lyrics.lines.get(index).text;}
    private boolean hasRoute(Song song){for(Route candidate:song.routes)if(repo.eligible(candidate)&&(!strictQueue||candidate.verifiedPlayable))return true;return false;}
    public void next(int delta){sleep.cancelTrack();advance(delta,false);}
    private void advance(int delta,boolean natural){int next=QueuePolicy.nextIndex(queue.size(),index,delta,mode,natural);if(next<0){player.pause();recovery.setWantsPlay(false);status="已到播放队列末尾";savePlayback();repo.changed();return;}start(queue.get(next),queue,null,0,true);}
    public List<Song> queueSnapshot(){return new ArrayList<>(queue);}
    public void cycleMode(){mode=(mode+1)%3;prefetchToken++;prefetched=false;savePlayback();repo.changed();}
    public void queueContentChanged(Song song){if(queue.contains(song))queueSnapshot.changed();}
    public void enqueue(Song song,boolean next){if(current!=null&&current.key.equals(song.key))return;queueSnapshot.changed();QueuePolicy.enqueue(queue,song,current==null?"":current.key,next);index=Math.max(0,findCurrent());if(current==null){index=0;current=queue.get(0);restored=true;recovery.reset(0,false);}prefetchToken++;prefetched=false;savePlayback();repo.changed();}
    private int findCurrent(){for(int i=0;i<queue.size();i++)if(current!=null&&queue.get(i).key.equals(current.key))return i;return -1;}
    public void removeQueued(int at){if(at<0||at>=queue.size())return;boolean active=at==index,wants=recovery.wantsPlay();queue.remove(at);queueSnapshot.changed();prefetchToken++;prefetched=false;if(queue.isEmpty()){clearQueue();return;}if(active){sleep.cancelTrack();index=Math.min(at,queue.size()-1);start(queue.get(index),queue,null,0,wants);}else index=findCurrent();savePlayback();repo.changed();}
    public void clearQueue(){generation++;prefetchToken++;sleep.clear();closeDiscovery();cancelTimeout();player.stop();player.clearMediaItems();recovery.reset(0,false);queue.clear();queueSnapshot.changed();current=null;route=null;restored=false;savePlayback();repo.changed();}
    public void setSleepMinutes(int value){sleep.minutes(value,SystemClock.elapsedRealtime());prefetchToken++;prefetched=false;repo.changed();}
    public void afterTrack(){sleep.afterTrack();prefetchToken++;prefetched=false;repo.changed();}
    public String timerLabel(){if(sleep.endOfTrack)return "播完当前歌曲停止";if(sleep.deadline==0)return "定时停止 · 未开启";long seconds=Math.max(0,(sleep.deadline-SystemClock.elapsedRealtime())/1000);return String.format(Locale.ROOT,"定时停止 · %d:%02d",seconds/60,seconds%60);}
    private void pauseForTimer(){long position=QueuePolicy.resumePosition(displayPosition(),player.getPlaybackState()==Player.STATE_ENDED);generation++;prefetchToken++;closeDiscovery();cancelTimeout();player.pause();recovery.reset(position,false);if(player.getPlaybackState()!=Player.STATE_READY){player.stop();restored=true;}status="定时已停止 · 点击继续播放";savePlayback();repo.changed();}
    private final QueueSnapshot queueSnapshot=new QueueSnapshot();
    private void savePlayback(){if(playbackPrefs==null)return;try{android.content.SharedPreferences.Editor edit=playbackPrefs.edit();String encoded=queueSnapshot.take(()->LibraryState.songsJson(queue).toString());if(encoded!=null)edit.putString("queue",encoded);edit.putInt("index",index).putInt("mode",mode).putLong("position",current==null?0:displayPosition()).apply();}catch(Exception ignored){}}
    private void prefetchNext(){prefetched=true;if(sleep.endOfTrack||mode==2)return;int at=QueuePolicy.nextIndex(queue.size(),index,1,mode,true);if(at<0||at==index)return;Song next=queue.get(at);Route chosen=next.fixedRoute();if(next.sourceChoiceRequired)return;if(chosen==null)for(Route r:next.routes)if(repo.eligible(r)&&(chosen==null||repo.sourceRank(next,r)<repo.sourceRank(next,chosen)))chosen=r;if(chosen==null)return;int token=++prefetchToken;repo.resolve(chosen,true,()->token==prefetchToken&&activePlaying,(m,e)->{});}
    public void toggle(){if(current==null)return;if(restored){start(current,queue,null,recovery.position(),true);return;}if(recovery.exhausted()){start(current,queue,null,recovery.position(),true);return;}if(player.getPlaybackState()==Player.STATE_ENDED){start(current,queue,null,0,true);return;}boolean ready=!recovery.wantsPlay();recovery.setWantsPlay(ready);player.setPlayWhenReady(ready);if(recovery.pending())status=attemptStatus+(ready?"":" · 已暂停");repo.changed();}
    private void cancelTimeout(){if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}}
    private void watchBuffering(int token,Route candidate){
        cancelTimeout();if(candidate==null||!recovery.wantsPlay())return;long requestToken=recovery.token();
        timeout=()->{if(token==generation&&recovery.valid(requestToken)&&route==candidate&&recovery.wantsPlay()&&!player.isPlaying()){failed("缓冲超过 25 秒");}};main.postDelayed(timeout,25000);
    }
    @Nullable @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo){return session;}
    @Override public void onDestroy(){savePlayback();main.removeCallbacksAndMessages(null);prefetchToken++;generation++;lyricGeneration++;closeDiscovery();cancelTimeout();if(route!=null){route.playbackActive=false;if(route.status.equals("正在播放"))route.status="播放通过";}instance=null;if(session!=null)session.release();if(player!=null)player.release();super.onDestroy();}
}
