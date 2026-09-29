package app.qingting.music;
import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@androidx.media3.common.util.UnstableApi
public class PlayerIntegrationTest {
    private MusicRepository repo;
    private void ui(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private void until(java.util.function.BooleanSupplier condition) throws Exception {
        long end=System.currentTimeMillis()+90000;AtomicBoolean ok=new AtomicBoolean();
        while(System.currentTimeMillis()<end){ui(()->ok.set(condition.getAsBoolean()));if(ok.get())return;Thread.sleep(200);}throw new AssertionError("Timed out waiting for app state; "+repo.notice);
    }
    @Test public void subscriptionSearchProbeFallbackFavoritesAndCooldown() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Verify playback without a saved activation state.
        assertTrue(context.getSharedPreferences("activation",Context.MODE_PRIVATE).edit().clear().commit());
        // Keep device fixtures isolated from the real default subscriptions.
        android.content.SharedPreferences.Editor defaults=context.getSharedPreferences("library",Context.MODE_PRIVATE).edit();
        for(String url:MusicRepository.DEFAULT_SUBSCRIPTIONS)defaults.putBoolean("defaultAttempted:"+url,true);
        defaults.commit();
        MainActivity home=(MainActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        repo=((MusicApp)context.getApplicationContext()).repository;
        until(()->PlaybackService.instance!=null);ui(home::finish);
        ui(()->repo.importUrl("http://127.0.0.1:18765/plugins.json"));until(()->!repo.importing);
        ui(()->assertEquals(repo.notice,4,repo.sources.size()));
        Models.Source retained=repo.sources.get(0);
        ui(()->{retained.enabled=false;repo.save();repo.ensureSubscriptions(java.util.Arrays.asList("http://127.0.0.1:18765/missing.json","http://127.0.0.1:18765/plugins.json"));});
        until(()->!repo.importing);
        ui(()->{assertEquals(4,repo.sources.size());assertSame(retained,repo.source(retained.id));assertFalse(retained.enabled);assertTrue(repo.defaultSubscriptionStatus("http://127.0.0.1:18765/missing.json").contains("失败 1"));assertTrue(repo.defaultSubscriptionStatus("http://127.0.0.1:18765/plugins.json").contains("已有"));retained.enabled=true;repo.save();});
        ui(repo::refreshCharts);until(()->!repo.charts.loadingCatalog);
        ui(()->{assertEquals(2,repo.charts.boards.size());repo.openChart(repo.charts.boards.get(0));});until(()->!repo.charts.loadingSongs);
        ui(()->{assertEquals(3,repo.charts.songs.size());assertEquals("chart-token",repo.charts.songs.get(0).routes.get(0).raw.optString("chartToken"));repo.loadMoreChart();});until(()->!repo.charts.loadingSongs);
        ui(()->{assertEquals(4,repo.charts.songs.size());assertTrue(repo.charts.end);});
        Charts.Board manuallySelected=repo.charts.current;
        AtomicBoolean dailyDone=new AtomicBoolean();ui(()->repo.dailyCharts.refresh(true,()->dailyDone.set(true)));until(dailyDone::get);
        ui(()->{assertNotNull(repo.dailyCharts.current);assertEquals(3,repo.dailyCharts.songs.size());assertTrue(repo.dailyCharts.songs.stream().anyMatch(s->repo.playableCount(s)>0));assertTrue(repo.dailyCharts.songs.stream().anyMatch(s->repo.playableCount(s)==0));assertSame(manuallySelected,repo.charts.current);assertEquals(4,repo.charts.songs.size());repo.dailyCharts.seed(repo.charts);assertEquals(1,repo.charts.page);assertFalse(repo.charts.end);repo.loadMoreChart();});until(()->!repo.charts.loadingSongs);
        ui(()->{assertEquals(4,repo.charts.songs.size());assertTrue(repo.charts.end);repo.closeChart();});
        ui(()->repo.search("晨间"));until(()->!repo.searching);
        ui(()->{assertEquals(3,repo.results.size());assertEquals(2,repo.results.get(0).routes.size());assertEquals("preserved",repo.results.get(0).routes.get(0).raw.optString("fixtureExtra"));});
        Models.Song automaticallyChecked=repo.results.get(0);
        ui(()->repo.verifyVisible(java.util.Collections.singletonList(automaticallyChecked)));until(()->repo.playableCount(automaticallyChecked)>0);
        ui(()->{repo.cancelVisibleChecks();assertFalse(automaticallyChecked.routes.stream().anyMatch(r->r.verifying));});
        ui(()->repo.verify(repo.results.get(0)));until(()->repo.results.get(0).routes.stream().noneMatch(r->r.verifying));
        ui(()->{assertEquals(1,repo.results.get(0).routes.stream().filter(r->r.status.equals("试播通过")).count());assertEquals(1,repo.results.get(0).routes.stream().filter(r->r.status.equals("试播失败")).count());assertTrue(repo.sources.stream().filter(s->!s.name.contains("故障")).allMatch(s->s.failures==0));});
        ui(()->{repo.scanResults();repo.cancelResultScan();assertFalse(repo.onlyPlayable);assertFalse(repo.results.stream().flatMap(song->song.routes.stream()).anyMatch(r->r.verifying));});
        ui(repo::loadMore);until(()->!repo.searching);ui(()->assertEquals("Live must remain separate",4,repo.results.size()));
        until(()->PlaybackService.instance!=null);
        ui(()->{Models.Song song=repo.results.get(0);Models.Route failing=song.routes.stream().filter(r->r.sourceName.contains("备用")).findFirst().get();PlaybackService.instance.play(song,repo.results,failing);});
        until(()->PlaybackService.instance.player.isPlaying());
        ui(()->{assertTrue(PlaybackService.instance.route.sourceName.contains("主线路"));assertFalse(repo.recent.isEmpty());if(!repo.liked(repo.results.get(0)))repo.favorite(repo.results.get(0));});until(()->repo.liked(repo.results.get(0)));
        until(()->!PlaybackService.instance.lyrics.lines.isEmpty());
        PlaybackService playing=PlaybackService.instance;
        PlayerActivity fullPlayer=(PlayerActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(context,PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ui(()->{android.util.TypedValue floating=new android.util.TypedValue();fullPlayer.getTheme().resolveAttribute(android.R.attr.windowIsFloating,floating,true);assertEquals("Player is a full screen activity",0,floating.data);assertNotNull(fullPlayer.findViewById(R.id.player_lyrics));assertTrue(fullPlayer.findViewById(R.id.player_play).isEnabled());fullPlayer.finish();});
        until(()->fullPlayer.isDestroyed());ui(()->{assertSame(playing,PlaybackService.instance);assertTrue(playing.player.getPlayWhenReady());});
        ui(()->{assertEquals("音源自带歌词",PlaybackService.instance.lyrics.lines.get(0).text);assertEquals("low",PlaybackService.instance.route.quality);});
        ui(()->{PlaybackService player=PlaybackService.instance;player.player.pause();player.resetLyricDelay();player.adjustLyricDelay(500);player.seekLyric(player.lyrics,1);assertFalse(player.player.getPlayWhenReady());});
        until(()->Math.abs(PlaybackService.instance.player.getCurrentPosition()-2500)<200);
        ui(()->{PlaybackService.instance.resetLyricDelay();PlaybackService.instance.player.play();});
        AtomicBoolean lyricDone=new AtomicBoolean();
        ui(()->repo.loadLyrics(repo.results.get(0),null,()->true,true,(lyrics,error)->{assertNull(error);assertEquals("专用歌词源补全",lyrics.lines.get(0).text);lyricDone.set(true);}));until(lyricDone::get);
        // A real HTTP failure after playback has advanced must resume on the backup.
        Models.Song original=repo.results.get(0);
        Models.Route primary=original.routes.stream().filter(r->r.sourceName.contains("主线路")).findFirst().get();
        Models.Route backup=original.routes.stream().filter(r->r.sourceName.contains("备用")).findFirst().get();
        Models.Route primaryCopy=new Models.Route(primary.sourceId,primary.sourceName,new org.json.JSONObject(primary.raw.toString()));primaryCopy.quality="low";
        Models.Route backupCopy=new Models.Route(backup.sourceId,backup.sourceName,new org.json.JSONObject(backup.raw.toString()).put("fixturePlayable",true));backupCopy.quality="low";
        Models.Song recoverySong=new Models.Song(primaryCopy);recoverySong.routes.add(backupCopy);
        ui(()->{PlaybackService s=PlaybackService.instance;s.play(recoverySong,java.util.Collections.singletonList(recoverySong),primaryCopy);s.toggle();assertFalse(s.wantsPlayback());});
        until(()->PlaybackService.instance.player.getPlaybackState()==androidx.media3.common.Player.STATE_READY);
        ui(()->{assertFalse(PlaybackService.instance.player.getPlayWhenReady());PlaybackService.instance.toggle();});
        until(()->PlaybackService.instance.player.isPlaying());
        ui(()->{PlaybackService s=PlaybackService.instance;s.player.setMediaItem(androidx.media3.common.MediaItem.fromUri("http://127.0.0.1:18765/invalid"),42000);s.player.prepare();});
        until(()->PlaybackService.instance.route==backupCopy&&PlaybackService.instance.player.isPlaying());
        ui(()->{assertTrue(PlaybackService.instance.player.getCurrentPosition()>=42000);assertTrue(PlaybackService.instance.status.contains("自动切换"));PlaybackService.instance.player.pause();PlaybackService.instance.switchRoute(recoverySong,primaryCopy);});
        until(()->PlaybackService.instance.route==primaryCopy&&PlaybackService.instance.player.getPlaybackState()==androidx.media3.common.Player.STATE_READY);
        ui(()->{assertFalse(PlaybackService.instance.player.getPlayWhenReady());assertTrue(PlaybackService.instance.player.getCurrentPosition()>=42000);});
        Models.Route onlyFailedRoute=new Models.Route(backup.sourceId,backup.sourceName,new org.json.JSONObject(backup.raw.toString()));onlyFailedRoute.quality="low";
        Models.Song crossSourceSong=new Models.Song(onlyFailedRoute);
        ui(()->{PlaybackService s=PlaybackService.instance;s.play(crossSourceSong,java.util.Collections.singletonList(crossSourceSong),null);s.toggle();});
        until(()->PlaybackService.instance.route!=null&&PlaybackService.instance.route.sourceId.equals(primary.sourceId)&&PlaybackService.instance.player.getPlaybackState()==androidx.media3.common.Player.STATE_READY);
        ui(()->{assertSame(crossSourceSong,PlaybackService.instance.current);assertEquals(2,crossSourceSong.routes.size());assertFalse(PlaybackService.instance.player.getPlayWhenReady());});
        Models.Source laterSource=new Models.Source("http://127.0.0.1:18765/primary.js?later");laterSource.name="测试 · 后续备用";laterSource.script=repo.source(primary.sourceId).script;
        ui(()->{repo.sources.add(laterSource);PlaybackService s=PlaybackService.instance;s.player.setMediaItem(androidx.media3.common.MediaItem.fromUri("http://127.0.0.1:18765/invalid"),10000);s.player.prepare();});
        until(()->PlaybackService.instance.route!=null&&PlaybackService.instance.route.sourceId.equals(laterSource.id)&&PlaybackService.instance.player.getPlaybackState()==androidx.media3.common.Player.STATE_READY);
        ui(()->{assertFalse(PlaybackService.instance.player.getPlayWhenReady());assertTrue(PlaybackService.instance.player.getCurrentPosition()>=10000);repo.sources.remove(laterSource);repo.save();});
        Models.Route retryRoute=new Models.Route(backup.sourceId,backup.sourceName,new org.json.JSONObject(backup.raw.toString()).put("title","不存在的测试曲"));retryRoute.quality="low";
        Models.Song retrySong=new Models.Song(retryRoute);
        ui(()->PlaybackService.instance.play(retrySong,java.util.Collections.singletonList(retrySong),null));
        until(()->PlaybackService.instance.needsRetry());ui(()->assertFalse(PlaybackService.instance.wantsPlayback()));
        retryRoute.raw.put("fixturePlayable",true);
        ui(()->PlaybackService.instance.toggle());until(()->PlaybackService.instance.player.isPlaying());
        ui(()->{assertSame(retryRoute,PlaybackService.instance.route);assertFalse(PlaybackService.instance.needsRetry());PlaybackService.instance.play(original,repo.results,null);});
        until(()->PlaybackService.instance.player.isPlaying());
        ui(repo::scanSources);until(()->!repo.scanningSources);
        ui(()->{assertTrue(repo.sources.stream().filter(s->s.name.contains("备用")).findFirst().get().playbackHealth.filtered());assertFalse(repo.sources.stream().filter(s->s.name.contains("主线路")).findFirst().get().hidden());});
        ui(()->repo.search("second"));until(()->!repo.searching);ui(()->repo.search("third"));until(()->!repo.searching);
        ui(()->assertTrue(repo.sources.stream().filter(s->s.name.contains("故障")).findFirst().get().hidden()));
        ui(()->repo.search("fourth"));until(()->!repo.searching);
        ui(()->{assertTrue(repo.searchStates.values().stream().anyMatch(s->s.contains("已过滤")));assertTrue(PlaybackService.instance.player.isPlaying());});
        MusicRepository restored=new MusicRepository(context);
        assertEquals(1,restored.favorites.size());assertEquals(4,restored.sources.size());
    }
}
