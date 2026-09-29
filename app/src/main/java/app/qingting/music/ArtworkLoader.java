package app.qingting.music;
import android.content.Context;import android.graphics.*;import android.os.*;import android.util.LruCache;import java.io.File;import java.util.*;import java.util.concurrent.*;import java.util.function.Consumer;
/** Main-thread subscriptions; identical URLs share one cancellable worker request. */
public final class ArtworkLoader {
 private static ArtworkLoader instance;
 public static synchronized ArtworkLoader get(Context c){if(instance==null)instance=new ArtworkLoader(c.getApplicationContext());return instance;}
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ThreadPoolExecutor io=(ThreadPoolExecutor)Executors.newFixedThreadPool(2);
 private final LocalFileCache disk;
 private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(4*1024*1024){protected int sizeOf(String k,Bitmap v){return v.getByteCount();}};
 private final Map<String,Job> jobs=new HashMap<>();
 private static final class Job{volatile boolean cancelled;final Map<Object,Consumer<Bitmap>> listeners=new HashMap<>();Future<?> future;}
 private ArtworkLoader(Context c){disk=new LocalFileCache(new File(c.getCacheDir(),"artwork"),32L*1024*1024,7L*86400000);}
 public Bitmap cached(String url){return memory.get(url);}
 public Runnable load(String url,Consumer<Bitmap> listener){Bitmap hit=cached(url);if(hit!=null){listener.accept(hit);return ()->{};}Job existing=jobs.get(url);boolean start=existing==null;Job job=start?new Job():existing;Object token=new Object();job.listeners.put(token,listener);jobs.put(url,job);if(start)job.future=io.submit(()->{
  Bitmap image=null;try{if(job.cancelled)return;byte[] bytes=disk.get(url,System.currentTimeMillis());boolean downloaded=bytes==null;if(downloaded)bytes=Http.image(url,()->!job.cancelled);if(job.cancelled)return;BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);o.inSampleSize=1;while(o.outWidth/o.inSampleSize>512||o.outHeight/o.inSampleSize>512)o.inSampleSize*=2;o.inJustDecodeBounds=false;image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(image!=null&&!job.cancelled){memory.put(url,image);if(downloaded)disk.put(url,bytes,System.currentTimeMillis());}}catch(Exception ignored){}finally{Bitmap result=image;main.post(()->{if(jobs.get(url)!=job)return;jobs.remove(url);if(!job.cancelled)for(Consumer<Bitmap> callback:new ArrayList<>(job.listeners.values()))callback.accept(result);job.listeners.clear();});}
 });return ()->{job.listeners.remove(token);if(job.listeners.isEmpty()&&jobs.get(url)==job){jobs.remove(url);job.cancelled=true;if(job.future!=null){job.future.cancel(true);if(job.future instanceof Runnable)io.remove((Runnable)job.future);}}};}
}
