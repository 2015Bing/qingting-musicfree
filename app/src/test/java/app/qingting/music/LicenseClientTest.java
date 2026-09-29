package app.qingting.music;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class LicenseClientTest {
    @Test public void rejectsMissingCleartextOrCredentialBearingEndpointBeforeNetwork(){
        for(String endpoint:new String[]{"","http://example.test/licenses.json","https://user:secret@example.test/licenses.json","file:///license.json"})
            assertThrows(IOException.class,()->new LicenseClient().verify(endpoint,"QT-0123456789ABCDEF"));
    }
}
