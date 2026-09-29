package app.qingting.music;
import org.junit.Test;import java.util.*;import java.util.concurrent.Executor;import static org.junit.Assert.*;
public class LibraryTransactionsTest {
 @Test public void loadedLegacyFavoritesSurviveFirstSuccessfulWriteAfterMigrationFailure()throws Exception{
  Models.Source source=new Models.Source("legacy");org.json.JSONArray old=new org.json.JSONArray().put(new Models.Song(new Models.Route(source,new org.json.JSONObject().put("id",1).put("title","saved"))).json());LibraryState loaded=LibraryState.migrateLegacy(old);List<LibraryState> states=new ArrayList<>();LibraryTransactions tx=new LibraryTransactions(loaded.json().toString(),Runnable::run,Runnable::run,json->true);tx.submit(l->l.remember("query"),(l,e)->states.add(l));assertEquals(1,states.get(0).favorites.size());assertEquals("saved",states.get(0).favorites.get(0).title);assertEquals("query",states.get(0).history.get(0));
 }
 @Test public void editsWaitForWorkerAndPublishOnlyAfterSuccessfulCommit() throws Exception {
  List<Runnable> work=new ArrayList<>(),ui=new ArrayList<>();List<String> saved=new ArrayList<>();List<LibraryState> published=new ArrayList<>();
  LibraryTransactions tx=new LibraryTransactions(new LibraryState().json().toString(),work::add,ui::add,json->{saved.add(json);return saved.size()!=2;});
  tx.submit(l->l.create("one"),(l,e)->{assertNull(e);published.add(l);});
  tx.submit(l->l.create("failed"),(l,e)->{assertNull(l);assertNotNull(e);});
  tx.submit(l->l.create("three"),(l,e)->{assertNull(e);published.add(l);});
  assertTrue(saved.isEmpty());for(Runnable r:work)r.run();assertTrue(published.isEmpty());for(Runnable r:ui)r.run();
  assertEquals(1,published.get(0).playlists.size());assertEquals(2,published.get(1).playlists.size());assertEquals("three",published.get(1).playlists.get(1).name);
 }
 @Test public void duplicateQueuedNamesAreValidatedAgainstLatestCommittedState() throws Exception {
  List<Runnable> work=new ArrayList<>();int[] success={0},failure={0};LibraryTransactions tx=new LibraryTransactions(new LibraryState().json().toString(),work::add,Runnable::run,j->true);
  for(int i=0;i<2;i++)tx.submit(l->l.create("same"),(l,e)->{if(e==null)success[0]++;else failure[0]++;});for(Runnable r:work)r.run();assertEquals(1,success[0]);assertEquals(1,failure[0]);
 }
}
