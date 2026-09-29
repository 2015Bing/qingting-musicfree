package app.qingting.music;
import org.junit.*;
import org.json.*;
import okhttp3.mockwebserver.*;
import okio.Buffer;
import static org.junit.Assert.*;

public class HttpTest {
    @Test public void detachedImageCancelsSlowDownload()throws Exception{
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));java.util.concurrent.atomic.AtomicBoolean valid=new java.util.concurrent.atomic.AtomicBoolean(true);java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);String url=server.url("/image").toString();Thread worker=new Thread(()->{try{Http.image(url,valid::get);}catch(Exception expected){}finally{done.countDown();}});worker.start();assertNotNull(server.takeRequest(2,java.util.concurrent.TimeUnit.SECONDS));valid.set(false);assertTrue(done.await(2,java.util.concurrent.TimeUnit.SECONDS));
    }
    private MockWebServer server;
    @Before public void start() throws Exception {server=new MockWebServer();server.start();}
    @After public void stop() throws Exception {server.shutdown();}
    @Test public void probeUsesBoundedGetAndRejectsHtml() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type","text/html").setBody("<html>login required</html>"));
        Models.Media media=new Models.Media(new JSONObject().put("url",server.url("/music").toString()));
        assertThrows(java.io.IOException.class,()->Http.probe(media));
        RecordedRequest request=server.takeRequest();assertEquals("GET",request.getMethod());assertEquals("bytes=0-2047",request.getHeader("Range"));
    }
    @Test public void audioProbeRetainsRequiredHeaders() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type","audio/wav").setBody("RIFFtestdata"));
        Models.Media media=new Models.Media(new JSONObject().put("url",server.url("/music").toString()).put("headers",new JSONObject().put("Referer","https://required.example/")));
        Http.probe(media);assertEquals("https://required.example/",server.takeRequest().getHeader("Referer"));
    }
    @Test public void nativeBridgePreservesBinaryBytes() throws Exception {
        server.enqueue(new MockResponse().setBody(new Buffer().write(new byte[]{(byte)255,0,(byte)128})));
        JSONObject result=Http.bridge(new JSONObject().put("url",server.url("/binary").toString()).put("responseType","arraybuffer"));
        assertEquals("base64",result.getString("dataEncoding"));assertEquals("/wCA",result.getString("data"));
    }
    @Test public void mediaPreservesEmbeddedLyricsAndHlsMime() throws Exception {
        String lrc="[00:01.00]内嵌歌词";
        Models.Media media=new Models.Media(new JSONObject().put("url",server.url("/stream").toString()).put("rawLrc",lrc).put("translation","[00:01]translation"));
        assertEquals(lrc,media.rawLrc);assertEquals("[00:01]translation",media.translation);
        server.enqueue(new MockResponse().setHeader("Content-Type","application/octet-stream").setBody("#EXTM3U\n#EXT-X-VERSION:3\n"));
        Http.probe(media);assertEquals("application/x-mpegURL",media.mimeType);
    }
    @Test public void lowRetryDoesNotLockFutureValidationToUnsupportedQuality(){
        Models.Route route=new Models.Route("id","name",new JSONObject());route.lowOnly=true;
        assertArrayEquals(new String[]{"low"},route.qualities(false));
        assertArrayEquals(new String[]{"standard","low"},route.qualities(true));
        route.lowOnly=false;route.quality="low";
        assertArrayEquals(new String[]{"low","standard"},route.qualities(false));
    }
    @Test public void sourceAndFavoriteRoundTripRetainsPluginFields() throws Exception {
        Models.Source s=new Models.Source("https://example.test/plugin.js");s.script="module.exports={}";s.variables.put("account","value");
        Models.Source restored=Models.Source.from(new JSONObject(s.json().toString()));assertEquals(s.id,restored.id);assertEquals("value",restored.variables.getString("account"));
        Models.Song song=new Models.Song(new Models.Route(s,new JSONObject().put("id",1).put("title","Song").put("artist","Artist").put("extraToken","retain")));
        Models.Song copy=Models.Song.from(new JSONObject(song.json().toString()));assertEquals("retain",copy.routes.get(0).raw.getString("extraToken"));
    }
    @Test public void cancelProbeReleasesSlowNetwork()throws Exception{
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));java.util.concurrent.atomic.AtomicBoolean valid=new java.util.concurrent.atomic.AtomicBoolean(true);java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);Models.Media media=new Models.Media(new JSONObject().put("url",server.url("/slow").toString()));Thread t=new Thread(()->{try{Http.probe(media,valid::get);}catch(Exception expected){}finally{done.countDown();}});t.start();assertNotNull(server.takeRequest(2,java.util.concurrent.TimeUnit.SECONDS));valid.set(false);assertTrue(done.await(2,java.util.concurrent.TimeUnit.SECONDS));
    }
    @Test public void bridgeCancelUsesExactOwner()throws Exception{
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));Object owner=new Object();java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);JSONObject request=new JSONObject().put("url",server.url("/slow").toString());Thread t=new Thread(()->{try{Http.bridge(request,owner);}catch(Exception expected){}finally{done.countDown();}});t.start();assertNotNull(server.takeRequest(2,java.util.concurrent.TimeUnit.SECONDS));Http.cancel(owner);assertTrue(done.await(2,java.util.concurrent.TimeUnit.SECONDS));
    }
}
