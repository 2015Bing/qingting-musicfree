package app.qingting.music;
import android.content.Context;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Short, silent audio decode; no MediaSession and no audio-focus request. */
@androidx.media3.common.util.UnstableApi
public final class MediaVerifier {
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
    public MediaVerifier(Context context){this.context=context;}
    public void verify(Models.Media media,BooleanSupplier valid) throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicBoolean closed=new AtomicBoolean(),passed=new AtomicBoolean();
        AtomicReference<String> error=new AtomicReference<>("试播超时");AtomicReference<ExoPlayer> player=new AtomicReference<>();AtomicReference<Runnable> poll=new AtomicReference<>();
        main.post(()->{
            if(closed.get())return;
            try{
                if(!valid.getAsBoolean()){error.set("检测已取消");done.countDown();return;}
                ExoPlayer p=new ExoPlayer.Builder(context).setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(1000,3000,250,500).build()).build();player.set(p);
                p.setVolume(0);p.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),false);
                p.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException e){error.set(e.getErrorCodeName());done.countDown();}});
                DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("QingTing/0.1 Android").setDefaultRequestProperties(media.headers).setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(5000).setReadTimeoutMs(5000);
                MediaItem.Builder item=new MediaItem.Builder().setUri(media.url);if(!media.mimeType.isEmpty())item.setMimeType(media.mimeType);
                p.setMediaSource(new DefaultMediaSourceFactory(http).createMediaSource(item.build()));p.prepare();p.play();
                Runnable check=new Runnable(){public void run(){
                    if(closed.get())return;
                    if(!valid.getAsBoolean()){error.set("检测已取消");done.countDown();return;}
                    if(p.isPlaying()&&p.getCurrentPosition()>=350&&p.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_AUDIO)){passed.set(true);done.countDown();return;}
                    if(p.getPlaybackState()==Player.STATE_ENDED){error.set("音源过短或没有可播放音轨");done.countDown();return;}
                    main.postDelayed(this,100);
                }};poll.set(check);main.post(check);
            }catch(Exception e){error.set(e.getMessage());done.countDown();}
        });
        try{if(!done.await(10,TimeUnit.SECONDS)||!passed.get())throw new IOException(error.get());}
        finally{closed.set(true);main.post(()->{if(poll.get()!=null)main.removeCallbacks(poll.get());if(player.get()!=null)player.get().release();});}
    }
}
