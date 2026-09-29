package app.qingting.music;

/** Playback intent and checkpoint survive asynchronous route attempts. */
public final class PlaybackRecovery {
    private volatile long serial;
    private long position;
    private boolean pending,wantsPlay,exhausted;
    public void reset(long position,boolean autoplay){serial++;this.position=Math.max(0,position);pending=false;exhausted=false;wantsPlay=autoplay;}
    public long beginAttempt(){pending=true;exhausted=false;return ++serial;}
    public boolean valid(long token){return token==serial;}
    public long token(){return serial;}
    public void capture(long position){if(!pending&&!exhausted)this.position=Math.max(0,position);}
    public void ready(){pending=false;}
    public void exhaust(){serial++;pending=false;exhausted=true;wantsPlay=false;}
    public void setWantsPlay(boolean value){wantsPlay=value;}
    public long position(){return position;}
    public boolean canResume(long duration){return duration<=0||position<duration;}
    public boolean pending(){return pending;}
    public boolean exhausted(){return exhausted;}
    public boolean wantsPlay(){return wantsPlay;}
}
