package app.qingting.music;
import org.junit.Test;
import static org.junit.Assert.*;
public class LyricFollowTest {
    @Test public void holdingStopsFollowAndReleaseResumesWithoutLineChange(){LyricFollow follow=new LyricFollow();assertTrue(follow.following(0));follow.touch(0);assertFalse(follow.following(9000));follow.release(9000);assertFalse(follow.following(11999));assertTrue(follow.following(12000));}
    @Test public void explicitResumeCancelsManualBrowsing(){LyricFollow follow=new LyricFollow();follow.touch(100);follow.release(200);follow.resume();assertTrue(follow.following(201));}
}
