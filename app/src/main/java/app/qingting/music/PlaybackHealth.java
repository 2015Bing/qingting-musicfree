package app.qingting.music;
import java.util.*;
import org.json.*;

/** Channel playback failures are separate from search health and counted per song. */
public final class PlaybackHealth {
    private final LinkedHashSet<String> failedSongs=new LinkedHashSet<>();
    public boolean filtered(){return failedSongs.size()>=3;}
    public void record(String key,boolean playable,boolean online){
        if(!online)return;
        if(playable){restore();return;}
        if(key!=null&&!key.isEmpty()&&failedSongs.size()<3)failedSongs.add(key);
    }
    public void restore(){failedSongs.clear();}
    public JSONArray json(){return new JSONArray(failedSongs);}
    public static PlaybackHealth from(JSONArray a){PlaybackHealth h=new PlaybackHealth();if(a!=null)for(int i=0;i<a.length();i++)h.record(a.optString(i),false,true);return h;}
}
