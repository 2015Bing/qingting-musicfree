package app.qingting.music;
import org.junit.Test;import org.json.*;import static org.junit.Assert.*;
public class ResolutionCacheTest {
 @Test public void expiresAndBounded()throws Exception{ResolutionCache c=new ResolutionCache(2,100);Models.Media m=new Models.Media(new JSONObject().put("url","https://example.org/a"));c.put("a",m,10);assertNotNull(c.get("a",109));assertNull(c.get("a",110));c.put("a",m,120);c.put("b",m,120);c.put("c",m,120);assertNull(c.get("a",121));}
 @Test public void explicitExpiryWins()throws Exception{ResolutionCache c=new ResolutionCache(2,100);Models.Media m=new Models.Media(new JSONObject().put("url","https://example.org/a"));m.expiresAt=50;c.put("a",m,10);assertNull(c.get("a",50));}
}
