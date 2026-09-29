package app.qingting.music;
import org.json.*;import org.junit.Test;import static org.junit.Assert.*;import static app.qingting.music.Models.*;
public class SheetsTest {
 private Source source(String name){Source s=new Source("https://test/"+name);s.name=name;s.script="v1";return s;}
 private JSONObject raw(String id)throws Exception{return new JSONObject().put("id",id).put("title","同名歌单").put("token","keep");}
 @Test public void identityKeepsSourceAndCompositePrimaryKeys()throws Exception{
  Source a=source("a"),b=source("b");JSONArray keys=new JSONArray("[\"id\",\"kind\"]");
  Sheets.Item one=new Sheets.Item(a,raw("1").put("kind","a"),keys);
  assertNotEquals(one.key,new Sheets.Item(b,one.raw,keys).key);
  assertNotEquals(one.key,new Sheets.Item(a,raw("1").put("kind","b"),keys).key);
  assertEquals("keep",one.raw.getString("token"));assertEquals("a",one.sourceName);
 }
 @Test public void detailKeepsEmptyFirstPageContinuationAndSupplement()throws Exception{
  Sheets.Item item=new Sheets.Item(source("a"),raw("1"),null);Sheets.Detail detail=new Sheets.Detail(item);
  detail=detail.append(new JSONObject("{\"musicList\":[],\"isEnd\":false,\"sheetItem\":{\"cursor\":\"next\"}}"));
  assertFalse(detail.end);assertEquals(1,detail.page);assertEquals("keep",detail.raw.getString("token"));assertEquals("next",detail.raw.getString("cursor"));
  detail=detail.append(new JSONObject("{\"musicList\":[{\"id\":1,\"title\":\"歌\",\"artist\":\"人\"}],\"isEnd\":false}"));
  detail=detail.append(new JSONObject("{\"musicList\":[{\"id\":1,\"title\":\"歌\",\"artist\":\"人\"},{\"id\":2,\"title\":\"歌\",\"artist\":\"人\"}]}"));
  assertEquals(2,detail.songs.size());assertTrue(detail.end);assertEquals(3,detail.page);assertFalse(detail.songs.get(0).fixed());
  Sheets.Detail restored=Sheets.Detail.from(item,new JSONObject(detail.json().toString()));assertEquals(2,restored.songs.size());assertEquals("next",restored.raw.getString("cursor"));assertEquals(3,restored.page);
 }
 @Test public void malformedDetailDoesNotAdvance()throws Exception{Sheets.Detail d=new Sheets.Detail(new Sheets.Item(source("a"),raw("1"),null));assertThrows(JSONException.class,()->d.append(new JSONObject()));assertEquals(0,d.page);}
 @Test public void configurationChangesInvalidateCache()throws Exception{Source a=source("a");String key=Sheets.fingerprint(a);a.variables.put("token","new");assertNotEquals(key,Sheets.fingerprint(a));key=Sheets.fingerprint(a);a.script="v2";assertNotEquals(key,Sheets.fingerprint(a));}
}
