package app.qingting.music;
import org.junit.Test;import org.json.*;import static org.junit.Assert.*;
public class ChartArchiveTest {
 @Test public void lastVisitedOrderDoesNotExtendExpiryAndLargeArchiveEvictsOldest()throws Exception{
  Models.Source s=new Models.Source("s");ChartArchive a=new ChartArchive();for(int n=0;n<3;n++)a.record("k"+n,new Charts.Board(s,"",new JSONObject().put("id",n)),1,page(n,true),1000+n);
  a.touch("k0");assertEquals("k0",a.entries(2000).get(2).key);assertEquals(1000,a.entries(2000).get(2).at);int size=a.boundedBytes(100000).length;ChartArchive trimmed=ChartArchive.from(new JSONArray(new String(a.boundedBytes(size-1),java.nio.charset.StandardCharsets.UTF_8)),2000);assertEquals(2,trimmed.entries(2000).size());assertEquals("k2",trimmed.entries(2000).get(0).key);
 }
 @Test public void restartKeepsPagesCursorRankAndExpiry()throws Exception{
  Models.Source source=new Models.Source("https://test/source");Charts.Board board=new Charts.Board(source,"group",new JSONObject().put("id","a"));ChartArchive archive=new ChartArchive();
  archive.record("config-a",board,1,page(1,false),1000);archive.record("config-a",board,2,page(2,false).put("topListItem",new JSONObject().put("cursor","next")),2000);
  ChartArchive restored=ChartArchive.from(new JSONArray(archive.json().toString()),2500);ChartArchive.Entry e=restored.entries(2500).get(0);assertEquals("config-a",e.key);assertEquals(2,e.charts.page);assertEquals(2,e.charts.songs.size());assertEquals(2,e.charts.rank(e.charts.songs.get(1)));assertEquals("next",e.charts.detailRaw.getString("cursor"));
  assertTrue(restored.entries(1802001).isEmpty());
 }
 @Test public void refreshReplacesPagesAndRemovalSurvivesRestart()throws Exception{
  Models.Source source=new Models.Source("url");Charts.Board b=new Charts.Board(source,"",new JSONObject().put("id","a"));ChartArchive a=new ChartArchive();a.record("k",b,1,page(1,false),1000);a.record("k",b,2,page(2,true),1100);a.record("k",b,1,page(3,true),1200);
  assertEquals(1,a.entries(1300).get(0).charts.songs.size());a.remove("k");assertTrue(ChartArchive.from(a.json(),1300).entries(1300).isEmpty());
  assertTrue(ChartArchive.from(new JSONArray().put(new JSONObject().put("bad",true)),1300).entries(1300).isEmpty());
 }
 private JSONObject page(int id,boolean end)throws Exception{return new JSONObject().put("isEnd",end).put("musicList",new JSONArray().put(new JSONObject().put("id",id).put("title","song"+id)));}
}
