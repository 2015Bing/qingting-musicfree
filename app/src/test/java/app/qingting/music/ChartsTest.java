package app.qingting.music;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;

public class ChartsTest {
    @Test public void switchingRestoresAllPagesRanksCursorAndVerificationWithoutFetching() throws Exception {
        Charts charts=new Charts();Charts.Board a=board("a"),b=board("b");
        charts.append(charts.select(a),1,page(false,1,2));
        charts.append(charts.token(),2,page(false,2,3).put("topListItem",new JSONObject().put("cursor","page3")));
        Models.Song checked=charts.songs.get(0);checked.routes.get(0).verifiedAt=1000;checked.routes.get(0).verifiedPlayable=true;
        charts.remember("source-config:a",1000);int stale=charts.select(b);
        assertTrue(charts.restore(a,"source-config:a",2000));assertEquals(2,charts.page);assertEquals(3,charts.songs.size());
        assertSame(checked,charts.songs.get(0));assertEquals(4,charts.rank(charts.songs.get(2)));assertEquals("page3",charts.detailRaw.getString("cursor"));
        assertNull(SongPolicy.nextProbe(checked,r->true,new java.util.HashSet<>(),2000));
        assertFalse(charts.append(stale,3,page(true,9)));assertTrue(charts.append(charts.token(),3,page(true,3,4)));assertEquals(4,charts.songs.size());
    }
    @Test public void chartCacheExpiresAndReadsDoNotExtendIt() throws Exception {
        Charts charts=new Charts();Charts.Board a=board("a");charts.append(charts.select(a),1,page(true,1));charts.remember("a",1000);
        assertTrue(charts.restore(a,"a",1800000));assertFalse(charts.restore(a,"a",1801001));
        charts.remember("a",2000);assertFalse(charts.restore(a,"a",1999));
    }
    @Test public void chartCacheConfigurationRefreshAndCapacityAreIsolated() throws Exception {
        Charts charts=new Charts();for(int i=0;i<25;i++){charts.append(charts.select(board(""+i)),1,page(true,i));charts.remember("config:"+i,1000);}
        assertFalse(charts.restore(board("0"),"config:0",2000));assertTrue(charts.restore(board("24"),"config:24",2000));
        assertFalse(charts.restore(board("24"),"new-config:24",2000));charts.forget("config:24");assertFalse(charts.restore(board("24"),"config:24",2000));
        charts.clearCache();assertFalse(charts.restore(board("23"),"config:23",2000));
    }
    @Test public void seedKeepsCachedInstancesContinuationAndOriginalRanks() throws Exception {
        Charts original=new Charts();Charts.Board board=board("one");JSONObject first=page(false,1,1,2).put("topListItem",new JSONObject().put("cursor","next"));
        original.append(original.select(board),1,first);Models.Song cached=original.songs.get(1);cached.routes.get(0).verifiedPlayable=true;
        Charts charts=new Charts();int stale=charts.select(board("old"));charts.seed(board,first,original.songs);
        assertSame(cached,charts.songs.get(1));assertEquals(3,charts.rank(cached));assertEquals(1,charts.page);assertFalse(charts.end);assertEquals("next",charts.detailRaw.getString("cursor"));assertEquals("keep",charts.detailRaw.getString("token"));
        assertFalse(charts.append(stale,2,page(true,9)));assertTrue(charts.append(charts.token(),2,page(true,2,3)));assertEquals(3,charts.songs.size());assertEquals(5,charts.rank(charts.songs.get(2)));
    }
    private final Models.Source source=new Models.Source("https://example.test/source.js");
    private Charts.Board board(String id) throws Exception {return new Charts.Board(source,"精选",new JSONObject().put("id",id).put("title",id).put("token","keep"));}
    private JSONObject page(boolean end,int... ids) throws Exception {JSONArray songs=new JSONArray();for(int id:ids)songs.put(new JSONObject().put("id",id).put("title","歌"+id).put("artist","歌手").put("extra","keep"));return new JSONObject().put("isEnd",end).put("musicList",songs);}
    @Test public void groupsPreserveRawBoardAndSource() throws Exception {
        JSONArray groups=new JSONArray().put(new JSONObject().put("title","精选").put("data",new JSONArray().put(board("1").raw)));
        Charts.Board parsed=Charts.parseBoards(source,groups).get(0);assertEquals("keep",parsed.raw.getString("token"));assertEquals(source.id,parsed.sourceId);assertEquals("精选",parsed.group);
    }
    @Test public void lateResponseCannotOverwriteNewSelection() throws Exception {
        Charts charts=new Charts();int old=charts.select(board("old"));int current=charts.select(board("new"));
        assertFalse(charts.append(old,1,page(true,1)));assertTrue(charts.append(current,1,page(true,2)));assertEquals("歌2",charts.songs.get(0).title);
        charts.clearSelection();assertFalse(charts.append(current,2,page(true,3)));
    }
    @Test public void pagesKeepOrderDeduplicateAndDefaultToEnd() throws Exception {
        Charts charts=new Charts();int token=charts.select(board("one"));assertTrue(charts.append(token,1,page(false,1,2)));assertFalse(charts.end);
        assertTrue(charts.append(token,2,page(false,2,3)));assertEquals(3,charts.songs.size());assertEquals("keep",charts.songs.get(2).routes.get(0).raw.getString("extra"));
        assertTrue(charts.append(token,3,page(false,3)));assertTrue(charts.end);
        token=charts.select(board("two"));JSONObject data=page(false,1);data.remove("isEnd");charts.append(token,1,data);assertTrue(charts.end);
    }
    @Test public void malformedPageDoesNotAdvanceAndPreservesBoardExtras() throws Exception {
        Charts charts=new Charts();int token=charts.select(board("one"));
        assertThrows(JSONException.class,()->charts.append(token,1,new JSONObject()));assertEquals(0,charts.page);
        JSONObject data=page(false,1).put("topListItem",new JSONObject().put("cursor","next"));charts.append(token,1,data);
        assertEquals("keep",charts.detailRaw.getString("token"));assertEquals("next",charts.detailRaw.getString("cursor"));
    }
}
