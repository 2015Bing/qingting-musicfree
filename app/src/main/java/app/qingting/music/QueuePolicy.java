package app.qingting.music;
public final class QueuePolicy {
 public static void enqueue(java.util.List<Models.Song> queue,Models.Song incoming,String currentKey,boolean next){Models.Song entry=incoming.snapshot(null);for(Models.Song song:queue)if(song.key.equals(incoming.key)){entry=song;break;}queue.removeIf(song->song.key.equals(incoming.key));int at=-1;for(int i=0;i<queue.size();i++)if(queue.get(i).key.equals(currentKey))at=i;queue.add(next&&at>=0?at+1:queue.size(),entry);}
 public static long resumePosition(long position,boolean ended){return ended?0:Math.max(0,position);}
 public static final String[] MODES={"顺序播放","列表循环","单曲循环"};
 public static int nextIndex(int size,int index,int delta,int mode,boolean natural){if(size==0)return -1;if(natural&&mode==2)return index;int n=index+delta;if(mode!=0)return Math.floorMod(n,size);return n>=0&&n<size?n:-1;}
}
