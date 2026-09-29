package app.qingting.music;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.*;
import static app.qingting.music.Models.*;

/** Full-screen view of the existing playback session; navigation never owns playback. */
@androidx.media3.common.util.UnstableApi
public final class PlayerActivity extends Activity implements MusicRepository.Listener {
    private final int bg=Color.rgb(247,248,244),ink=Color.rgb(28,46,39),green=Color.rgb(36,94,79),muted=Color.rgb(111,123,114);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Lyrics emptyLyrics=Lyrics.parse("","");
    private MusicRepository repo;private LyricsView lyrics;private TextView title,artist,source,elapsed,duration,follow,lyricStatus;
    private ArtworkView artwork;private boolean showingLyrics;private TextView timerText;private ImageButton modeButton;private FrameLayout visual;
    private ImageButton play,favorite,previous,next,more;private SeekBar seek;private boolean dragging,started;
    private ListenableFuture<MediaController> controllerFuture;private UiSheet sheet;private Song sheetSong;
    private final Runnable tick=new Runnable(){public void run(){if(!started)return;render();handler.postDelayed(this,150);}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);repo=((MusicApp)getApplication()).repository;showingLyrics=state!=null&&state.getBoolean("showingLyrics");build();
        controllerFuture=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
        controllerFuture.addListener(()->{if(!isDestroyed())render();},command->handler.post(command));
        render();
    }
    @Override protected void onStart(){super.onStart();if(repo==null)return;started=true;repo.addListener(this);handler.post(tick);}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("showingLyrics",showingLyrics);}
    @Override protected void onStop(){started=false;if(artwork!=null)artwork.running(false);handler.removeCallbacks(tick);if(repo!=null)repo.removeListener(this);super.onStop();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(sheet!=null)sheet.dismiss();if(controllerFuture!=null)MediaController.releaseFuture(controllerFuture);super.onDestroy();}
    @Override public void changed(){if(started)render();}
    private PlaybackService current(){PlaybackService s=PlaybackService.instance;return s!=null&&s.current!=null?s:null;}
    private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout row(){LinearLayout view=new LinearLayout(this);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private void space(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private Drawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private ImageButton icon(int drawable,String label,int size,boolean primary,Runnable action){
        ImageButton button=new ImageButton(this);button.setImageResource(drawable);button.setContentDescription(label);button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);button.setPadding(dp(primary?22:12),dp(primary?22:12),dp(primary?22:12),dp(primary?22:12));
        button.setImageTintList(ColorStateList.valueOf(primary?Color.WHITE:green));button.setBackground(new RippleDrawable(ColorStateList.valueOf(primary?0x33ffffff:0x18245e4f),rounded(primary?green:Color.TRANSPARENT,size/2),rounded(Color.WHITE,size/2)));button.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));button.setOnClickListener(v->action.run());return button;
    }
    private void build(){
        boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout root=column();root.setBackgroundColor(bg);root.setPadding(dp(24),0,dp(24),0);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{int left,top,right,bottom;if(Build.VERSION.SDK_INT>=30){Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());left=i.left;top=i.top;right=i.right;bottom=i.bottom;}else{left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();}v.setPadding(dp(24)+left,top,dp(24)+right,bottom);return insets;});
        LinearLayout header=row();header.addView(icon(R.drawable.player_back,"返回",48,false,this::finish));TextView heading=text("正 在 播 放",12,muted,false);heading.setGravity(Gravity.CENTER);header.addView(heading,new LinearLayout.LayoutParams(0,dp(48),1));more=icon(R.drawable.player_more,"更多播放选项",48,false,this::showMore);header.addView(more);root.addView(header);space(root,landscape?2:16);
        LinearLayout body=landscape?row():column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout meta=column();LinearLayout names=row();LinearLayout copy=column();title=text("尚未播放",landscape?22:26,ink,true);title.setMaxLines(landscape?1:2);title.setEllipsize(TextUtils.TruncateAt.END);artist=text("选一首喜欢的歌，开始听",14,muted,false);artist.setMaxLines(1);artist.setEllipsize(TextUtils.TruncateAt.END);copy.addView(title);space(copy,7);copy.addView(artist);names.addView(copy,new LinearLayout.LayoutParams(0,-2,1));favorite=icon(R.drawable.player_heart,"收藏",48,false,()->{PlaybackService s=current();if(s!=null)PlaybackUi.favorite(this,repo,s.current);});names.addView(favorite);meta.addView(names);space(meta,10);source=text("",11,muted,false);source.setMaxLines(2);source.setEllipsize(TextUtils.TruncateAt.END);meta.addView(source);
        LinearLayout lyricArea=column();lyrics=new LyricsView(this);lyrics.setId(R.id.player_lyrics);lyrics.setSeekListener((document,line)->{PlaybackService s=current();if(s!=null)s.seekLyric(document,line);});visual=new FrameLayout(this);artwork=new ArtworkView(this,true);visual.addView(artwork,new FrameLayout.LayoutParams(-1,-1));visual.addView(lyrics,new FrameLayout.LayoutParams(-1,-1));lyrics.setVisibility(showingLyrics?View.VISIBLE:View.GONE);artwork.setVisibility(showingLyrics?View.GONE:View.VISIBLE);artwork.setOnClickListener(v->toggleVisual());lyricArea.addView(visual,new LinearLayout.LayoutParams(-1,0,1));TextView switcher=text("唱片  /  歌词",13,green,true);switcher.setGravity(Gravity.CENTER);switcher.setMinHeight(dp(48));switcher.setOnClickListener(v->toggleVisual());lyricArea.addView(switcher);
        LinearLayout caption=row();lyricStatus=text("",11,muted,false);lyricStatus.setMaxLines(1);lyricStatus.setEllipsize(TextUtils.TruncateAt.END);caption.addView(lyricStatus,new LinearLayout.LayoutParams(0,-2,1));follow=text("回到当前",12,green,true);follow.setGravity(Gravity.CENTER);follow.setMinHeight(dp(48));follow.setPadding(dp(12),0,0,0);follow.setOnClickListener(v->lyrics.resumeFollowing());caption.addView(follow);lyricArea.addView(caption);
        LinearLayout footer=buildControls();
        if(landscape){LinearLayout left=column();left.addView(meta);left.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));left.addView(footer);body.addView(left,new LinearLayout.LayoutParams(0,-1,.46f));View divider=new View(this);body.addView(divider,new LinearLayout.LayoutParams(dp(24),1));body.addView(lyricArea,new LinearLayout.LayoutParams(0,-1,.54f));}
        else{body.addView(meta);body.addView(lyricArea,new LinearLayout.LayoutParams(-1,0,1));body.addView(footer);}
    }
    private LinearLayout buildControls(){
        LinearLayout footer=column();seek=new SeekBar(this);seek.setMax(1000);seek.setProgressTintList(ColorStateList.valueOf(green));seek.setThumbTintList(ColorStateList.valueOf(green));seek.setPadding(dp(12),0,dp(12),0);footer.addView(seek,new LinearLayout.LayoutParams(-1,dp(36)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){PlaybackService s=current();if(user&&s!=null&&s.player.isCurrentMediaItemSeekable()&&s.player.getDuration()>0){s.player.seekTo(s.player.getDuration()*value/1000);lyrics.resumeFollowing();}}public void onStartTrackingTouch(SeekBar b){dragging=true;}public void onStopTrackingTouch(SeekBar b){dragging=false;lyrics.resumeFollowing();}});
        LinearLayout times=row();elapsed=text("0:00",11,muted,false);duration=text("0:00",11,muted,false);times.addView(elapsed,new LinearLayout.LayoutParams(0,-2,1));times.addView(duration);footer.addView(times);timerText=text("",11,muted,false);timerText.setGravity(Gravity.CENTER);timerText.setOnClickListener(v->PlaybackUi.timer(this));footer.addView(timerText);space(footer,8);
        LinearLayout controls=row();controls.setGravity(Gravity.CENTER);previous=icon(R.drawable.player_previous,"上一首",48,false,()->{PlaybackService s=current();if(s!=null){s.next(-1);lyrics.resumeFollowing();}});next=icon(R.drawable.player_next,"下一首",48,false,()->{PlaybackService s=current();if(s!=null){s.next(1);lyrics.resumeFollowing();}});play=icon(R.drawable.player_play,"播放",72,true,()->{PlaybackService s=current();if(s!=null)s.toggle();});play.setId(R.id.player_play);modeButton=icon(R.drawable.player_mode_sequence,"顺序播放，点击切换",48,false,()->{PlaybackService s=current();if(s!=null){s.cycleMode();render();Toast.makeText(this,QueuePolicy.MODES[s.mode],Toast.LENGTH_SHORT).show();}});controls.addView(modeButton);controls.addView(previous);controls.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));controls.addView(play);controls.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));controls.addView(next);TextView queue=text("☷",26,green,true);queue.setGravity(Gravity.CENTER);queue.setContentDescription("当前播放列表");queue.setOnClickListener(v->PlaybackUi.queue(this));controls.addView(queue,new LinearLayout.LayoutParams(dp(48),dp(48)));footer.addView(controls);space(footer,18);return footer;
    }
    private void toggleVisual(){showingLyrics=!showingLyrics;lyrics.setVisibility(showingLyrics?View.VISIBLE:View.GONE);artwork.setVisibility(showingLyrics?View.GONE:View.VISIBLE);render();}
    private void render(){
        PlaybackService s=current();boolean available=s!=null;for(View v:new View[]{play,previous,next,favorite,more,modeButton}){v.setEnabled(available);v.setAlpha(available?1:.35f);}
        if(sheet!=null&&sheet.isShowing()&&(!available||s.current!=sheetSong))sheet.dismiss();
        if(!available){artwork.bind(null);artwork.running(false);title.setText("尚未播放");artist.setText("返回后选择一首喜欢的歌");source.setText("");lyrics.update(emptyLyrics,"让喜欢的声音，留在这里。",0,0);seek.setEnabled(false);seek.setProgress(0);follow.setVisibility(View.INVISIBLE);elapsed.setText("0:00");duration.setText("0:00");lyricStatus.setText("");return;}
        artwork.bind(s.current);artwork.running(started&&!showingLyrics&&s.player.isPlaying());modeButton.setImageResource(s.mode==0?R.drawable.player_mode_sequence:s.mode==1?R.drawable.player_mode_repeat:R.drawable.player_mode_repeat_one);modeButton.setTooltipText(QueuePolicy.MODES[s.mode]);modeButton.setContentDescription(QueuePolicy.MODES[s.mode]+"，点击切换");timerText.setText(s.timerLabel());title.setText(s.current.title);artist.setText(s.current.artist.isEmpty()?"未知歌手":s.current.artist);source.setText(s.status);favorite.setImageResource(repo.liked(s.current)?R.drawable.player_heart_filled:R.drawable.player_heart);favorite.setContentDescription(repo.liked(s.current)?"取消收藏":"收藏");
        long pos=s.displayPosition(),length=s.player.getDuration();boolean playing=s.wantsPlayback();
        play.setImageResource(s.needsRetry()?R.drawable.player_retry:playing?R.drawable.player_pause:R.drawable.player_play);play.setContentDescription(s.needsRetry()?"重试播放":playing?"暂停":"继续播放");elapsed.setText(format(pos));duration.setText(format(Math.max(0,length)));seek.setEnabled(!s.isRecovering()&&!s.needsRetry()&&length>0&&s.player.isCurrentMediaItemSeekable());if(!dragging)seek.setProgress(length>0?(int)(pos*1000/length):0);
        lyrics.update(s.lyrics,s.lyricStatus,pos,s.lyricDelay());lyricStatus.setText(s.lyrics.timed?"点击歌词，跳转到这一句":s.lyricStatus);follow.setVisibility(s.lyrics.timed&&!lyrics.isFollowing()?View.VISIBLE:View.INVISIBLE);
    }
    private void showMore(){
        if(current()==null)return;List<String> options=new ArrayList<>(Arrays.asList("手动选择播放来源","歌词时间校准","重新加载歌词","定时停止","本地起播统计"));if(current().current.fixed()&&repo.liked(current().current))options.add("将当前来源保存到收藏");new UiSheet.Builder(this).setTitle("播放选项").setItems(options.toArray(new String[0]),(dialog,index)->{PlaybackService s=current();if(s==null)return;if(index==0)showRoutes(s);else if(index==1)showCalibration(s);else if(index==3)PlaybackUi.timer(this);else if(index==4)new UiSheet.Builder(this).setTitle("本地起播统计").setMessage(repo.metrics()).setPositiveButton("关闭",null).show();else if(index==5){if(s.route!=null){repo.pinFavorite(s.current,s.route,(saved,error)->Toast.makeText(this,saved?"已保存收藏来源":error,Toast.LENGTH_SHORT).show());}}else{s.loadLyrics(true);lyrics.resumeFollowing();}}).show();
    }
    private void showRoutes(PlaybackService s){
        Song song=s.current;List<Route> routes=new ArrayList<>(song.routes);String[] labels=new String[routes.size()];
        for(int i=0;i<routes.size();i++){Route route=routes.get(i);Source source=repo.source(route.sourceId);labels[i]=(route==s.route?"当前 · ":"")+route.sourceName+" · "+(source==null||!source.enabled?"已停用":route.label());}
        sheetSong=song;sheet=new UiSheet.Builder(this).setTitle("切换播放线路").setNeutralButton("寻找其他来源",(dialog,w)->findRoutes(song)).setItems(labels,(d,which)->{PlaybackService player=current();Route route=routes.get(which);Source source=repo.source(route.sourceId);if(player==null||player.current!=song)return;if(source==null||!source.enabled){Toast.makeText(this,"请先在订阅源中启用这条线路",Toast.LENGTH_SHORT).show();return;}if(route==player.route&&player.player.getPlaybackState()==androidx.media3.common.Player.STATE_READY)return;player.switchRoute(song,route);lyrics.resumeFollowing();}).setNegativeButton("取消",null).create();sheet.show();
    }
    private void findRoutes(Song song){Toast.makeText(this,"正在查找同曲其他来源…",Toast.LENGTH_SHORT).show();Set<String> excluded=new HashSet<>();for(Route r:song.routes)excluded.add(r.sourceId);repo.findAlternatives(song,excluded,()->!isDestroyed()&&PlaybackService.instance!=null&&PlaybackService.instance.current==song,(r,e)->{song.routes.add(r);if(PlaybackService.instance!=null)PlaybackService.instance.queueContentChanged(song);},()->{if(current()!=null&&current().current==song)showRoutes(current());});}
    private void showCalibration(PlaybackService s){
        if(!s.lyrics.timed){Toast.makeText(this,"带时间戳的歌词才支持校准",Toast.LENGTH_SHORT).show();return;}
        Song song=s.current;LinearLayout content=column();content.setPadding(dp(24),dp(12),dp(24),dp(8));TextView label=text(delayLabel(s.lyricDelay()),18,green,true);content.addView(label);space(content,10);content.addView(text("每格 0.5 秒，仅调整当前歌曲的歌词时间",13,muted,false));SeekBar adjust=new SeekBar(this);adjust.setMax(40);adjust.setProgress(s.lyricDelay()/500+20);content.addView(adjust);
        adjust.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){PlaybackService player=current();if(user&&player!=null&&player.current==song){repo.setLyricDelay(song.key,(value-20)*500);label.setText(delayLabel(player.lyricDelay()));lyrics.resumeFollowing();}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        sheetSong=song;sheet=new UiSheet.Builder(this).setTitle("歌词时间校准").setView(content).setNeutralButton("重置",(d,w)->{if(current()!=null&&current().current==song){repo.setLyricDelay(song.key,0);lyrics.resumeFollowing();}}).setPositiveButton("完成",null).create();sheet.show();
    }
    private String delayLabel(int value){return value==0?"与原歌词同步":String.format(Locale.ROOT,"歌词%s %.1f 秒",value<0?"提前":"延后",Math.abs(value)/1000.0);}
    private String format(long ms){long sec=Math.max(0,ms)/1000;return String.format(Locale.ROOT,"%d:%02d",sec/60,sec%60);}
}
