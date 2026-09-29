package app.qingting.music;

import org.junit.Test;
import org.json.JSONObject;
import java.util.*;
import static org.junit.Assert.*;

public class SongPolicyTest {
    private Models.Song song(String title,String artist,String album) throws Exception {
        return new Models.Song(new Models.Route("a","源",new JSONObject().put("title",title).put("artist",artist).put("album",album)));
    }
    @Test public void metadataCleanedWithoutChangingRaw() throws Exception {
        Models.Song screenshot=song("无言感激&nbsp; (cover:&nbsp;譚詠麟)","永胜","永胜选集2");
        assertEquals("无言感激 (cover: 譚詠麟)",screenshot.title);assertEquals("翻唱",SongPolicy.versionLabel(screenshot));
        assertEquals("无言感激&nbsp; (cover:&nbsp;譚詠麟)",screenshot.routes.get(0).raw.getString("title"));
        Models.Song s=song("<em>晴天</em>&nbsp;", "周杰伦&amp;nbsp;", "叶惠美");
        assertEquals("晴天",s.title);assertEquals("周杰伦",s.artist);
        assertEquals("<em>晴天</em>&nbsp;",s.routes.get(0).raw.getString("title"));
    }
    @Test public void strictIdentityRetainsVersionsAndAlbum() throws Exception {
        Models.Song s=song("晴天","周杰伦","叶惠美");
        assertTrue(SongPolicy.sameRecording(s,song(" 晴天 ","周杰伦","叶惠美")));
        assertFalse(SongPolicy.sameRecording(s,song("晴天 (Live)","周杰伦","叶惠美")));
        assertFalse(SongPolicy.sameRecording(s,song("晴天 (cover 周杰伦)","其他人","叶惠美")));
        assertFalse(SongPolicy.sameRecording(s,song("晴天","其他人","叶惠美")));
        assertFalse(SongPolicy.sameRecording(s,song("晴天","周杰伦","演唱会")));
        assertFalse(SongPolicy.sameRecording(song("晴天","",""),song("晴天","","")));
        assertFalse(SongPolicy.sameRecording(song("晴天","未知歌手",""),song("晴天","未知歌手","")));
    }
    @Test public void relevanceDoesNotDependOnVerification() throws Exception {
        Models.Song exact=song("晴天","周杰伦","");Models.Song loose=song("晴天 (Live)","周杰伦","");
        loose.routes.get(0).verifiedPlayable=true;
        assertTrue(SongPolicy.relevance(exact,"晴天 周杰伦")<SongPolicy.relevance(loose,"晴天 周杰伦"));
        assertEquals("",SongPolicy.versionLabel(exact));assertEquals("Live",SongPolicy.versionLabel(loose));
        assertEquals("",SongPolicy.versionLabel(song("Live Forever","Oasis","")));
        assertEquals("翻唱",SongPolicy.versionLabel(song("晴天 (cover 周杰伦)","甲","")));
    }
    @Test public void freshnessAndProbeScheduling() throws Exception {
        long now=1_000_000;Models.Song s=song("晴天","周杰伦","");Models.Route a=s.routes.get(0);
        Set<Models.Route> attempted=new HashSet<>();assertSame(a,SongPolicy.nextProbe(s,r->true,attempted,now));
        a.verifying=true;assertNull(SongPolicy.nextProbe(s,r->true,attempted,now));a.verifying=false;
        a.verifiedPlayable=true;a.verifiedAt=now;assertEquals(1,SongPolicy.playableCount(s,now));
        assertNull(SongPolicy.nextProbe(s,r->true,attempted,now));
        a.verifiedAt=now-300001;assertFalse(SongPolicy.recentlyPlayable(a,now));
        assertSame(a,SongPolicy.nextProbe(s,r->true,attempted,now));
        a.playbackActive=true;assertFalse(SongPolicy.recentlyPlayable(a,now));a.status="正在播放";assertTrue(SongPolicy.recentlyPlayable(a,now));a.error="failed";
        assertFalse(SongPolicy.recentlyPlayable(a,now));a.playbackActive=false;
        assertNull(SongPolicy.nextProbe(s,r->true,attempted,now));a.error="";attempted.add(a);
        assertNull(SongPolicy.nextProbe(s,r->true,attempted,now));
        Models.Route b=new Models.Route("b","源二",new JSONObject());s.routes.add(b);
        assertSame(b,SongPolicy.nextProbe(s,r->true,attempted,now));
        assertNull(SongPolicy.nextProbe(s,r->false,attempted,now));
    }
    @Test public void matchingDurationAllowsDifferentAlbumButNotVersion()throws Exception{
        Models.Song a=song("晴天","周杰伦","原专辑"),b=song("晴天","周杰伦","精选");a.routes.get(0).raw.put("duration",269);b.routes.get(0).raw.put("duration",270);assertTrue(SongPolicy.sameRecording(a,b));b.routes.get(0).raw.put("duration",300);assertFalse(SongPolicy.sameRecording(a,b));
    }
}
