package app.qingting.music;
import android.app.Application;
@androidx.media3.common.util.UnstableApi
public final class MusicApp extends Application {
    public MusicRepository repository;
    @Override public void onCreate(){super.onCreate();repository=new MusicRepository(this);}
}
