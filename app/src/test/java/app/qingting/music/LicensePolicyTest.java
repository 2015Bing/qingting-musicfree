package app.qingting.music;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;

public class LicensePolicyTest {
    private static final String CODE="ABCD-EFGH-IJKL-MNOP";
    private String whitelist(String hash) throws Exception {return new JSONObject().put("version",1).put("keyHashes",new JSONArray().put(hash)).toString();}
    @Test public void matchesOnlyHashedCodesAndNormalizesInput() throws Exception {
        String hash=LicensePolicy.hash(CODE);assertEquals("a974bce510987bf3e9b6bc206fbb5ade39ae75403502ae6f78f52cd047ff76ab",hash);assertEquals(64,hash.length());assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals(hash,LicensePolicy.hash("  abcd-efgh-ijkl-mnop  "));
        assertTrue(LicensePolicy.accepts(whitelist(hash),CODE));assertTrue(LicensePolicy.accepts(whitelist(hash.toUpperCase(java.util.Locale.ROOT))," abcd-efgh-ijkl-mnop "));
        assertFalse(LicensePolicy.accepts(whitelist(hash),"ABCD-EFGH-IJKL-MNOQ"));assertFalse(LicensePolicy.accepts("{\"version\":1,\"keyHashes\":[]}",CODE));
    }
    @Test public void rejectsInvalidCodesBeforeHashing(){
        for(String code:new String[]{null,"short","ABCD EFGH IJKL MNOP","ABCDEFGHIJKLMNOP中","ABCDEFGHIJKLMNOß","ABCDEFGHIJKLMNOı",new String(new char[129]).replace('\0','A')})assertThrows(IllegalArgumentException.class,()->LicensePolicy.hash(code));
        assertEquals(64,LicensePolicy.hash("ABCDEFGHIJKLMNOP").length());assertEquals(64,LicensePolicy.hash(new String(new char[128]).replace('\0','A')).length());
    }
    @Test public void rejectsMalformedSchemaAndPlaintext() throws Exception {
        for(String json:new String[]{"bad","[]","{}","{\"version\":2,\"keyHashes\":[]}","{\"version\":\"1\",\"keyHashes\":[]}","{\"version\":1.0,\"keyHashes\":[]}","{\"version\":1,\"keyHashes\":{}}","{\"version\":1,\"keyHashes\":[null]}","{\"version\":1,\"keyHashes\":[12]}","{\"version\":1,\"keyHashes\":[],\"keys\":[]}",whitelist(CODE),"{\"version\":1,\"keyHashes\":[]} trailing"})assertThrows(Exception.class,()->LicensePolicy.accepts(json,CODE));
    }
    @Test public void validatesEntireWhitelistEvenAfterMatching() throws Exception {
        String hash=LicensePolicy.hash(CODE);JSONObject json=new JSONObject().put("version",1).put("keyHashes",new JSONArray().put(hash).put("invalid"));
        assertThrows(JSONException.class,()->LicensePolicy.accepts(json.toString(),CODE));
        JSONArray oversized=new JSONArray();for(int i=0;i<10001;i++)oversized.put(hash);
        json.put("keyHashes",oversized);assertThrows(JSONException.class,()->LicensePolicy.accepts(json.toString(),CODE));
    }
}
