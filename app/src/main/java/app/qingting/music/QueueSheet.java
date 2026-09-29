package app.qingting.music;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import app.qingting.music.Models.Song;

/** A compact, live queue. Uses the existing playback service without owning playback. */
@androidx.media3.common.util.UnstableApi
public final class QueueSheet extends Dialog implements MusicRepository.Listener {
    private final Activity activity;
    private final PlaybackService player;
    private final MusicRepository repository;
    private final int ink=0xff25332c,muted=0xff879087,green=0xff245e4f;
    private final List<Song> songs=new ArrayList<>();
    private LinearLayout surface;
    private TextView count,clear;
    private ListView list;
    private QueueAdapter adapter;
    private boolean listening;
    private float dragStart;private SwipeRow openRow;
    private final android.app.Application.ActivityLifecycleCallbacks lifecycle=new android.app.Application.ActivityLifecycleCallbacks(){
        public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}
        public void onActivityDestroyed(Activity a){if(a==activity)dismiss();}
    };

    public QueueSheet(Activity activity,PlaybackService player,MusicRepository repository){
        super(activity);this.activity=activity;this.player=player;this.repository=repository;
    }
    private int dp(float value){return (int)(value*getContext().getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color){TextView view=new TextView(getContext());view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private LinearLayout row(){LinearLayout view=new LinearLayout(getContext());view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private TextView action(String label,String description,Runnable run){TextView view=text(label,14,muted);view.setGravity(Gravity.CENTER);view.setPadding(dp(12),0,dp(12),0);view.setMinWidth(dp(48));view.setMinHeight(dp(48));view.setContentDescription(description);view.setBackground(selectable());view.setOnClickListener(v->run.run());return view;}
    private android.graphics.drawable.Drawable selectable(){android.util.TypedValue value=new android.util.TypedValue();getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground,value,true);return getContext().getDrawable(value.resourceId);}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);setCanceledOnTouchOutside(true);
        surface=new LinearLayout(getContext());surface.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background=new GradientDrawable();background.setColor(0xfffcfdfb);float radius=dp(22);background.setCornerRadii(new float[]{radius,radius,radius,radius,0,0,0,0});surface.setBackground(background);surface.setClipToOutline(true);
        FrameLayout grab=new FrameLayout(getContext());View handle=new View(getContext());GradientDrawable pill=new GradientDrawable();pill.setColor(0xffd4d9d3);pill.setCornerRadius(dp(2));handle.setBackground(pill);FrameLayout.LayoutParams handleParams=new FrameLayout.LayoutParams(dp(32),dp(4),Gravity.CENTER);grab.addView(handle,handleParams);surface.addView(grab,new LinearLayout.LayoutParams(-1,dp(20)));grab.setContentDescription("下拉关闭播放列表");grab.setOnTouchListener((v,event)->{switch(event.getActionMasked()){case MotionEvent.ACTION_DOWN:dragStart=event.getRawY();return true;case MotionEvent.ACTION_MOVE:surface.setTranslationY(Math.max(0,event.getRawY()-dragStart));return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:if(event.getActionMasked()==MotionEvent.ACTION_UP&&surface.getTranslationY()>dp(70))dismiss();else surface.animate().translationY(0).setDuration(150).start();v.performClick();return true;default:return false;}});
        LinearLayout heading=row();heading.setPadding(dp(20),0,dp(8),0);TextView title=text("当前播放",20,ink);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);heading.addView(title);count=text("",13,muted);count.setPadding(dp(8),dp(2),0,0);heading.addView(count,new LinearLayout.LayoutParams(0,dp(48),1));clear=action("清空","清空播放列表",this::confirmClear);heading.addView(clear);TextView close=action("×","关闭播放列表",this::dismiss);close.setTextSize(26);heading.addView(close);surface.addView(heading);
        View rule=new View(getContext());rule.setBackgroundColor(0xffe9ede6);surface.addView(rule,new LinearLayout.LayoutParams(-1,dp(1)));
        FrameLayout body=new FrameLayout(getContext());list=new ListView(getContext());list.setDivider(null);list.setCacheColorHint(Color.TRANSPARENT);list.setSelector(new ColorDrawable(0xffe7eee6));list.setVerticalScrollBarEnabled(false);list.setClipToPadding(false);list.setPadding(0,0,0,dp(12));adapter=new QueueAdapter();list.setAdapter(adapter);body.addView(list,new FrameLayout.LayoutParams(-1,-1));TextView empty=text("播放列表为空\n去搜索或收藏中选一首歌吧",15,muted);empty.setGravity(Gravity.CENTER);empty.setLineSpacing(dp(10),1);body.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);surface.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        boolean landscape=getContext().getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;int height=(int)(getContext().getResources().getDisplayMetrics().heightPixels*(landscape?.84f:.68f));setContentView(surface,new ViewGroup.LayoutParams(-1,height));
        Window window=getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setGravity(Gravity.BOTTOM);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.32f);window.getDecorView().setPadding(0,0,0,0);window.setNavigationBarColor(0xfffcfdfb);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);}
        refresh();list.post(()->{for(int i=0;i<songs.size();i++)if(player.current!=null&&songs.get(i).key.equals(player.current.key)){list.setSelectionFromTop(i,dp(8));break;}});
    }
    @Override protected void onStart(){super.onStart();Window window=getWindow();if(window!=null)window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);repository.addListener(this);activity.getApplication().registerActivityLifecycleCallbacks(lifecycle);listening=true;refresh();}
    @Override protected void onStop(){if(listening){repository.removeListener(this);activity.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);listening=false;}super.onStop();}
    @Override public void changed(){if(listening)refresh();}
    private void refresh(){if(adapter==null)return;songs.clear();songs.addAll(player.queueSnapshot());count.setText("("+songs.size()+")");clear.setEnabled(!songs.isEmpty());clear.setAlpha(songs.isEmpty()?.35f:1f);adapter.notifyDataSetChanged();}
    private void confirmClear(){if(songs.isEmpty())return;new UiSheet.Builder(activity).setTitle("清空播放列表？").setMessage("当前播放也将停止。").setPositiveButton("清空",(d,w)->{player.clearQueue();refresh();}).setNegativeButton("取消",null).show();}
    private void remove(String key){List<Song> current=player.queueSnapshot();for(int i=0;i<current.size();i++)if(current.get(i).key.equals(key)){player.removeQueued(i);break;}refresh();}
    private final class SwipeRow extends FrameLayout {
        final LinearLayout front=row();
        final TextView delete=action("移出队列","移出播放列表",()->{});
        final SwipeGesture gesture=new SwipeGesture(dp(76),android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop());
        boolean closingTap;
        SwipeRow(){super(QueueSheet.this.getContext());setClipChildren(true);setMinimumHeight(dp(72));setClickable(true);delete.setTextColor(Color.WHITE);delete.setBackgroundColor(0xffcf5e56);delete.setFocusable(false);delete.setVisibility(View.INVISIBLE);addView(delete,new FrameLayout.LayoutParams(dp(76),-1,Gravity.END));front.setMinimumHeight(dp(72));front.setPadding(dp(8),0,dp(20),0);addView(front,new FrameLayout.LayoutParams(-1,-2));}
        void reveal(float value){delete.setVisibility(value<0?View.VISIBLE:View.INVISIBLE);front.animate().cancel();front.setTranslationX(value);delete.setImportantForAccessibility(value<0?View.IMPORTANT_FOR_ACCESSIBILITY_YES:View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void close(){delete.setVisibility(View.INVISIBLE);front.animate().cancel();front.setTranslationX(0);delete.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);if(openRow==this)openRow=null;}
        @Override public boolean onInterceptTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN){front.animate().cancel();gesture.begin(event.getRawX(),event.getRawY(),front.getTranslationX());closingTap=front.getTranslationX()<0&&event.getX()<getWidth()-dp(76);if(openRow!=null&&openRow!=this){openRow.close();closingTap=true;}return closingTap;}if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&gesture.move(event.getRawX(),event.getRawY())){getParent().requestDisallowInterceptTouchEvent(true);return true;}return false;}
        @Override public boolean onTouchEvent(MotionEvent event){switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:front.animate().cancel();gesture.begin(event.getRawX(),event.getRawY(),front.getTranslationX());return true;
            case MotionEvent.ACTION_MOVE:if(gesture.move(event.getRawX(),event.getRawY())){getParent().requestDisallowInterceptTouchEvent(true);if(openRow!=null&&openRow!=this)openRow.close();reveal(gesture.offset);}return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:boolean canceled=event.getActionMasked()==MotionEvent.ACTION_CANCEL;boolean dragged=gesture.dragging;float target=gesture.finish(canceled);if(!dragged&&closingTap)target=0;delete.setVisibility(target<0?View.VISIBLE:View.INVISIBLE);if(target<0){openRow=this;delete.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);}else{if(openRow==this)openRow=null;delete.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}front.animate().translationX(target).setDuration(150).start();getParent().requestDisallowInterceptTouchEvent(false);if(!canceled&&!dragged&&!closingTap)performClick();closingTap=false;return true;
            default:return super.onTouchEvent(event);
        }}
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private final class QueueAdapter extends BaseAdapter {
        @Override public int getCount(){return songs.size();}
        @Override public Song getItem(int position){return songs.get(position);}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View reusable,ViewGroup parent){
            Holder h;SwipeRow item;
            if(reusable==null){item=new SwipeRow();h=new Holder();h.marker=text("",12,muted);h.marker.setGravity(Gravity.CENTER);item.front.addView(h.marker,new LinearLayout.LayoutParams(dp(22),-1));h.cover=new ArtworkView(getContext(),false);item.front.addView(h.cover,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout copy=new LinearLayout(getContext());copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(Gravity.CENTER_VERTICAL);copy.setPadding(dp(10),dp(10),dp(8),dp(10));h.title=text("",16,ink);h.title.setSingleLine(true);h.title.setEllipsize(TextUtils.TruncateAt.END);h.artist=text("",12,muted);h.artist.setSingleLine(true);h.artist.setEllipsize(TextUtils.TruncateAt.END);h.artist.setPadding(0,dp(3),0,0);copy.addView(h.title);copy.addView(h.artist);item.front.addView(copy,new LinearLayout.LayoutParams(0,-2,1));h.more=action("⋮","歌曲操作",()->{});h.more.setTextSize(24);item.front.addView(h.more);item.setTag(h);}else{item=(SwipeRow)reusable;h=(Holder)item.getTag();}
            Song song=getItem(position);if(!song.key.equals(h.key)){item.close();h.key=song.key;}boolean active=player.current!=null&&player.current.key.equals(song.key);h.marker.setText(active?(player.wantsPlayback()?"▶":"Ⅱ"):"");h.cover.bind(song);h.more.setOnClickListener(v->new UiSheet.Builder(activity).setTitle(song.title).setItems(new String[]{"播放歌曲","移出队列"},(d,w)->{if(w==0)PlaybackUi.play(activity,repository,song,player.queueSnapshot());else remove(song.key);}).show());h.marker.setTextColor(active?green:muted);h.title.setText(song.title);h.title.setTextColor(active?green:ink);h.title.setTypeface(Typeface.DEFAULT,active?Typeface.BOLD:Typeface.NORMAL);h.artist.setText(song.artist.isEmpty()?"未知歌手":song.artist);h.artist.setTextColor(active?green:muted);item.front.setBackgroundColor(active?0xffedf3eb:0xfffcfdfb);item.setContentDescription((active?"当前歌曲，":"")+song.title+"，"+song.artist+"，左滑移除");item.delete.setContentDescription("移除 "+song.title);item.delete.setOnClickListener(v->remove(song.key));item.setOnClickListener(v->{if(openRow!=null){openRow.close();return;}PlaybackUi.play(activity,repository,song,player.queueSnapshot());});
            item.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS,"移出播放列表"));}@Override public boolean performAccessibilityAction(View host,int action,Bundle args){if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS){remove(song.key);return true;}return super.performAccessibilityAction(host,action,args);}});
            return item;
        }
    }
    private static final class Holder {String key="";TextView marker,title,artist,more;ArtworkView cover;}
}
