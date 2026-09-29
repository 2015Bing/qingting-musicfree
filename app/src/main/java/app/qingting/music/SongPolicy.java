package app.qingting.music;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class SongPolicy {
    private static final long FRESH_MS=5*60*1000L;
    private SongPolicy() {}
    private static String normalized(String text) {
        return Normalizer.normalize(SongText.clean(text),Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('’','\'').replace('‘','\'').trim();
    }
    private static boolean known(String value) {
        return !value.isEmpty()&&!value.equals("未知")&&!value.equals("未知歌手")&&!value.equals("未知艺术家")&&!value.equals("unknown")&&!value.equals("unknown artist")&&!value.equals("未命名歌曲");
    }
    public static boolean sameRecording(Models.Song expected,Models.Song candidate) {
        if(expected==null||candidate==null)return false;
        String title=normalized(expected.title),artist=normalized(expected.artist);
        if(!known(title)||!known(artist)||!title.equals(normalized(candidate.title))||!artist.equals(normalized(candidate.artist)))return false;
        String album=normalized(expected.album),otherAlbum=normalized(candidate.album);
        double duration=duration(expected),otherDuration=duration(candidate);if(duration>0&&otherDuration>0){if(Math.abs(duration-otherDuration)>2)return false;if(!versionLabel(expected).equals(versionLabel(candidate)))return false;return true;}return album.isEmpty()||otherAlbum.isEmpty()||album.equals(otherAlbum);
    }
    private static double duration(Models.Song song){if(song.routes.isEmpty())return 0;double d=song.routes.get(0).raw.optDouble("duration",0);if(d>10000)d/=1000;return d;}
    public static int relevance(Models.Song song,String query) {
        String q=normalized(query),title=normalized(song.title),artist=normalized(song.artist);
        if(q.isEmpty())return 0;
        if(q.equals(title)||q.equals(title+" "+artist)||q.equals(artist+" "+title))return 0;
        String[] tokens=q.split("\\s+");boolean all=true;
        for(String token:tokens)if(!title.contains(token)&&!artist.contains(token)){all=false;break;}
        if(all)return 1;
        for(String token:tokens)if(title.contains(token))return 2;
        for(String token:tokens)if(artist.contains(token))return 3;
        return 4;
    }
    public static String versionLabel(Models.Song song) {
        String title=normalized(song.title);
        if(Pattern.compile("(?i)(?:\\bcover\\s*[:：(（]|[\\[(（]\\s*cover\\b|翻唱)").matcher(title).find())return "翻唱";
        if(Pattern.compile("(?i)(?:[\\[(（]\\s*live\\b|[-—]\\s*live(?:\\s|$)|现场版|现场录音|演唱会版)").matcher(title).find())return "Live";
        if(Pattern.compile("(?i)(?:\\bremix\\b|混音版)").matcher(title).find())return "Remix";
        if(Pattern.compile("(?i)(?:\\binstrumental\\b|伴奏|纯音乐版)").matcher(title).find())return "伴奏";
        return "";
    }
    public static boolean recentlyPlayable(Models.Route route,long now) {
        return route.verifiedPlayable&&route.error.isEmpty()&&((route.playbackActive&&(route.status.equals("正在播放")||route.status.equals("播放通过")))||(route.verifiedAt>0&&now>=route.verifiedAt&&now-route.verifiedAt<=FRESH_MS));
    }
    public static int playableCount(Models.Song song,long now) {
        int count=0;for(Models.Route route:song.routes)if(recentlyPlayable(route,now))count++;return count;
    }
    public static Models.Route nextProbe(Models.Song song,Predicate<Models.Route> eligible,Set<Models.Route> attempted,long now) {
        for(Models.Route route:song.routes)if(eligible.test(route)&&(recentlyPlayable(route,now)||route.verifying||route.playbackActive))return null;
        for(Models.Route route:song.routes)if(eligible.test(route)&&!attempted.contains(route)&&!route.verifying&&!route.playbackActive&&route.error.isEmpty())return route;
        return null;
    }
}
