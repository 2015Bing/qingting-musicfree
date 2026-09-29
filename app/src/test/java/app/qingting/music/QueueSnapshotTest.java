package app.qingting.music;
import org.junit.Test;import static org.junit.Assert.*;
public class QueueSnapshotTest {
 @Test public void progressTicksNeverSerializeUnchangedQueue()throws Exception{QueueSnapshot state=new QueueSnapshot();int[] calls={0};assertEquals("queue",state.take(()->{calls[0]++;return "queue";}));for(int i=0;i<100;i++)assertNull(state.take(()->{calls[0]++;return "queue";}));assertEquals(1,calls[0]);state.changed();assertEquals("new",state.take(()->"new"));}
 @Test public void serializationFailureCanBeRetried()throws Exception{QueueSnapshot state=new QueueSnapshot();assertThrows(Exception.class,()->state.take(()->{throw new Exception("failed");}));assertEquals("retry",state.take(()->"retry"));}
}
