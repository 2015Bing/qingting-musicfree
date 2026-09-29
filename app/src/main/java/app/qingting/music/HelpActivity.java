package app.qingting.music;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Offline guide; opening this page never changes subscriptions or playback. */
public final class HelpActivity extends Activity {
    private final int green=Color.rgb(36,94,79),ink=Color.rgb(28,46,39),muted=Color.rgb(111,123,114);
    private static final String[][] CHAPTERS={
        {"01  导航与快速开始","底部从左到右是四个图标：柱形榜单＝排行榜，放大镜＝搜索，人像＝我的音乐，滑杆＝订阅源。长按图标可查看名称。\n\n首次打开会自动尝试导入四个默认订阅。点最右侧订阅源图标查看进度，失败时点「补导缺失插件 / 重试」。无需授权码。\n\n点放大镜，输入歌名或歌手后搜索；在歌曲行点击歌名区域播放。也可以直接从排行榜选歌。\n\n底部小播放条：点封面或歌曲标题进入播放详情；三角形／暂停图标控制播放，列表音符图标打开当前播放队列。返回列表不会停止音乐。\n\n本手册入口：订阅源页面顶部的「使用手册」。手册可离线阅读，歌曲播放通常需要联网。"},
        {"02  歌曲、歌单搜索与历史","搜索页顶部可切换「歌曲 / 歌单」，共用搜索框和历史。歌曲按歌名／歌手搜索；歌单按关键词查找在线歌单。\n\n搜索结果以歌曲列表展示，右侧「N 源 ›」表示该歌曲有多少条来源。点击它查看各来源状态、手动播放或「重新检测」；右侧更多菜单可收藏、下一首播放或添加到队尾。不同版本的歌曲可能分别显示。\n\n当前可见歌曲会自动静音检查；近期验证有效时直接复用结果。待检查不等于失败，试播通过也不保证整首或以后一直可播。\n\n「仅看已验证」只显示近期检查通过的结果；结果少时切回全部结果。「来源状态」可查看各源搜索情况，并检测已加载的全部搜索结果。\n\n歌单结果按列表展示封面、名称、作者、歌曲数和来源；插件没有提供的信息不显示。只向支持歌单搜索和详情的启用来源请求，结果陆续出现。点「全部来源」筛选，点「状态」查看不支持或失败原因；「加载更多 / 重试失败来源」继续搜索，刷新可绕过结果缓存。不同来源的同名歌单独立显示。\n\n点击歌单打开详情，不会自动播放。可搜索已加载歌曲、播放当前筛选结果、继续加载分页；歌曲更多菜单可收藏、下一首播放或加入队尾。左上角返回歌单搜索并恢复浏览位置。来源失效时可重试，刷新失败保留已加载歌曲。\n\n在线歌单不能直接加入自建歌单：先收藏需要的歌曲，再从「我的收藏」单首或批量添加。\n\n清空搜索框可看最近 30 条搜索历史。点击记录直接搜索；点「管理」或长按记录进入删除模式，点记录删除单条；「清空」需确认，点「完成」退出。"},
        {"03  排行榜与刷新","点底部最左侧柱形图标进入排行榜。顶部来源标签切换渠道，榜单标题右侧「切换」打开该来源的榜单列表。点击歌曲播放，「播放全部」播放当前已加载的歌曲；底部「加载更多歌曲」继续取下一页。\n\n「刷新榜单」会重新读取各来源目录并检查歌曲数据，隐藏空榜和确认失败的榜单。检查期间可取消，点击单行进度文字查看详情。来源多时需要等待。\n\n榜单不见了：点「管理 → 查看隐藏榜单及原因」，可查看错误并「重新检查」。失败榜单暂时隐藏 30 分钟；空榜可通过刷新重新发现。断网或取消不会当作失败记录。排行榜来源与歌曲播放渠道的健康过滤分别处理。\n\n只想更新当前榜：点「管理 → 刷新当前榜单歌曲」。只想重读可选目录：点「刷新所有来源的榜单目录」。需要自动挑榜：点「自动选择可用榜单并更新缓存」。手动选榜后不会被后台自动选榜打断。"},
        {"04  缓存与再次打开","榜单歌曲列表：最近浏览的榜单在本地保留最多 30 分钟、最多 24 个；切回或重启时优先恢复有效缓存及已加载分页。超过期限、缓存被淘汰或来源配置变化时需要重新获取。需要最新内容可手动刷新当前榜单。\n\n线路验证状态：成功结果保留 5 分钟，失败结果保留 1 分钟；有效期内切榜或重开不会仅因切换重复检测。点歌曲右侧「N 源 › → 重新检测」可主动复检。榜单列表缓存不代表其中每首歌都已验证。\n\n在线歌单搜索和详情在本地缓存最多 30 分钟；详情最多保留 12 份内存记录，本地歌单缓存总量最多 8 MB。返回详情优先恢复已加载分页；重启后重新搜索同一关键词、打开同一歌单可读取有效缓存。更改来源脚本或配置后旧缓存不再使用。离页暂停未完成请求，回来可加载更多或重试。\n\n封面会自动缓存，离开页面后无用的下载会取消。缓存用于减少重复加载，不是整首歌曲下载，也不表示可以离线听歌。"},
        {"05  当前队列与循环模式","点底部小播放条右侧列表音符图标，或播放详情右下角队列图标，打开当前播放列表。点歌曲切歌；左滑后点「移出队列」，或通过歌曲更多菜单移出。标题行可清空队列。\n\n搜索／排行榜歌曲的更多菜单提供「下一首播放」和「添加到队尾」。当前队列与自建歌单是不同列表，移出队列不会取消收藏。\n\n播放模式在歌曲详情页左下角，通过图标点击轮换：顺序播放到末尾停止；双箭头表示列表循环；带数字 1 的循环箭头表示单曲循环。点击有模式提示，长按可查看当前模式。单曲循环时手动上一首／下一首仍可切歌。\n\n队列、模式和播放进度保存在本机，重开默认暂停，可继续播放。"},
        {"06  收藏、最近播放与返回","点底部人像图标打开「我的音乐」，进入「我的收藏」「最近播放」或自建歌单。详情页左上角「← 返回我的音乐」回到音乐库首页；系统返回在批量选择时先退出选择，再返回音乐库。\n\n播放详情点爱心，或歌曲更多菜单点「收藏歌曲」。收藏会保存选定的播放来源；有多个来源且无法确定时，需要选一次固定来源。取消收藏只移除收藏记录。\n\n收藏和自建歌单按保存的固定来源播放。来源失效会提示，由你手动换源，不会悄悄换到其他渠道。可在收藏歌曲更多菜单中「选择收藏来源」；播放详情手动换源后，也可在更多菜单中「将当前来源保存到收藏」。旧版多来源收藏可能需要先指定来源。\n\n收藏、最近播放与歌单详情支持歌名／歌手搜索；搜索框右侧排序图标可切换默认顺序、添加时间、歌名或歌手。最近播放记录听过的歌曲。"},
        {"07  自建歌单与批量添加","在「我的音乐 → 自建歌单」标题旁点加号创建歌单。名称不能空白、不能重名，最多 60 个字符；歌单更多菜单可重命名或删除。\n\n在自建歌单标题下输入名称可快速筛选本地歌单，清空恢复全部；这与搜索页的在线歌单搜索相互独立。\n\n只有「我的收藏」提供添加歌曲到歌单的入口：单首歌曲点更多菜单 → 添加到歌单；多首歌曲点「批量」，勾选后通过底部操作栏「添加到歌单」，选择已有歌单或新建歌单。\n\n「全选」包含当前搜索筛选结果中的所有歌曲，包括屏幕外的歌曲；再次点「取消全选」清除选择。重复歌曲自动跳过。若旧收藏尚未选定来源，先为它选择固定来源再添加。\n\n在歌单中「移出歌单」不影响我的收藏；删除歌单也不会删除收藏记录。收藏、歌单及搜索历史修改会在后台保存，保存成功后界面才更新；若提示保存失败，请重试并检查存储空间。"},
        {"08  播放详情、歌词与定时停止","点小播放条的封面或歌曲标题进入详情。点击唱片，或点「唱片 / 歌词」切换显示。带时间戳的歌词会跟随播放并高亮当前句，点击某句可跳转；只有纯文本的歌词不会同步滚动。\n\n手动滚动歌词后，松手约 3 秒恢复跟随，也可点「回到当前」。暂停时点击歌词或拖进度仍保持暂停。歌词快慢不对时，到右上角更多菜单 → 歌词时间校准，按歌曲调整或重置。无歌词时可尝试「重新加载歌词」。\n\n更多菜单 → 定时停止，可选 15、30、60 分钟后停止，或播完当前歌曲停止，也可关闭定时。倒计时在暂停时仍继续；播完当前歌曲停止优先于循环模式，手动切歌会取消这次单曲结束定时。到期保留队列和进度。"},
        {"09  订阅管理与自动切源","点底部最右侧滑杆图标进入订阅源。首次自动尝试导入四个默认合集；「补导缺失插件 / 重试」只补缺失插件，不覆盖已有配置及停用状态。\n\n「＋ 添加订阅」支持 MusicFree 插件订阅 JSON 地址或单个 JS 地址。插件行可启用、停用、配置和更新；只有插件要求时才需填写自己的账号或 API 参数。MusicFree 与 LX 自定义音源格式不能直接互换。\n\n普通非固定来源歌曲播放失败时，会尝试其他音质和已有线路；仍失败则向其他启用来源补搜匹配歌曲。收藏及歌单的固定来源规则见第 06 节。全部失败时可重试、手动换源或选另一首歌。\n\n「一键过滤渠道」抽样检查歌曲源；被过滤渠道可点「恢复」。抽检通过不代表该源所有歌曲可播，导入成功也不等于播放一定成功。"},
        {"10  常见问题、后台与数据","搜索不到：检查网络、关键词和启用来源；点「来源状态」看失败原因，必要时更新订阅。搜到但播不了：搜索和播放接口可能分别失效，检查来源配置；固定来源歌曲需要手动换源。\n\n排行榜空白：部分插件不提供榜单，或榜单为空／暂时失败。点「刷新榜单」，并在管理中查看隐藏原因。切榜首次仍慢：首次访问或缓存失效需要联网；缓存有效时优先展示已有内容。\n\n后台播放：可使用通知栏和锁屏控制。若切到其他应用后被中断，检查网络及系统对轻听的后台运行、省电限制。打开手册或返回列表不会主动停止音乐。\n\n更新安装：使用同签名正式 APK 覆盖安装，可保留本地数据，无需先卸载。正式包与 debug 测试包不能直接互相覆盖。\n\n收藏、歌单、最近播放、订阅和设置均在本机，目前没有账号同步、备份导出或歌曲下载功能。卸载或清除应用数据会丢失这些内容。轻听不提供音乐会员权益，可用内容由你使用的订阅源决定。"}
    };
    private boolean[] expanded=new boolean[CHAPTERS.length];
    private ScrollView scroll;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);expanded[0]=true;
        if(state!=null){boolean[] saved=state.getBooleanArray("expanded");if(saved!=null&&saved.length==expanded.length)expanded=saved;}
        LinearLayout root=column();root.setBackgroundColor(Color.rgb(247,248,244));root.setPadding(dp(20),0,dp(20),0);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(20),i.top,dp(20),i.bottom);}else v.setPadding(dp(20),insets.getSystemWindowInsetTop(),dp(20),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(0,dp(12),0,dp(12));
        TextView back=label("‹ 返回",16,green,true);back.setGravity(Gravity.CENTER);back.setMinHeight(dp(48));back.setPadding(0,0,dp(18),0);back.setContentDescription("返回上一页");back.setOnClickListener(v->finish());header.addView(back);
        header.addView(label("使用手册",23,ink,true));root.addView(header);
        scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout body=column();body.setPadding(0,dp(4),0,dp(20));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView intro=label("适用 0.5.0 · 按当前界面更新\n点击章节展开查看 · 手册可离线阅读",13,muted,false);intro.setPadding(0,0,0,dp(18));intro.setLineSpacing(dp(5),1);body.addView(intro);
        for(int i=0;i<CHAPTERS.length;i++){
            final int index=i;LinearLayout card=column();GradientDrawable shape=new GradientDrawable();shape.setColor(Color.WHITE);shape.setCornerRadius(dp(16));card.setBackground(shape);
            LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.bottomMargin=dp(10);body.addView(card,cardParams);
            TextView title=label("",16,ink,true);title.setPadding(dp(16),dp(18),dp(16),dp(18));title.setMinHeight(dp(56));card.addView(title);
            TextView copy=label(CHAPTERS[i][1],15,ink,false);copy.setPadding(dp(16),0,dp(16),dp(20));copy.setLineSpacing(dp(6),1);copy.setTextIsSelectable(true);card.addView(copy);
            Runnable update=()->{title.setText(CHAPTERS[index][0]+(expanded[index]?"  −":"  ＋"));title.setContentDescription(CHAPTERS[index][0]+(expanded[index]?"，已展开，点击收起":"，已收起，点击展开"));copy.setVisibility(expanded[index]?View.VISIBLE:View.GONE);};
            title.setOnClickListener(v->{expanded[index]=!expanded[index];update.run();});update.run();
        }
        if(state!=null){int position=state.getInt("scroll",0);scroll.post(()->scroll.scrollTo(0,position));}
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBooleanArray("expanded",expanded);out.putInt("scroll",scroll.getScrollY());}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private TextView label(String text,int size,int color,boolean bold){TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(color);if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;}
}
