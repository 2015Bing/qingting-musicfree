package app.qingting.music;
public final class SleepTimer {
 public long deadline;public boolean endOfTrack;
 public void minutes(int minutes,long now){deadline=minutes>0?now+minutes*60000L:0;endOfTrack=false;}
 public void afterTrack(){deadline=0;endOfTrack=true;}
 public void clear(){deadline=0;endOfTrack=false;}
 public void cancelTrack(){endOfTrack=false;}
 public boolean expired(long now){if(deadline==0||now<deadline)return false;clear();return true;}
 public boolean consumeTrackEnd(){if(!endOfTrack)return false;clear();return true;}
}
