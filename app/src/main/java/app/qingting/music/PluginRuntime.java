package app.qingting.music;
import android.content.Context;
import android.os.*;
import android.webkit.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Isolated source sessions, serialized per source; idle LRU sessions are reclaimable. */
public final class PluginRuntime {
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newFixedThreadPool(6);
    private final LinkedHashMap<String,Session> sessions=new LinkedHashMap<>(8,.75f,true);
    private final String runtime;private final AtomicLong sequence=new AtomicLong();
    public PluginRuntime(Context context) {
        this.context=context;
        try(java.io.InputStream in=context.getAssets().open("runtime.js");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);runtime=out.toString("UTF-8");
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private final class Session {
        WebView web;volatile boolean closed;boolean ready;final AtomicInteger users=new AtomicInteger();
        volatile String id;volatile JSONObject result;volatile CountDownLatch done;
        String command;
        void finish(String token,String json){if(closed||!token.equals(id))return;try{result=new JSONObject(json);}catch(Exception e){result=error("插件返回格式错误");}done.countDown();}
        void close(){closed=true;Http.cancel(this);if(done!=null)done.countDown();main.post(()->{if(web!=null){web.removeJavascriptInterface("Native");web.stopLoading();web.destroy();web=null;}});}
        @SuppressWarnings("SetJavaScriptEnabled") void execute(String script){
            if(closed)return;command=script;
            try {
                if(web!=null){if(ready)web.evaluateJavascript(command,null);return;}
                web=new WebView(context);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setAllowFileAccess(false);
                web.getSettings().setAllowContentAccess(false);web.getSettings().setBlockNetworkLoads(true);
                web.getSettings().setDomStorageEnabled(false);web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
                web.addJavascriptInterface(new Object(){
                    @JavascriptInterface public void complete(String token,String json){finish(token,json);}
                    @JavascriptInterface public void request(String token,String json){
                        if(closed)return;
                        network.execute(()->{if(closed)return;JSONObject response;try{response=Http.bridge(new JSONObject(json),Session.this,()->!closed);}catch(Exception e){response=error(e.getMessage());}
                            String js="__httpResult("+JSONObject.quote(token)+","+response+")";
                            main.post(()->{if(!closed&&web!=null)web.evaluateJavascript(js,null);});});
                    }
                },"Native");
                web.setWebViewClient(new WebViewClient(){
                    @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
                    @Override public void onPageFinished(WebView v,String url){if(!closed){ready=true;v.evaluateJavascript(runtime+"\n;"+command,null);}}
                    @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){result=error("插件进程已退出，下次调用会重建");if(done!=null)done.countDown();close();return true;}
                });
                web.loadDataWithBaseURL("https://runtime.invalid/","<html><head><meta charset='utf-8'></head><body></body></html>","text/html","UTF-8",null);
            }catch(Exception e){result=error(e.getMessage());done.countDown();close();}
        }
    }
    public void prioritize(String sourceId){synchronized(sessions){Session s=sessions.get(sourceId);if(s!=null&&s.users.get()>0)s.close();}}
    public Object invoke(Models.Source source,String method,JSONArray args) throws Exception {return invoke(source,method,args,()->true);}
    public Object invoke(Models.Source source,String method,JSONArray args,java.util.function.BooleanSupplier valid) throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("不能在界面线程执行插件");
        Session session;
        synchronized(sessions){session=sessions.get(source.id);if(session==null||session.closed){session=new Session();sessions.put(source.id,session);}session.users.incrementAndGet();}
        Session current=session;
        try {synchronized(current){
            if(!valid.getAsBoolean())throw new CancellationException("已取消");if(current.closed)throw new CancellationException("优先播放，已取消旧任务");
            String id=String.valueOf(sequence.incrementAndGet());current.id=id;current.result=null;current.done=new CountDownLatch(1);
            String script="__invoke("+JSONObject.quote(id)+","+JSONObject.quote(source.script)+","+source.variables+","+JSONObject.quote(method)+","+args+");";
            main.post(()->current.execute(script));
            long end=android.os.SystemClock.elapsedRealtime()+22000;while(!current.done.await(100,TimeUnit.MILLISECONDS)){if(!valid.getAsBoolean()){current.close();throw new CancellationException("已取消");}if(android.os.SystemClock.elapsedRealtime()>=end){current.close();throw new java.io.IOException("插件调用超时");}}if(!valid.getAsBoolean())throw new CancellationException("已取消");
            if(current.closed)throw new CancellationException("优先播放，已取消旧任务");JSONObject result=current.result;if(result==null)throw new java.io.IOException("插件没有返回结果");
            if(result.has("error"))throw new java.io.IOException(result.optString("error"));return result.opt("value");
        }} finally {
            current.users.decrementAndGet();
            synchronized(sessions){Iterator<Map.Entry<String,Session>> i=sessions.entrySet().iterator();while(sessions.size()>6&&i.hasNext()){Session idle=i.next().getValue();if(idle.users.get()==0){i.remove();idle.close();}}}
        }
    }
    private static JSONObject error(String message){JSONObject j=new JSONObject();try{j.put("error",message==null?"未知错误":message);}catch(JSONException ignored){}return j;}
}

