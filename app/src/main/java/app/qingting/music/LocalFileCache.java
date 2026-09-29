package app.qingting.music;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;
/** Bounded disposable files. All filesystem work belongs on a worker. */
public final class LocalFileCache {
 private final File directory;private final long limit,ttl;
 public LocalFileCache(File directory,long limit,long ttl){this.directory=directory;this.limit=limit;this.ttl=ttl;}
 private File file(String key)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));StringBuilder name=new StringBuilder();for(byte b:digest)name.append(String.format(Locale.ROOT,"%02x",b&255));return new File(directory,name+".cache");}
 public synchronized byte[] get(String key,long now){try{File f=file(key);if(!f.isFile())return null;long at=f.lastModified();if(now<at||now-at>ttl||f.length()>limit){f.delete();return null;}return Files.readAllBytes(f.toPath());}catch(Exception e){return null;}}
 public synchronized void put(String key,byte[] data,long now){if(data.length>limit)return;File temp=null;try{if(!directory.isDirectory()&&!directory.mkdirs())return;File target=file(key);temp=File.createTempFile("write-",".tmp",directory);Files.write(temp.toPath(),data);Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);target.setLastModified(now);File[] files=directory.listFiles((d,n)->n.endsWith(".cache"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long size=0;for(File f:files)size+=f.length();for(File f:files){if(size<=limit)break;long bytes=f.length();if(f.delete())size-=bytes;}}catch(Exception ignored){}finally{if(temp!=null)temp.delete();}}
}
