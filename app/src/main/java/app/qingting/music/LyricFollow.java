package app.qingting.music;
/** Manual browsing and automatic follow share an explicit clock for predictable resumption. */
public final class LyricFollow {
    private boolean touching;private long resumeAt;
    public void touch(long now){touching=true;}
    public void release(long now){touching=false;resumeAt=now+3000;}
    public void resume(){touching=false;resumeAt=0;}
    public boolean following(long now){return !touching&&now>=resumeAt;}
}
