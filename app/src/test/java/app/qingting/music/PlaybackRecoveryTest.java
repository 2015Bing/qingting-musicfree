package app.qingting.music;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackRecoveryTest {
    @Test public void shorterAlternativeCannotResumeBeyondItsEnd(){
        PlaybackRecovery r=new PlaybackRecovery();r.reset(42000,false);r.beginAttempt();
        assertFalse(r.canResume(30000));assertFalse(r.canResume(42000));assertTrue(r.canResume(90000));assertTrue(r.canResume(-1));
        assertFalse(r.wantsPlay());
    }
    @Test public void failureDuringPlaybackKeepsPositionAcrossFailedCandidates(){
        PlaybackRecovery r=new PlaybackRecovery();r.reset(0,true);r.beginAttempt();r.ready();
        r.capture(42000);r.beginAttempt();r.capture(0);r.beginAttempt();
        assertEquals(42000,r.position());assertTrue(r.pending());
    }
    @Test public void pauseDuringResolutionSurvivesCompletion(){
        PlaybackRecovery r=new PlaybackRecovery();r.reset(0,true);r.beginAttempt();r.setWantsPlay(false);r.ready();
        assertFalse(r.wantsPlay());assertFalse(r.pending());
    }
    @Test public void nextAttemptRejectsOldCallback(){
        PlaybackRecovery r=new PlaybackRecovery();long old=r.beginAttempt();long current=r.beginAttempt();
        assertFalse(r.valid(old));assertTrue(r.valid(current));
    }
    @Test public void newSongAndExhaustionInvalidateRequests(){
        PlaybackRecovery r=new PlaybackRecovery();long old=r.beginAttempt();r.reset(0,true);assertFalse(r.valid(old));
        long current=r.beginAttempt();r.exhaust();assertFalse(r.valid(current));assertTrue(r.exhausted());assertFalse(r.wantsPlay());
    }
    @Test public void retryKeepsCheckpointButNewSongStartsAtZero(){
        PlaybackRecovery r=new PlaybackRecovery();r.reset(61000,false);r.beginAttempt();r.exhaust();
        r.reset(r.position(),true);assertEquals(61000,r.position());assertTrue(r.wantsPlay());assertFalse(r.exhausted());
        r.reset(0,true);assertEquals(0,r.position());
    }
    @Test public void readyRouteCanCaptureLaterSeekEvenBackwards(){
        PlaybackRecovery r=new PlaybackRecovery();r.reset(42000,true);r.beginAttempt();r.ready();r.capture(5000);
        assertEquals(5000,r.position());
    }
}
