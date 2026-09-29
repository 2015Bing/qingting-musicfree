package app.qingting.music;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Read-only HTTPS access; never ships OSS write credentials or persists the whitelist. */
public final class LicenseClient {
    private static final OkHttpClient CLIENT=new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS).followSslRedirects(false).build();
    private volatile Call active;
    public boolean verify(String url,String code) throws Exception {
        LicensePolicy.hash(code);
        HttpUrl parsed=HttpUrl.parse(url);
        if(parsed==null||!parsed.isHttps()||!parsed.username().isEmpty()||!parsed.password().isEmpty())throw new IOException("授权服务尚未正确配置，请联系卖家");
        Call call=CLIENT.newCall(new Request.Builder().url(parsed).header("Cache-Control","no-cache").build());active=call;
        try(Response response=call.execute()){
            if(!response.isSuccessful())throw new IOException("授权服务暂时无法访问（"+response.code()+"），请稍后重试或联系卖家");
            if(response.body()==null)throw new IOException("授权服务返回内容为空，请联系卖家");
            try(InputStream input=response.body().byteStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int read;while((read=input.read(buffer))!=-1){if(output.size()+read>1024*1024)throw new IOException("授权文件过大，请联系卖家");output.write(buffer,0,read);}
                return LicensePolicy.accepts(new String(output.toByteArray(),StandardCharsets.UTF_8),code);
            }
        }finally{active=null;}
    }
    public void cancel(){Call call=active;if(call!=null)call.cancel();}
}
