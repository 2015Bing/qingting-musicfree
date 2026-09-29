package app.qingting.music;
/** Axis-locked reveal gesture; deleting always requires a separate button tap. */
public final class SwipeGesture {
    private final float width,slop;private float x,y,original;private boolean vertical;
    public boolean dragging;public float offset;
    public SwipeGesture(float width,float slop){this.width=width;this.slop=slop;}
    public void begin(float x,float y,float original){this.x=x;this.y=y;this.original=original;offset=original;dragging=false;vertical=false;}
    public boolean move(float x,float y){float dx=x-this.x,dy=y-this.y;if(vertical)return false;if(!dragging){if(Math.abs(dy)>slop&&Math.abs(dy)>=Math.abs(dx)){vertical=true;return false;}if(Math.abs(dx)<=slop||Math.abs(dx)<=Math.abs(dy)*1.4f)return false;if(original==0&&dx>0)return false;dragging=true;}offset=Math.max(-width,Math.min(0,original+dx));return true;}
    public float finish(boolean canceled){if(canceled)return original;return offset<-width*.4f?-width:0;}
}
