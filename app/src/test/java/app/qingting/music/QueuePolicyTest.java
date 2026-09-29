package app.qingting.music;
import org.junit.Test;import static org.junit.Assert.*;
public class QueuePolicyTest {
 @Test public void boundariesAndRepeat(){assertEquals(-1,QueuePolicy.nextIndex(0,0,1,0,true));assertEquals(-1,QueuePolicy.nextIndex(3,2,1,0,true));assertEquals(0,QueuePolicy.nextIndex(3,2,1,1,true));assertEquals(2,QueuePolicy.nextIndex(3,2,1,2,true));assertEquals(0,QueuePolicy.nextIndex(3,2,1,2,false));assertEquals(2,QueuePolicy.nextIndex(3,0,-1,1,false));}
 @Test public void timerExpiresOnceAndTrackStopWins(){SleepTimer t=new SleepTimer();t.minutes(15,1000);assertFalse(t.expired(900999));assertTrue(t.expired(901000));assertFalse(t.expired(901001));t.afterTrack();assertTrue(t.consumeTrackEnd());assertFalse(t.consumeTrackEnd());t.afterTrack();t.cancelTrack();assertFalse(t.consumeTrackEnd());}
 @Test public void moveExistingQueueSongKeepsPinnedSource()throws Exception{Models.Song pinned=LibraryStateTest.song("B","one");pinned=pinned.snapshot(pinned.routes.get(0));Models.Song incoming=LibraryStateTest.song("B","two");java.util.List<Models.Song> q=new java.util.ArrayList<>();q.add(LibraryStateTest.song("A","one"));q.add(pinned);QueuePolicy.enqueue(q,incoming,q.get(0).key,true);assertEquals(2,q.size());assertEquals("one",q.get(1).fixedRoute().sourceId);}
 @Test public void timerAtEndResumesFromBeginning(){assertEquals(0,QueuePolicy.resumePosition(90000,true));assertEquals(42000,QueuePolicy.resumePosition(42000,false));}
}
