package app.qingting.music;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class ChartFilterTest {
    private JSONObject page(boolean end,JSONObject... songs)throws Exception{JSONArray a=new JSONArray();for(JSONObject s:songs)a.put(s);return new JSONObject().put("musicList",a).put("isEnd",end);}
    @Test public void onlyConfirmedEmptyIsHidden()throws Exception{
        ChartFilter f=new ChartFilter();assertEquals(-1,f.record("empty",page(true),1000));assertTrue(f.hidden("empty"));
        assertEquals(0,f.record("next",page(false),1000));assertFalse(f.hidden("next"));
        assertEquals(0,f.record("broken",new JSONObject(),1000));assertFalse(f.hidden("broken"));
        assertEquals(0,f.record("bad",new JSONObject().put("musicList",new JSONArray().put("bad")),1000));assertFalse(f.hidden("bad"));
    }
    @Test public void refreshCanRestorePreviouslyEmptyBoard()throws Exception{
        ChartFilter f=new ChartFilter();f.record("b",page(true),1000);f.record("b",page(true,new JSONObject().put("id",1)),2000);assertFalse(f.hidden("b"));assertNotNull(f.firstPage("b",2001));
    }
    @Test public void errorsAreNotEmptyAndClearOldHiddenResult()throws Exception{
        ChartFilter f=new ChartFilter();f.record("b",page(true),1000);f.record("b",null,2000);assertFalse(f.hidden("b"));assertNull(f.firstPage("b",2001));
    }
    @Test public void hiddenResultsPersistButSongPagesExpire()throws Exception{
        ChartFilter f=new ChartFilter();f.record("empty",page(true),1000);f.record("songs",page(true,new JSONObject().put("id",1)),1000);
        assertTrue(ChartFilter.from(f.json()).hidden("empty"));assertNotNull(f.firstPage("songs",2000));assertNull(f.firstPage("songs",301001));
    }
    @Test public void sourceAndConfigurationChangesInvalidateKeys()throws Exception{
        ChartFilter f=new ChartFilter();Models.Source s=new Models.Source("https://example.test/plugin");Charts.Board b=new Charts.Board(s,"",new JSONObject().put("id",1));String first=f.key(s,b);
        s.variables.put("token","new");assertNotEquals(first,f.key(s,b));String second=f.key(s,b);s.script="new";assertNotEquals(second,f.key(s,b));
    }
    @Test public void cachedFirstPageIsIndependentAndBounded()throws Exception{
        ChartFilter f=new ChartFilter();JSONObject p=page(true,new JSONObject().put("id",1));for(int i=0;i<25;i++)f.record("b"+i,p,1000);
        assertNull(f.firstPage("b0",2000));JSONObject copy=f.firstPage("b24",2000);copy.put("musicList",new JSONArray());assertEquals(1,f.firstPage("b24",2000).getJSONArray("musicList").length());
    }

    @Test public void httpFailureIsTemporarilyHiddenAndPersists()throws Exception{
        ChartFilter f=new ChartFilter();f.failure("b","HTTP 503",1000);assertTrue(f.hidden("b",2000));assertEquals("HTTP 503",f.reason("b",2000));
        assertTrue(ChartFilter.from(f.json()).hidden("b",2000));assertFalse(f.hidden("b",1801001));
    }
    @Test public void successfulRefreshRestoresFailedBoard()throws Exception{
        ChartFilter f=new ChartFilter();f.failure("b","HTTP 503",1000);f.record("b",page(true,new JSONObject().put("id",1)),2000);assertFalse(f.hidden("b",2001));
    }
    @Test public void playbackFilteredSourcesStillOfferCharts(){
        Models.Source s=new Models.Source("https://example.test/plugin");for(int i=0;i<3;i++)s.playbackHealth.record("song"+i,false,true);
        assertTrue(s.hidden());assertTrue(Charts.sourceAvailable(s));s.enabled=false;assertFalse(Charts.sourceAvailable(s));
    }

    @Test public void laterNonemptyPageKeepsCorrectContinuation()throws Exception{
        ChartFilter f=new ChartFilter();JSONObject data=page(false,new JSONObject().put("id",7));f.record("b",data,1000,3);
        Models.Source source=new Models.Source("https://example.test/source");Charts.Board board=new Charts.Board(source,"",new JSONObject().put("id",1));Charts charts=new Charts();charts.seedPage(board,f.firstPage("b",2000),f.firstPageNumber("b"));
        assertEquals(3,charts.page);assertEquals(1,charts.songs.size());assertTrue(charts.append(charts.token(),4,page(true,new JSONObject().put("id",8))));assertEquals(2,charts.songs.size());
    }
}
