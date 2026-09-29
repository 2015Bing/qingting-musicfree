package app.qingting.music;
import org.junit.Test;import static org.junit.Assert.*;
public class PlaylistFilterTest {
 @Test public void nameFilterIgnoresCaseAndWhitespacePreservesOrder(){LibraryState l=new LibraryState();LibraryState.Playlist a=l.create("Morning 通勤");l.create("夜晚");LibraryState.Playlist c=l.create("morning coffee");assertEquals(java.util.Arrays.asList(a,c),l.filteredPlaylists(" MORNING "));assertEquals(3,l.filteredPlaylists("").size());assertEquals(1,l.filteredPlaylists("通勤").size());assertTrue(l.filteredPlaylists("不存在").isEmpty());assertEquals(3,l.playlists.size());}
}
