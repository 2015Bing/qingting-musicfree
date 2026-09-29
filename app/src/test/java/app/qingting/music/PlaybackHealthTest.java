package app.qingting.music;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackHealthTest {
    @Test public void repeatedFailureOfOneSongNeverFiltersChannel(){
        PlaybackHealth h=new PlaybackHealth();for(int i=0;i<10;i++)h.record("same",false,true);
        assertFalse(h.filtered());
    }
    @Test public void threeDistinctFailuresFilterAndSuccessRestores(){
        PlaybackHealth h=new PlaybackHealth();h.record("a",false,true);h.record("b",false,true);assertFalse(h.filtered());
        h.record("c",false,true);assertTrue(h.filtered());h.record("d",true,true);assertFalse(h.filtered());
    }
    @Test public void offlineOrEmptyEvidenceNeverFilters(){
        PlaybackHealth h=new PlaybackHealth();h.record("a",false,false);h.record("b",false,false);h.record("c",false,false);h.record("",false,true);assertFalse(h.filtered());
    }
    @Test public void stateRoundTripsAndManualRestoreClearsEvidence() throws Exception {
        PlaybackHealth h=new PlaybackHealth();h.record("a",false,true);h.record("b",false,true);h.record("c",false,true);
        PlaybackHealth restored=PlaybackHealth.from(h.json());assertTrue(restored.filtered());restored.restore();assertFalse(restored.filtered());restored.record("a",false,true);assertFalse(restored.filtered());
    }
}
