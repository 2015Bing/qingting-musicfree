package app.qingting.music;
import okhttp3.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class Http {
    private static final java.util.concurrent.ScheduledExecutorService CANCELLATIONS=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"http-cancellation");t.setDaemon(true);return t;});
    private static java.util.concurrent.ScheduledFuture<?> watch(Call call,java.util.function.BooleanSupplier valid){return CANCELLATIONS.scheduleAtFixedRate(()->{if(!valid.getAsBoolean())call.cancel();},0,100,TimeUnit.MILLISECONDS);}
    private static final OkHttpClient CLIENT=new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS).callTimeout(15,TimeUnit.SECONDS).build();
    public static Request.Builder request(String url) throws IOException {
        if(!url.startsWith("http://")&&!url.startsWith("https://"))throw new IOException("仅支持 HTTP(S) 地址");
        return new Request.Builder().url(url).header("User-Agent","QingTing/0.1 Android");
    }
    public static String get(String url) throws Exception {
        try(Response r=CLIENT.newCall(request(url).build()).execute()) { if(!r.isSuccessful())throw new IOException("HTTP "+r.code());return read(r,3*1024*1024); }
    }
    private static String read(Response r,int limit) throws IOException {
        return new String(readBytes(r,limit),java.nio.charset.StandardCharsets.UTF_8);
    }
    private static byte[] readBytes(Response r,int limit) throws IOException {
        if(r.body()==null)throw new IOException("响应为空");
        try(InputStream in=r.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192]; int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("响应超过大小限制");out.write(b,0,n);}return out.toByteArray();
        }
    }
    public static void cancel(Object owner){for(Call c:CLIENT.dispatcher().runningCalls())if(c.request().tag()==owner)c.cancel();for(Call c:CLIENT.dispatcher().queuedCalls())if(c.request().tag()==owner)c.cancel();}
    public static JSONObject bridge(JSONObject config) throws Exception {return bridge(config,null);}
    public static JSONObject bridge(JSONObject config,Object owner) throws Exception {return bridge(config,owner,()->true);}
    public static JSONObject bridge(JSONObject config,Object owner,java.util.function.BooleanSupplier valid) throws Exception {
        Request.Builder b=request(config.getString("url")).tag(owner);JSONObject headers=config.optJSONObject("headers");
        if(headers!=null)for(Iterator<String> i=headers.keys();i.hasNext();){String key=i.next();if(!headers.isNull(key))b.header(key,headers.optString(key));}
        String method=config.optString("method","GET");String body=config.isNull("body")?"":config.optString("body");
        if(!method.equals("GET")&&!method.equals("HEAD"))b.method(method,RequestBody.create(body,MediaType.parse(headers==null?"application/json":headers.optString("Content-Type","application/json"))));else b.method(method,null);
        Call call=CLIENT.newCall(b.build());java.util.concurrent.ScheduledFuture<?> watcher=watch(call,valid);try(Response r=call.execute()) {
            JSONObject h=new JSONObject();for(String k:r.headers().names())h.put(k,r.header(k));
            JSONObject result=new JSONObject().put("status",r.code()).put("headers",h);
            if(config.optString("responseType").equals("arraybuffer"))return result.put("dataEncoding","base64").put("data",java.util.Base64.getEncoder().encodeToString(readBytes(r,3*1024*1024)));
            return result.put("data",read(r,3*1024*1024));
        }finally{watcher.cancel(false);}
    }
    public static byte[] image(String url)throws Exception{return image(url,()->true);}
    public static byte[] image(String url,java.util.function.BooleanSupplier valid)throws Exception{Call call=CLIENT.newCall(request(url).build());java.util.concurrent.ScheduledFuture<?> watcher=watch(call,valid);try(Response r=call.execute()){if(!r.isSuccessful())throw new IOException("图片不可用");return readBytes(r,2*1024*1024);}finally{watcher.cancel(false);}}
    public static void probe(Models.Media media) throws Exception {probe(media,()->true);}
    public static void probe(Models.Media media,java.util.function.BooleanSupplier valid) throws Exception {
        Request.Builder b=request(media.url);for(Map.Entry<String,String> e:media.headers.entrySet())b.header(e.getKey(),e.getValue());b.header("Range","bytes=0-2047");
        Call call=CLIENT.newCall(b.build());java.util.concurrent.ScheduledFuture<?> watcher=watch(call,valid);try(Response r=call.execute()) {
            if(!r.isSuccessful())throw new IOException("HTTP "+r.code());if(r.body()==null)throw new IOException("空音源");
            byte[] buffer=new byte[2048];int count=r.body().byteStream().read(buffer);if(count<=0)throw new IOException("音源没有数据");byte[] bytes=java.util.Arrays.copyOf(buffer,count);
            String type=r.header("Content-Type","").toLowerCase(Locale.ROOT);String head=new String(bytes,java.nio.charset.StandardCharsets.UTF_8).trim();
            if(head.startsWith("#EXTM3U")||type.contains("mpegurl"))media.mimeType="application/x-mpegURL";
            if(type.contains("text/html")||type.contains("application/json")||head.startsWith("<!DOCTYPE")||head.startsWith("<html"))throw new IOException("返回了网页或错误信息");
            boolean magic=head.startsWith("#EXTM3U")||head.startsWith("ID3")||head.startsWith("OggS")||head.startsWith("fLaC")||head.startsWith("RIFF")||(bytes.length>1&&(bytes[0]&255)==255&&(bytes[1]&224)==224);
            if(!magic&&!type.startsWith("audio/")&&!type.contains("mpegurl")&&!type.contains("video/"))throw new IOException("未识别音频格式，仍可手动尝试播放");
        }finally{watcher.cancel(false);}
    }
}
