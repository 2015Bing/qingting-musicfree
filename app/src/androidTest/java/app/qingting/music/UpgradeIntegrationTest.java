package app.qingting.music;
import android.content.*;import android.os.SystemClock;import androidx.test.ext.junit.runners.AndroidJUnit4;import androidx.test.platform.app.InstrumentationRegistry;import org.junit.*;import org.junit.runner.RunWith;import org.json.*;import java.util.*;import java.util.concurrent.atomic.*;import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) @androidx.media3.common.util.UnstableApi
public class UpgradeIntegrationTest {
 private MusicRepository repo;
 private void ui(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
 private void until(java.util.function.BooleanSupplier test)throws Exception{long until=System.currentTimeMillis()+30000;AtomicBoolean done=new AtomicBoolean();while(System.currentTimeMillis()<until){ui(()->done.set(test.getAsBoolean()));if(done.get())return;Thread.sleep(100);}throw new AssertionError("Timed out: "+(PlaybackService.instance==null?"no player":PlaybackService.instance.status));}
 @Test public void fixedSourceQueueTimerAndBatch()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();SharedPreferences.Editor defaults=c.getSharedPreferences("library",0).edit();for(String url:MusicRepository.DEFAULT_SUBSCRIPTIONS)defaults.putBoolean("defaultAttempted:"+url,true);defaults.commit();
  android.app.Activity home=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));repo=((MusicApp)c.getApplicationContext()).repository;until(()->PlaybackService.instance!=null);
  Models.Source good=new Models.Source("http://127.0.0.1:18765/upgrade-good.js");good.name="验收固定来源";good.script="module.exports={platform:'fixture',version:'1',async getMediaSource(){return {url:'http://127.0.0.1:18765/tone.wav'};}};";
  Models.Source bad=new Models.Source("http://127.0.0.1:18765/upgrade-bad.js");bad.name="验收失效来源";bad.script="module.exports={platform:'fixture',version:'1',async getMediaSource(){throw new Error('fixed failure');}};";
  Models.Song first=new Models.Song(new Models.Route(good,new JSONObject().put("title","验收 · 第一首").put("artist","本地测试")));
  Models.Song second=new Models.Song(new Models.Route(good,new JSONObject().put("title","验收 · 第二首").put("artist","本地测试")));
  Models.Song fixed=first.snapshot(first.routes.get(0)),next=second.snapshot(second.routes.get(0));
  ui(()->{repo.sources.add(good);repo.sources.add(bad);PlaybackService.instance.play(fixed,Arrays.asList(fixed,next),null);});until(()->PlaybackService.instance.player.isPlaying());
  ui(()->{PlaybackService p=PlaybackService.instance;assertEquals(good.id,p.route.sourceId);p.mode=1;p.next(1);});until(()->PlaybackService.instance.player.isPlaying()&&PlaybackService.instance.current.key.equals(next.key));
  ui(()->PlaybackService.instance.next(1));until(()->PlaybackService.instance.player.isPlaying()&&PlaybackService.instance.current.key.equals(fixed.key));
  Models.Song broken=new Models.Song(new Models.Route(bad,first.routes.get(0).raw));broken.routes.add(first.routes.get(0));Models.Song pinnedBad=broken.snapshot(broken.routes.get(0));ui(()->PlaybackService.instance.play(pinnedBad,Arrays.asList(pinnedBad),null));until(()->PlaybackService.instance.needsRetry());ui(()->assertEquals(bad.id,PlaybackService.instance.route.sourceId));
  ui(()->PlaybackService.instance.play(fixed,Arrays.asList(fixed,next),null));until(()->PlaybackService.instance.player.isPlaying());
  java.lang.reflect.Field field=PlaybackService.class.getDeclaredField("sleep");field.setAccessible(true);SleepTimer timer=(SleepTimer)field.get(PlaybackService.instance);ui(()->timer.minutes(1,SystemClock.elapsedRealtime()-60001));until(()->!PlaybackService.instance.wantsPlayback());ui(()->assertFalse(PlaybackService.instance.player.getPlayWhenReady()));
  String name="验收歌单 "+System.currentTimeMillis();AtomicBoolean saved=new AtomicBoolean();ui(()->repo.editLibrary(l->{String id=l.create(name).id;assertEquals(2,l.addToPlaylist(id,Arrays.asList(fixed,next)));assertEquals(0,l.addToPlaylist(id,Arrays.asList(fixed,next)));},(ok,error)->{assertTrue(error,ok);saved.set(true);}));until(saved::get);
  ui(()->{MusicRepository restored=new MusicRepository(c);assertTrue(restored.library.playlists.stream().anyMatch(l->l.name.equals(name)&&l.songs.size()==2));PlaybackService.instance.clearQueue();repo.sources.remove(good);repo.sources.remove(bad);home.finish();});
 }
}
