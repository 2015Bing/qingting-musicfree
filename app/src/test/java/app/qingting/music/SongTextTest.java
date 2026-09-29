package app.qingting.music;
import org.junit.Test;
import static org.junit.Assert.*;
public class SongTextTest {
    @Test public void decodesEntitiesAndNormalizesWhitespace() {
        assertEquals("A & B <3 \"' 😀",SongText.clean(" A&nbsp;&amp; B &#60;3 &quot;&apos; &#x1F600; "));
        assertEquals("中 文",SongText.clean("&#20013;\u00a0\t&#x6587;"));
        assertEquals("A B",SongText.clean("A&amp;nbsp;B"));
        assertEquals("&unknown; &#xD800; &#0;",SongText.clean("&unknown; &#xD800; &#0;"));
    }
    @Test public void removesHighlightTagsButKeepsMusicalText() {
        assertEquals("晴天 <3",SongText.clean("<em class='keyword'>晴天</em> <3"));
        assertEquals("晴天",SongText.clean("&lt;em&gt;晴天&lt;/em&gt;"));
        assertEquals("",SongText.clean(null));
    }
}
