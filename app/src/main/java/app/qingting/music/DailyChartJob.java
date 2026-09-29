package app.qingting.music;
import android.app.job.*;

/** Android schedules this approximately daily when network constraints allow. */
@androidx.media3.common.util.UnstableApi
public final class DailyChartJob extends JobService {
    private JobParameters active;
    @Override public boolean onStartJob(JobParameters params){
        active=params;DailyCharts charts=((MusicApp)getApplication()).repository.dailyCharts;
        charts.refresh(false,()->new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(active==params){active=null;jobFinished(params,false);}}));
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){
        active=null;((MusicApp)getApplication()).repository.dailyCharts.cancel();return true;
    }
}
