package app.qingting.music;
import org.junit.Test;import org.junit.Rule;import org.junit.rules.TemporaryFolder;import org.json.*;import java.util.*;import java.util.concurrent.Executor;import static app.qingting.music.Models.*;import static org.junit.Assert.*;
public class OnlineSheetsTest {
 @Rule public TemporaryFolder folder=new TemporaryFolder();
 static class Tasks implements Executor {final ArrayDeque<Runnable> tasks=new ArrayDeque<>();public void execute(Runnable r){tasks.add(r);}void drain(){while(!tasks.isEmpty())tasks.remove().run();}}
 private Source source(String id){Source s=new Source("https://test/"+id);s.name=id;s.script=id;return s;}
 @Test public void unsupportedSkippedFailuresRetryAndSourcesRemainSeparate()throws Exception{
  Tasks work=new Tasks();List<Source> sources=Arrays.asList(source("a"),source("b"),source("no"));List<String> calls=new ArrayList<>();boolean[] fail={true};
  OnlineSheets state=new OnlineSheets(()->sources,(s,m,args,valid)->{calls.add(s.name+":"+m);if(m.equals("metadata"))return new JSONObject().put("sheets",!s.name.equals("no"));if(s.name.equals("b")&&fail[0])throw new Exception("HTTP 503");return new JSONObject("{\"isEnd\":true,\"data\":[{\"id\":1,\"title\":\"same\"}]}");},work,work,Runnable::run,new LocalFileCache(folder.newFolder(),100000,1800000),()->{});
  state.search("test",false);work.drain();assertEquals(1,state.items.size());assertEquals(2,state.supported.size());assertTrue(state.hasMore());assertFalse(calls.contains("no:search"));fail[0]=false;state.loadMore();work.drain();assertEquals(2,state.items.size());assertFalse(state.hasMore());
  int callsBefore=calls.size();state.search("test",false);work.drain();assertEquals(callsBefore,calls.size());assertEquals(2,state.items.size());
 }
 @Test public void oldResponseCannotReplaceNewQueryOrDisabledSource()throws Exception{
  Tasks work=new Tasks(),main=new Tasks();Source s=source("a");OnlineSheets state=new OnlineSheets(()->Arrays.asList(s),(src,m,args,valid)->m.equals("metadata")?new JSONObject().put("sheets",true):new JSONObject().put("data",new JSONArray().put(new JSONObject().put("id",args.optString(0)).put("title",args.optString(0)))),work,work,main,new LocalFileCache(folder.newFolder(),100000,1800000),()->{});
  state.search("old",false);work.drain();state.search("new",false);main.drain();assertTrue(state.items.isEmpty());work.drain();s.enabled=false;main.drain();assertTrue(state.items.isEmpty());assertFalse(state.searching);
 }
 @Test public void detailCacheRestoresPagesAndRefreshFailureKeepsSongs()throws Exception{
  Tasks work=new Tasks();Source s=source("a");int[] calls={0};boolean[] fail={false};OnlineSheets state=new OnlineSheets(()->Arrays.asList(s),(src,m,args,valid)->{calls[0]++;if(fail[0])throw new Exception("503");return new JSONObject().put("musicList",new JSONArray().put(new JSONObject().put("id",args.optInt(1)).put("title","song"))).put("isEnd",args.optInt(1)>=2);},work,work,Runnable::run,new LocalFileCache(folder.newFolder(),100000,1800000),()->{});
  Sheets.Item item=new Sheets.Item(s,new JSONObject().put("id",1),null);state.open(item,false);work.drain();state.loadDetail();work.drain();assertEquals(2,state.detail.songs.size());state.closeDetail();state.open(item,false);work.drain();assertEquals(2,calls[0]);assertEquals(2,state.detail.page);fail[0]=true;state.open(item,true);work.drain();assertEquals(2,state.detail.songs.size());assertTrue(state.detailError.contains("503"));
 }
 @Test public void failedRefreshRetryStartsFromPageOneAndPauseKeepsState()throws Exception{
  Tasks work=new Tasks();Source s=source("a");List<Integer> requested=new ArrayList<>();boolean[] fail={false};OnlineSheets state=new OnlineSheets(()->Arrays.asList(s),(src,m,args,valid)->{requested.add(args.optInt(1));if(fail[0])throw new Exception("503");return new JSONObject().put("musicList",new JSONArray().put(new JSONObject().put("id",args.optInt(1)).put("title","song"))).put("isEnd",false);},work,work,Runnable::run,new LocalFileCache(folder.newFolder(),100000,1800000),()->{});
  Sheets.Item item=new Sheets.Item(s,new JSONObject().put("id",1),null);state.open(item,false);work.drain();state.loadDetail();work.drain();fail[0]=true;state.open(item,true);work.drain();fail[0]=false;state.retryDetail();work.drain();assertEquals(Arrays.asList(1,2,1,1),requested);assertEquals(1,state.detail.page);
  state.loadDetail();state.pauseDetail();work.drain();assertEquals(1,state.detail.page);assertFalse(state.loadingDetail);
 }
 @Test public void emptySearchPageWithContinuationDoesNotHideLaterResults()throws Exception{
  Tasks work=new Tasks();Source s=source("a");OnlineSheets state=new OnlineSheets(()->Arrays.asList(s),(src,m,args,valid)->m.equals("metadata")?new JSONObject().put("sheets",true):new JSONObject().put("isEnd",args.optInt(1)>=2).put("data",args.optInt(1)==1?new JSONArray():new JSONArray().put(new JSONObject().put("id",1).put("title","later"))),work,work,Runnable::run,new LocalFileCache(folder.newFolder(),100000,1800000),()->{});
  state.search("test",false);work.drain();assertTrue(state.hasMore());state.loadMore();work.drain();assertEquals(1,state.items.size());assertFalse(state.hasMore());
 }
 @Test public void inFlightVariablesMutationRejectsOldSearchAndDetail()throws Exception{
  Tasks work=new Tasks(),main=new Tasks();Source s=source("a");s.variables.put("account","old");OnlineSheets state=new OnlineSheets(()->Arrays.asList(s),(src,m,args,valid)->m.equals("metadata")?new JSONObject().put("sheets",true):m.equals("search")?new JSONObject().put("data",new JSONArray().put(new JSONObject().put("id",1).put("title","old"))):new JSONObject().put("musicList",new JSONArray().put(new JSONObject().put("id",1).put("title","old"))),work,work,main,new LocalFileCache(folder.newFolder(),100000,1800000),()->{});
  state.search("test",true);work.drain();s.variables.put("account","new");main.drain();assertTrue(state.items.isEmpty());
  Sheets.Item item=new Sheets.Item(s,new JSONObject().put("id",1),null);state.open(item,true);work.drain();s.variables.put("account","another");main.drain();assertTrue(state.detail.songs.isEmpty());assertTrue(state.detailError.contains("更新"));
 }
 @Test public void restartingReadsSearchAndAllDetailPagesFromDisk()throws Exception{
  Tasks work=new Tasks();Source source=source("a");LocalFileCache cache=new LocalFileCache(folder.newFolder(),100000,1800000);int[] calls={0};OnlineSheets.Invoker invoke=(src,m,args,valid)->{calls[0]++;if(m.equals("metadata"))return new JSONObject().put("sheets",true);if(m.equals("search"))return new JSONObject().put("data",new JSONArray().put(new JSONObject().put("id",1).put("title","sheet")));return new JSONObject().put("musicList",new JSONArray().put(new JSONObject().put("id",args.optInt(1)).put("title","song"))).put("sheetItem",new JSONObject().put("cursor","saved")).put("isEnd",args.optInt(1)>=2);};
  OnlineSheets first=new OnlineSheets(()->Arrays.asList(source),invoke,work,work,Runnable::run,cache,()->{});first.search("test",false);work.drain();first.open(first.items.get(0),false);work.drain();first.loadDetail();work.drain();int requests=calls[0];
  OnlineSheets restored=new OnlineSheets(()->Arrays.asList(source),invoke,work,work,Runnable::run,cache,()->{});restored.search("test",false);work.drain();restored.open(restored.items.get(0),false);work.drain();assertEquals(requests,calls[0]);assertEquals(2,restored.detail.page);assertEquals(2,restored.detail.songs.size());assertEquals("saved",restored.detail.raw.optString("cursor"));
 }
 @Test public void expiredCacheRefreshFailureKeepsCurrentlyDisplayedSongs()throws Exception{
  Tasks work=new Tasks();Source source=source("a");boolean[] fail={false};OnlineSheets state=new OnlineSheets(()->Arrays.asList(source),(src,m,args,valid)->{if(fail[0])throw new Exception("503");return new JSONObject().put("musicList",new JSONArray().put(new JSONObject().put("id",1).put("title","keep")));},work,work,Runnable::run,new LocalFileCache(folder.newFolder(),100000,1800000),()->{});
  Sheets.Item item=new Sheets.Item(source,new JSONObject().put("id",1),null);state.open(item,false);work.drain();Sheets.Detail shown=state.detail;
  java.lang.reflect.Field field=OnlineSheets.class.getDeclaredField("details");field.setAccessible(true);Object cached=((Map<?,?>)field.get(state)).values().iterator().next();java.lang.reflect.Field at=cached.getClass().getDeclaredField("at");at.setAccessible(true);at.setLong(cached,System.currentTimeMillis()-1800001);
  fail[0]=true;state.open(item,true);assertSame(shown,state.detail);work.drain();assertSame(shown,state.detail);assertTrue(state.detailError.contains("503"));
 }
}
