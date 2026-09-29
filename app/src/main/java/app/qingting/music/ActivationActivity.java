package app.qingting.music;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/** The only launcher entry: successful local activation skips this form on subsequent launches. */
public final class ActivationActivity extends Activity {
    private final int green=Color.rgb(36,94,79),ink=Color.rgb(28,46,39),muted=Color.rgb(111,123,114);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LicenseClient client=new LicenseClient();
    private EditText input;private TextView status,submit;private boolean checking;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(LicenseStore.activated(this)){enter();return;}
        LinearLayout root=column();root.setBackgroundColor(Color.rgb(247,248,244));root.setPadding(dp(24),dp(24),dp(24),dp(24));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(dp(24),i.top+dp(24),dp(24),i.bottom+dp(24));}else v.setPadding(dp(24),insets.getSystemWindowInsetTop()+dp(24),dp(24),insets.getSystemWindowInsetBottom()+dp(24));return insets;});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));LinearLayout content=column();content.setGravity(Gravity.CENTER_VERTICAL);scroll.addView(content);
        TextView brand=text("轻听",36,green,true);content.addView(brand);space(content,8);content.addView(text("少一点寻找，多一点音乐。",14,muted,false));space(content,32);
        LinearLayout card=column();card.setPadding(dp(20),dp(24),dp(20),dp(24));card.setBackground(shape(Color.WHITE));content.addView(card);
        card.addView(text("激活轻听",24,ink,true));space(card,10);card.addView(text("输入购买时获得的授权码。首次激活需要联网，成功后本机无需重复输入。",14,muted,false));space(card,22);
        input=new EditText(this);input.setSingleLine(true);input.setTextSize(16);input.setHint("粘贴授权码");input.setContentDescription("授权码");input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);input.setMinHeight(dp(52));input.setPadding(dp(12),dp(10),dp(12),dp(10));input.setBackground(shape(Color.rgb(240,244,237)));if(state!=null)input.setText(state.getString("code",""));card.addView(input);space(card,12);
        status=text("",13,muted,false);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(status);space(card,16);
        submit=text("验证并进入",16,Color.WHITE,true);submit.setGravity(Gravity.CENTER);submit.setMinHeight(dp(52));submit.setBackground(shape(green));submit.setOnClickListener(v->activate());card.addView(submit);input.setOnEditorActionListener((v,id,event)->{if(id==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){activate();return true;}return false;});
        space(content,20);TextView help=text("使用手册",14,green,true);help.setGravity(Gravity.CENTER);help.setMinHeight(dp(48));help.setOnClickListener(v->startActivity(new Intent(this,HelpActivity.class)));content.addView(help);
        content.addView(text("卸载或清除应用数据后，需要重新激活。\n授权码无法使用时，请联系卖家核对。",12,muted,false));
        if(BuildConfig.LICENSE_URL.isEmpty())status.setText("授权服务尚未配置，请联系卖家提供可激活版本。");
    }
    private void activate(){
        if(checking)return;String code=input.getText().toString();
        try{LicensePolicy.hash(code);}catch(IllegalArgumentException e){status.setText("请填写完整授权码，支持字母、数字和短横线（16–128 位）。");return;}
        if(BuildConfig.LICENSE_URL.isEmpty()){status.setText("授权服务尚未配置，请联系卖家提供可激活版本。");return;}
        checking=true;submit.setEnabled(false);submit.setAlpha(.6f);input.setEnabled(false);submit.setText("正在验证…");status.setText("正在连接授权服务，请稍候");
        worker.execute(()->{
            boolean accepted=false;String problem=null;
            try{accepted=client.verify(BuildConfig.LICENSE_URL,code);}catch(org.json.JSONException e){problem="授权文件格式异常，请联系卖家";}catch(Exception e){problem="验证未完成，请检查网络，稍后重试；持续失败请联系卖家。";}
            boolean valid=accepted;String error=problem;main.post(()->{
                if(isFinishing()||isDestroyed())return;checking=false;submit.setEnabled(true);submit.setAlpha(1);input.setEnabled(true);submit.setText("验证并进入");
                if(error!=null)status.setText(error);else if(!valid)status.setText("授权码不正确或未启用，请核对后重试。");else if(!LicenseStore.save(this,code))status.setText("激活状态保存失败，请检查设备存储空间后重试。");else enter();
            });
        });
    }
    @androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
    private void enter(){startActivity(new Intent(this,MainActivity.class));finish();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(input!=null)out.putString("code",input.getText().toString());}
    @Override protected void onDestroy(){client.cancel();worker.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void space(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private TextView text(String s,int size,int color,boolean bold){TextView view=new TextView(this);view.setText(s);view.setTextSize(size);view.setTextColor(color);view.setLineSpacing(dp(3),1);if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;}
    private GradientDrawable shape(int color){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(16));return drawable;}
}
