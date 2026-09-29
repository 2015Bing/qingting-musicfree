package app.qingting.music;
/** Queue serialization is independent from frequent position writes. */
public final class QueueSnapshot {
 public interface Encoder{String encode()throws Exception;}
 private boolean dirty=true;
 public void changed(){dirty=true;}
 public String take(Encoder encoder)throws Exception{if(!dirty)return null;String value=encoder.encode();dirty=false;return value;}
}
