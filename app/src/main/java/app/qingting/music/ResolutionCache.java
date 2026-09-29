package app.qingting.music;
import java.util.*;
public final class ResolutionCache {
 private static final class Entry {Models.Media media;long until;Entry(Models.Media m,long t){media=m;until=t;}}
 private final int limit;private final long ttl;private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(24,.75f,true);
 public ResolutionCache(int limit,long ttl){this.limit=limit;this.ttl=ttl;}
 public synchronized Models.Media get(String key,long now){Entry e=entries.get(key);if(e==null)return null;if(now>=e.until){entries.remove(key);return null;}return e.media;}
 public synchronized void put(String key,Models.Media media,long now){entries.put(key,new Entry(media,media.expiresAt>0?Math.min(now+ttl,media.expiresAt):now+ttl));while(entries.size()>limit)entries.remove(entries.keySet().iterator().next());}
 public synchronized void remove(String key){entries.remove(key);}
}
