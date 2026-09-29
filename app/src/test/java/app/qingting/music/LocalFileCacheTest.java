package app.qingting.music;
import java.io.*;import java.nio.file.*;import org.junit.Test;import static org.junit.Assert.*;
public class LocalFileCacheTest {
 @Test public void survivesRestartExpiresAndBoundsDiskUsage()throws Exception{
  File dir=Files.createTempDirectory("qingting-cache").toFile();LocalFileCache cache=new LocalFileCache(dir,6,100);
  cache.put("a",new byte[]{1,2,3,4},1000);assertArrayEquals(new byte[]{1,2,3,4},new LocalFileCache(dir,6,100).get("a",1050));
  cache.put("b",new byte[]{5,6,7,8},1060);assertNull(cache.get("a",1060));assertNotNull(cache.get("b",1100));assertNull(cache.get("b",1161));
 }
 @Test public void rollbackOversizeAndBrokenEntriesAreMisses()throws Exception{
  File dir=Files.createTempDirectory("qingting-cache").toFile();LocalFileCache cache=new LocalFileCache(dir,4,100);
  cache.put("a",new byte[]{1},1000);assertNull(cache.get("a",999));cache.put("big",new byte[5],1000);assertNull(cache.get("big",1001));
 }
}
