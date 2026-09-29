package app.qingting.music;
import android.app.*;import android.view.*;import android.widget.*;import android.graphics.Color;import java.util.*;import static app.qingting.music.Models.*;
/** Shared interactions; playlist addition intentionally lives only in LibraryUi. */
@androidx.media3.common.util.UnstableApi
public final class PlaybackUi {
 static void toast(Activity a,String s){Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
 public static void favorite(Activity a,MusicRepository repo,Song song){if(repo.liked(song)){repo.favorite(song,null);return;}PlaybackService p=PlaybackService.instance;Route r=song.fixedRoute();if(p!=null&&p.current!=null&&p.current.key.equals(song.key)&&p.route!=null&&p.player.isPlaying())r=p.route;if(r==null&&song.routes.size()==1)r=song.routes.get(0);if(r!=null){repo.favorite(song,r);return;}chooseRoute(a,repo,song,selected->repo.favorite(song,selected));}
 public interface RouteAction {void run(Route route);}
 public static void chooseRoute(Activity a,MusicRepository repo,Song song,RouteAction action){String[] names=new String[song.routes.size()];for(int i=0;i<names.length;i++)names[i]=song.routes.get(i).sourceName;new UiSheet.Builder(a).setTitle("选择固定播放源").setSingleChoiceItems(names,-1,(d,w)->action.run(song.routes.get(w))).setNegativeButton("取消",null).show();}
 private static long playRequest;
 public static void play(Activity a,MusicRepository repo,Song song,List<Song> songs){long request=++playRequest;PlaybackService p=PlaybackService.instance;if(p==null){toast(a,"播放器正在准备，请稍后重试");return;}if(song.sourceChoiceRequired){chooseRoute(a,repo,song,r->{Song fixed=song.snapshot(r),previous=p.current;List<Song> copy=new ArrayList<>(songs);for(int i=0;i<copy.size();i++)if(copy.get(i).key.equals(song.key))copy.set(i,fixed);repo.pinFavorite(song,r,(ok,error)->{if(!ok||request!=playRequest||PlaybackService.instance!=p||p.current!=previous||a.isDestroyed())return;p.play(fixed,copy,r);});});return;}p.play(song,songs,null);}
 public static void timer(Activity a){PlaybackService p=PlaybackService.instance;if(p==null)return;new UiSheet.Builder(a).setTitle(p.timerLabel()).setItems(new String[]{"15 分钟后停止","30 分钟后停止","60 分钟后停止","播完当前歌曲停止","关闭定时"},(d,w)->{if(w==3)p.afterTrack();else p.setSleepMinutes(w==0?15:w==1?30:w==2?60:0);}).setNegativeButton("取消",null).show();}
 public static void queue(Activity a){PlaybackService p=PlaybackService.instance;if(p!=null)new QueueSheet(a,p,((MusicApp)a.getApplication()).repository).show();}
}
