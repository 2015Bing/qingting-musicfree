package app.qingting.music;
import android.content.Context;import android.view.*;import android.widget.*;import java.util.*;import static app.qingting.music.Models.*;
/** One native recycling list per screen; controls remain in a scrolling header/footer. */
public final class SongListView extends ListView {
 public interface Binder{View bind(Song song,View recycled,ViewGroup parent);}
 public final LinearLayout header,footer;private List<Song> songs=Collections.emptyList();private Binder binder;private int kind;private final Rows rows=new Rows();
 public SongListView(Context context){super(context);setDivider(null);setDividerHeight(0);setCacheColorHint(android.graphics.Color.TRANSPARENT);setSelector(android.R.color.transparent);setItemsCanFocus(true);header=new LinearLayout(context);header.setOrientation(LinearLayout.VERTICAL);footer=new LinearLayout(context);footer.setOrientation(LinearLayout.VERTICAL);addHeaderView(header,null,false);addFooterView(footer,null,false);setAdapter(rows);}
 public void songs(List<Song> values,int type,Binder binding){songs=new ArrayList<>(values);kind=type;binder=binding;rows.notifyDataSetChanged();}
 public List<View> songRows(){List<View> visible=new ArrayList<>();for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getTag() instanceof Song)visible.add(child);}return visible;}
 public int songCount(){return songs.size();}
 public int positionOf(String key){for(int i=0;i<songs.size();i++)if(songs.get(i).key.equals(key))return getHeaderViewsCount()+i;return -1;}
 private final class Rows extends BaseAdapter{
  public int getCount(){return songs.size();}public Song getItem(int p){return songs.get(p);}public long getItemId(int p){return p;}
  public int getViewTypeCount(){return 3;}public int getItemViewType(int p){return kind;}
  public boolean areAllItemsEnabled(){return false;}public boolean isEnabled(int p){return false;}
  public View getView(int p,View reused,ViewGroup parent){View row=binder.bind(songs.get(p),reused,parent);row.setTag(songs.get(p));row.setLayoutParams(new AbsListView.LayoutParams(-1,-2));return row;}
 }
}
