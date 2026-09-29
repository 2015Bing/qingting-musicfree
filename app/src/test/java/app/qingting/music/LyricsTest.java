package app.qingting.music;
import org.junit.Test;
import static org.junit.Assert.*;

public class LyricsTest {
    @Test public void delayIsSharedByHighlightAndSeeking(){
        Lyrics lyrics=Lyrics.parse("[00:00]start[00:01]one[00:05]five","");
        assertEquals(-1,lyrics.indexAt(200,500));assertEquals(1,lyrics.indexAt(1500,500));assertEquals(1500,lyrics.seekPosition(1,500,10000));
        assertEquals(1,lyrics.indexAt(500,-500));assertEquals(0,lyrics.seekPosition(0,-500,10000));assertEquals(5200,lyrics.seekPosition(2,500,5200));
        assertEquals(-1,Lyrics.parse("plain","").seekPosition(0,0,100));
    }
    @Test public void compactLrcKeepsSeparateTextAndSharedTags(){
        Lyrics lyrics=Lyrics.parse("[00:01.00]first[00:02.00][00:03.00]second","");
        assertEquals(3,lyrics.lines.size());assertEquals("first",lyrics.lines.get(0).text);assertEquals("second",lyrics.lines.get(1).text);assertEquals("second",lyrics.lines.get(2).text);
    }
    @Test public void parsesMultipleTimestampsFractionsAndTranslation(){
        Lyrics l=Lyrics.parse("[ti:Test]\n[00:01.2][00:03.450]第一句\n[00:08]第二句","[00:01.20]First\n[00:03.45]Again");
        assertEquals(3,l.lines.size());assertEquals(1200,l.lines.get(0).timeMs);assertEquals(3450,l.lines.get(1).timeMs);assertEquals("First",l.lines.get(0).translation);
        assertEquals(-1,l.indexAt(1199));assertEquals(0,l.indexAt(1200));assertEquals(1,l.indexAt(7000));
    }
    @Test public void offsetAppliesAcrossWholeDocumentAndSeekGoesBackward(){
        Lyrics l=Lyrics.parse("[00:03.00]later\n[offset:-500]\n[00:01.00]first","");
        assertEquals(500,l.lines.get(0).timeMs);assertEquals(1,l.indexAt(4000));assertEquals(0,l.indexAt(500));
    }
    @Test public void supportsPlainTextAndEmptyProviderResults(){
        Lyrics l=Lyrics.parse("第一行\n第二行","");assertEquals(2,l.lines.size());assertFalse(l.timed);assertEquals(-1,l.indexAt(999));
        assertTrue(Lyrics.parse("[ti:Only metadata]","").lines.isEmpty());assertTrue(Lyrics.parse(null,null).lines.isEmpty());
    }
    @Test public void mergesEqualTimestampsAndRejectsMalformedTime(){
        Lyrics l=Lyrics.parse("[00:01.00]a\n[00:01.00]b\n[00:99.00]bad","");assertEquals(1,l.lines.size());assertEquals("a\nb",l.lines.get(0).text);
    }
}
