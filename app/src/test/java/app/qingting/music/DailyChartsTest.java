package app.qingting.music;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class DailyChartsTest {
    @Test public void fullPageKeepsUnverifiedSongsBeyondTen() throws Exception {
        Models.Source source=new Models.Source("source");Charts.Board board=new Charts.Board(source,"",new JSONObject().put("id","chart"));
        org.json.JSONArray items=new org.json.JSONArray();for(int i=0;i<35;i++)items.put(new JSONObject().put("id",i).put("title","song"+i));
        Charts page=DailyCharts.fullPage(board,new JSONObject().put("musicList",items).put("isEnd",false));
        assertEquals(35,page.songs.size());assertFalse(page.end);assertEquals(35,page.rank(page.songs.get(34)));assertFalse(page.songs.get(34).routes.get(0).verifiedPlayable);
    }
    @Test public void oldSubsetCacheRequiresRefetch() throws Exception {
        assertFalse(DailyCharts.fullPageCache(new JSONObject().put("songs",new org.json.JSONArray())));
        assertFalse(DailyCharts.fullPageCache(new JSONObject().put("version",2)));
        assertTrue(DailyCharts.fullPageCache(new JSONObject().put("version",2).put("detail",new JSONObject())));
    }
    @Test public void oldVerificationAloneIsNotProofCachedChartFailed() throws Exception {
        Models.Route route=new Models.Route("id","source",new JSONObject().put("title","song"));route.verifiedPlayable=true;route.verifiedAt=1;
        java.util.List<Models.Song> songs=java.util.Collections.singletonList(new Models.Song(route));
        assertTrue(DailyCharts.hasUnfailedRoute(songs));route.error="播放失败";assertFalse(DailyCharts.hasUnfailedRoute(songs));
    }
    @Test public void datesNeverUseFetchTimeAsPublicationTime() throws Exception {
        assertEquals(0,DailyCharts.timestamp(new JSONObject().put("fetchedAt",1789516800000L)));
        assertEquals(1789516800000L,DailyCharts.parseTimestamp("2026-09-16"));
        assertEquals(1789516800000L,DailyCharts.parseTimestamp("1789516800"));
        assertEquals(1789516800000L,DailyCharts.parseTimestamp("2026-09-16T00:00:00Z"));
        assertEquals(0,DailyCharts.parseTimestamp("每日更新"));
        assertEquals(0,DailyCharts.parseTimestamp("2026-02-30"));
    }
    @Test public void failedEmptyOrOlderCandidatesNeverReplaceSuccessfulCache(){
        assertFalse(DailyCharts.canReplace(20,30,0));assertFalse(DailyCharts.canReplace(20,0,3));assertFalse(DailyCharts.canReplace(20,10,3));
        assertTrue(DailyCharts.canReplace(20,20,1));assertTrue(DailyCharts.canReplace(0,0,1));assertTrue(DailyCharts.canReplace(20,30,3));
    }
    @Test public void staleOrDisabledCacheDoesNotBlockWorkingReplacement(){assertTrue(DailyCharts.canReplace(20,0,1,false));assertTrue(DailyCharts.canReplace(20,10,1,false));assertFalse(DailyCharts.canReplace(20,10,1,true));assertFalse(DailyCharts.canReplace(20,30,0,false));}
    @Test public void knownChartSourcesPrecedeLegacyAndLyrics(){
        Models.Source unknown=new Models.Source("unknown"),lyrics=new Models.Source("lyrics"),chart=new Models.Source("chart");lyrics.music=false;chart.topLists=true;
        java.util.List<Models.Source> selected=DailyCharts.selectSources(java.util.Arrays.asList(unknown,lyrics,chart));assertEquals(chart,selected.get(0));assertEquals(2,selected.size());
    }
    @Test public void cachedRouteRetainsDecodeQualityAndMime() throws Exception {
        Models.Route route=new Models.Route("id","source",new JSONObject().put("title","song"));route.quality="low";route.mimeType="audio/mpeg";route.verifiedPlayable=true;route.verifiedAt=100;
        Models.Route restored=DailyCharts.restoreSong(DailyCharts.cacheSong(new Models.Song(route))).routes.get(0);assertEquals("low",restored.quality);assertEquals("audio/mpeg",restored.mimeType);assertEquals(100,restored.verifiedAt);assertTrue(restored.verifiedPlayable);
    }
    @Test public void rejectsImplausibleFutureTimestamp(){assertEquals(0,DailyCharts.parseTimestamp("9999999999999"));assertEquals(0,DailyCharts.parseTimestamp("2099-01-01"));}
    @Test public void brokenCandidateYieldsToNextBoardAfterTwoAttemptsOrBudget(){
        assertTrue(DailyCharts.mayProbeCandidate(false,0,100,200));assertTrue(DailyCharts.mayProbeCandidate(false,1,100,200));
        assertFalse(DailyCharts.mayProbeCandidate(false,2,100,200));assertFalse(DailyCharts.mayProbeCandidate(false,1,200,200));
        assertTrue(DailyCharts.mayProbeCandidate(true,2,300,200));
    }
    @Test public void ttlIncludesClockRollback(){assertTrue(DailyCharts.stale(0,100));assertTrue(DailyCharts.stale(200,100));assertFalse(DailyCharts.stale(100,101));assertTrue(DailyCharts.stale(100,100+DailyCharts.DAY));}
}
