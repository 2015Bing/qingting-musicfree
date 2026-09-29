package app.qingting.music;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Locale;
import org.json.*;

/** Hash-only activation whitelist shared by the network client and local tests. */
public final class LicensePolicy {
    private LicensePolicy(){}
    public static String hash(String input) throws IllegalArgumentException {
        if(input==null)throw new IllegalArgumentException("激活码不能为空");
        String trimmed=input.trim();
        if(!trimmed.matches("[A-Za-z0-9-]{16,128}"))throw new IllegalArgumentException("激活码格式不正确");
        String normalized=trimmed.toUpperCase(Locale.ROOT);
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));StringBuilder result=new StringBuilder(64);for(byte value:digest){int unsigned=value&255;result.append(Character.forDigit(unsigned>>>4,16)).append(Character.forDigit(unsigned&15,16));}return result.toString();}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}
    }
    public static boolean accepts(String json,String code) throws JSONException {
        String expected=hash(code);if(json==null)throw new JSONException("缺少激活名单");
        JSONTokener tokens=new JSONTokener(json);Object value=tokens.nextValue();
        if(!(value instanceof JSONObject)||tokens.nextClean()!=0)throw new JSONException("激活名单格式不正确");
        JSONObject data=(JSONObject)value;Object version=data.opt("version");
        if(data.length()!=2||!(version instanceof Integer||version instanceof Long)||((Number)version).longValue()!=1||!(data.opt("keyHashes") instanceof JSONArray))throw new JSONException("激活名单版本或字段不正确");
        JSONArray hashes=data.getJSONArray("keyHashes");if(hashes.length()>10000)throw new JSONException("激活名单超过上限");
        boolean matched=false;for(int i=0;i<hashes.length();i++){Object entry=hashes.get(i);if(!(entry instanceof String)||!((String)entry).matches("[0-9a-fA-F]{64}"))throw new JSONException("激活名单包含无效哈希");if(expected.equals(((String)entry).toLowerCase(Locale.ROOT)))matched=true;}
        return matched;
    }
}
