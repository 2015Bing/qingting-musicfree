package app.qingting.music;
import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogPolicyTest {
    @Test public void mergesCaseAndWhitespaceButKeepsVersionsAndArtists() {
        assertEquals(CatalogPolicy.key(" Yellow ", "Coldplay", "Parachutes"), CatalogPolicy.key("yellow", " coldplay ", "Parachutes"));
        assertNotEquals(CatalogPolicy.key("Yellow (Live)", "Coldplay", ""), CatalogPolicy.key("Yellow", "Coldplay", ""));
        assertNotEquals(CatalogPolicy.key("Yellow", "Coldplay", ""), CatalogPolicy.key("Yellow", "Cover", ""));
        assertNotEquals(CatalogPolicy.key("Yellow", "Coldplay", "A"), CatalogPolicy.key("Yellow", "Coldplay", "B"));
    }
    @Test public void failuresCoolDownAndRecoverWithoutDeletingSource() {
        assertFalse(CatalogPolicy.hidden(2, 1000, 2000));
        assertTrue(CatalogPolicy.hidden(3, 1000, 2000));
        assertFalse(CatalogPolicy.hidden(3, 1000, 1000 + 15 * 60 * 1000));
        assertFalse(CatalogPolicy.hidden(0, 1000, 2000));
    }
    @Test public void emptyArtistDoesNotMergeDifferentUnknownRecordings() {
        assertFalse(CatalogPolicy.canGroup("Song", ""));
        assertFalse(CatalogPolicy.canGroup("", "Artist"));
        assertTrue(CatalogPolicy.canGroup("Song", "Artist"));
    }
}
