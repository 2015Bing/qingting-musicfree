package app.qingting.music;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class) @androidx.media3.common.util.UnstableApi
public class NavigationLayoutTest {
    @Test public void iconsAreCenteredAndLibraryNavigationDoesNotConsumeViewport() throws Exception {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();
        Activity activity=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("nav");field.setAccessible(true);
        LinearLayout nav=(LinearLayout)field.get(activity);
        try {instrumentation.runOnMainSync(()->{
            float density=activity.getResources().getDisplayMetrics().density;
            int width=Math.round(360*density),height=Math.round(640*density);
            nav.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(56*density),View.MeasureSpec.EXACTLY));nav.layout(0,0,width,nav.getMeasuredHeight());
            assertEquals(4,nav.getChildCount());
            for(int i=0;i<4;i++){
                LinearLayout item=(LinearLayout)nav.getChildAt(i);assertEquals(1,item.getChildCount());assertTrue(item.getChildAt(0) instanceof ImageView);
                View icon=item.getChildAt(0);assertEquals(item.getWidth()/2f,(icon.getLeft()+icon.getRight())/2f,1f);assertEquals(item.getHeight()/2f,(icon.getTop()+icon.getBottom())/2f,1f);
                assertNotNull(item.getContentDescription());
            }
            MusicRepository repo=((MusicApp)activity.getApplication()).repository;
            for(String page:new String[]{"favorites","recent"}){
                LibraryUi library=new LibraryUi(activity,repo,()->{});Bundle state=new Bundle();state.putString("libraryPage",page);library.restore(state);
                LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);library.render(content);
                ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.addView(content);
                scroll.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));scroll.layout(0,0,width,height);
                View navigation=content.getChildAt(0),heading=content.getChildAt(2);
                assertEquals(0,navigation.getTop());assertEquals(Math.round(48*density),navigation.getHeight());assertTrue(heading.getBottom()<Math.round(150*density));
            }
        });} finally {instrumentation.runOnMainSync(activity::finish);}
    }
}
