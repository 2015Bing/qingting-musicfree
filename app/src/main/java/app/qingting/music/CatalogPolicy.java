package app.qingting.music;
import java.text.Normalizer;
import java.util.Locale;

public final class CatalogPolicy {
    private CatalogPolicy() {}
    private static String normalize(String s) { return Normalizer.normalize(s, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    public static String key(String title, String artist, String album) { return normalize(title)+"\u001f"+normalize(artist)+"\u001f"+normalize(album); }
    public static boolean canGroup(String title, String artist) { return !title.trim().isEmpty() && !artist.trim().isEmpty(); }
    public static boolean hidden(int failures, long failedAt, long now) { return failures >= 3 && now - failedAt < 15 * 60 * 1000L; }
}
