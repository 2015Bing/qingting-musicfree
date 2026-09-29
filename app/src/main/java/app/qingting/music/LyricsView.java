package app.qingting.music;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.*;
import java.util.*;

/** Time-driven lyric view; manual browsing never blocks highlight updates or seeking. */
public final class LyricsView extends ScrollView {
    public interface SeekListener {void seek(Lyrics document,int line);}
    private final LinearLayout rows;private final List<TextView> labels=new ArrayList<>();private final LyricFollow follow=new LyricFollow();
    private Lyrics document;private String message="";private int active=-1;private boolean wasFollowing=true,forceCenter;private SeekListener seek;
    public LyricsView(Context context){super(context);rows=new LinearLayout(context);rows.setOrientation(LinearLayout.VERTICAL);addView(rows);setFillViewport(true);setSmoothScrollingEnabled(true);setVerticalScrollBarEnabled(false);setVerticalFadingEdgeEnabled(true);setFadingEdgeLength((int)(56*getResources().getDisplayMetrics().density));setOverScrollMode(OVER_SCROLL_NEVER);rows.setClipChildren(false);}
    @Override public int getSolidColor(){return Color.rgb(247,248,244);}
    public void setSeekListener(SeekListener listener){seek=listener;}
    public void resumeFollowing(){follow.resume();forceCenter=true;}
    public boolean isFollowing(){return follow.following(SystemClock.elapsedRealtime());}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);rows.setPadding(0,h/2,0,h/2);forceCenter=true;}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){follow.touch(SystemClock.elapsedRealtime());wasFollowing=false;}
        else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)follow.release(SystemClock.elapsedRealtime());
        return super.dispatchTouchEvent(event);
    }
    public void update(Lyrics lyrics,String status,long position,int delay){
        if(document!=lyrics||!message.equals(status)){
            document=lyrics;message=status;active=-1;rows.removeAllViews();labels.clear();resumeFollowing();
            if(lyrics.lines.isEmpty())add(status,-1,lyrics);
            else for(int i=0;i<lyrics.lines.size();i++){Lyrics.Line line=lyrics.lines.get(i);add(line.text+(line.translation.isEmpty()?"":"\n"+line.translation),i,lyrics);}
            scrollTo(0,0);
        }
        int next=lyrics.indexAt(position,delay);boolean changed=next!=active;
        if(changed){if(active>=0)style(labels.get(active),false);active=next;if(active>=0)style(labels.get(active),true);}
        boolean following=isFollowing();
        if(following&&(changed||!wasFollowing||forceCenter)){
            forceCenter=false;
            if(active>=0){TextView selected=labels.get(active);int index=active;post(()->{if(document==lyrics&&active==index&&isFollowing())smoothScrollTo(0,Math.max(0,selected.getTop()+selected.getHeight()/2-getHeight()/2));});}
            else if(lyrics.timed)smoothScrollTo(0,0);
        }
        wasFollowing=following;
    }
    private void add(String value,int index,Lyrics lyrics){
        TextView label=new TextView(getContext());label.setText(value);label.setTextSize(22);label.setLineSpacing(4*getResources().getDisplayMetrics().density,1);label.setGravity(android.view.Gravity.CENTER);int pad=(int)(18*getResources().getDisplayMetrics().density);label.setPadding(pad/2,pad,pad/2,pad);style(label,false);rows.addView(label);labels.add(label);label.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(document==lyrics&&active==index&&(t!=ot||b!=ob))forceCenter=true;});
        if(lyrics.timed&&index>=0){label.setContentDescription("播放此句："+value);label.setOnClickListener(v->{if(document!=lyrics)return;resumeFollowing();if(seek!=null)seek.seek(lyrics,index);});}
    }
    private void style(TextView label,boolean selected){label.setTextColor(selected?Color.rgb(36,94,79):Color.rgb(111,123,114));label.setTypeface(Typeface.DEFAULT,selected?Typeface.BOLD:Typeface.NORMAL);float scale=selected?1.08f:1,alpha=selected?1:.8f;label.animate().cancel();if(label.isLaidOut())label.animate().scaleX(scale).scaleY(scale).alpha(alpha).setDuration(180).start();else{label.setScaleX(scale);label.setScaleY(scale);label.setAlpha(alpha);}}
}
