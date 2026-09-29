package app.qingting.music;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class VerificationCacheTest {
    private final Models.Source source=new Models.Source("https://example.test/plugin.js");
    private Models.Route route(int id) throws Exception {return new Models.Route(source,new JSONObject().put("id",id).put("title","歌"+id));}
    private void success(Models.Route r,long at){r.verifiedPlayable=true;r.verifiedAt=at;r.status="试播通过";}
    @Test public void reconstructedSongAndRestartReuseCompletedCheck() throws Exception {
        VerificationCache cache=new VerificationCache();Models.Route original=route(1);success(original,1000);cache.put(source,original);
        cache=VerificationCache.from(cache.json(),2000);Models.Route recreated=route(1);
        assertTrue(cache.apply(source,recreated,2000));assertTrue(SongPolicy.recentlyPlayable(recreated,2000));
        assertNull(SongPolicy.nextProbe(new Models.Song(recreated),r->true,new HashSet<>(),2000));
    }
    @Test public void successExpiresWithoutExtendingTimestampOnRead() throws Exception {
        VerificationCache cache=new VerificationCache();Models.Route r=route(1);success(r,1000);cache.put(source,r);
        assertTrue(cache.apply(source,route(1),300999));assertFalse(cache.apply(source,route(1),301001));
    }
    @Test public void failedCheckExpiresAndAllowsRetry() throws Exception {
        VerificationCache cache=new VerificationCache();Models.Route r=route(1);r.verifiedAt=1000;r.error="不可播";r.status="试播失败";cache.put(source,r);
        Models.Route copy=route(1);assertTrue(cache.apply(source,copy,2000));assertEquals("不可播",copy.error);
        assertFalse(cache.apply(source,copy,61001));assertEquals("",copy.error);
        assertNotNull(SongPolicy.nextProbe(new Models.Song(copy),v->v.error.isEmpty(),new HashSet<>(),61001));
    }
    @Test public void sourceConfigurationAndSongsAreIsolated() throws Exception {
        VerificationCache cache=new VerificationCache();Models.Route r=route(1);success(r,1000);cache.put(source,r);
        assertFalse(cache.apply(source,route(2),2000));source.variables.put("token","changed");assertFalse(cache.apply(source,route(1),2000));
        source.variables=new JSONObject();source.script="updated";assertFalse(cache.apply(source,route(1),2000));
    }
    @Test public void activeOrNewerStateIsNotOverwritten() throws Exception {
        VerificationCache cache=new VerificationCache();Models.Route r=route(1);success(r,1000);cache.put(source,r);
        Models.Route copy=route(1);copy.playbackActive=true;copy.status="正在播放";assertFalse(cache.apply(source,copy,2000));assertEquals("正在播放",copy.status);
        copy.playbackActive=false;copy.verifiedAt=3000;copy.error="新失败";assertFalse(cache.apply(source,copy,4000));assertEquals("新失败",copy.error);
    }
    @Test public void boundedAndClockRollbackDoesNotTrustFutureChecks() throws Exception {
        VerificationCache cache=new VerificationCache();for(int i=0;i<520;i++){Models.Route r=route(i);success(r,1000);cache.put(source,r);}
        assertEquals(500,cache.json().length());assertFalse(cache.apply(source,route(0),2000));assertFalse(cache.apply(source,route(519),999));
        assertEquals(0,VerificationCache.from(new JSONArray().put(new JSONObject()),2000).json().length());
    }
    @Test public void switchingBoardsDoesNotRepeatProbeForSameSourceSong() throws Exception {
        VerificationCache cache=new VerificationCache();int checks=0;
        for(int visit=0;visit<3;visit++){
            Charts charts=new Charts();Charts.Board board=new Charts.Board(source,"",new JSONObject().put("id","board"+visit));
            charts.append(charts.select(board),1,new JSONObject().put("musicList",new JSONArray().put(route(1).raw)).put("isEnd",true));
            Models.Song song=charts.songs.get(0);Models.Route route=song.routes.get(0);cache.apply(source,route,2000+visit);
            if(SongPolicy.nextProbe(song,r->r.error.isEmpty(),new HashSet<>(),2000+visit)!=null){checks++;success(route,2000+visit);cache.put(source,route);}
        }
        assertEquals(1,checks);
    }
    @Test public void sourceChangeResetsPreviouslyAppliedObjectAndInProgressIsNotSaved() throws Exception {
        VerificationCache cache=new VerificationCache();Models.Route r=route(1);success(r,1000);r.verifying=true;cache.put(source,r);assertEquals(0,cache.json().length());
        r.verifying=false;cache.put(source,r);source.script="new";assertFalse(cache.apply(source,r,2000));assertFalse(r.verifiedPlayable);assertEquals(0,r.verifiedAt);
    }

    @Test public void actualPlaybackFailureInvalidationAndOtherSourceDoNotReuseSuccess() throws Exception {
        VerificationCache cache=new VerificationCache();Models.Route r=route(1);success(r,1000);cache.put(source,r);
        Models.Source other=new Models.Source("https://example.test/other.js");assertFalse(cache.apply(other,new Models.Route(other,r.raw),2000));
        cache.remove(source,r);assertFalse(cache.apply(source,route(1),2000));assertEquals(0,cache.json().length());
    }

}
