package app.qingting.music;
import java.util.*;
import java.util.regex.*;

/** LRC parsing independent of Android, including multi-tags, offsets and plain text. */
public final class Lyrics {
    public static final class Line {
        public final long timeMs;public final String text,translation;
        Line(long time,String text,String translation){timeMs=time;this.text=text;this.translation=translation;}
    }
    public final List<Line> lines;public final boolean timed;public String sourceName="";
    private Lyrics(List<Line> lines,boolean timed){this.lines=Collections.unmodifiableList(lines);this.timed=timed;}
    private static final Pattern TIME=Pattern.compile("\\[(\\d{1,4}):([0-5]\\d)(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern OFFSET=Pattern.compile("\\[offset:([+-]?\\d{1,9})\\]",Pattern.CASE_INSENSITIVE);
    private static TreeMap<Long,String> timedLines(String raw){
        TreeMap<Long,String> result=new TreeMap<>();Matcher offsetMatcher=OFFSET.matcher(raw);long offset=0;
        if(offsetMatcher.find())offset=Long.parseLong(offsetMatcher.group(1));
        for(String line:raw.split("\\r?\\n")){
            Matcher m=TIME.matcher(line);List<Long> times=new ArrayList<>();int end=-1;
            while(m.find()){
                if(end>=0){String text=line.substring(end,m.start()).trim();if(!text.isEmpty()){for(long time:times)result.merge(time,text,(a,b)->a.equals(b)?a:a+"\n"+b);times.clear();}}
                String fraction=m.group(3);long ms=fraction==null?0:Long.parseLong((fraction+"000").substring(0,3));times.add(Math.max(0,Long.parseLong(m.group(1))*60000+Long.parseLong(m.group(2))*1000+ms+offset));end=m.end();
            }
            if(end>=0){String text=line.substring(end).trim();if(!text.isEmpty())for(long time:times)result.merge(time,text,(a,b)->a.equals(b)?a:a+"\n"+b);}
        }return result;
    }
    public static Lyrics parse(String raw,String translation){
        raw=raw==null?"":raw;translation=translation==null?"":translation;
        if(raw.trim().isEmpty()){raw=translation;translation="";}
        TreeMap<Long,String> primary=timedLines(raw),secondary=timedLines(translation);List<Line> lines=new ArrayList<>();
        if(!primary.isEmpty()){for(Map.Entry<Long,String> e:primary.entrySet())lines.add(new Line(e.getKey(),e.getValue(),secondary.getOrDefault(e.getKey(),"")));return new Lyrics(lines,true);}
        for(String row:raw.split("\\r?\\n")){String text=row.trim();if(!text.isEmpty()&&!text.startsWith("["))lines.add(new Line(-1,text,""));}
        return new Lyrics(lines,false);
    }
    public int indexAt(long position){if(!timed)return -1;int lo=0,hi=lines.size()-1,result=-1;while(lo<=hi){int mid=(lo+hi)>>>1;if(lines.get(mid).timeMs<=position){result=mid;lo=mid+1;}else hi=mid-1;}return result;}
    public int indexAt(long position,int delayMs){return indexAt(position-delayMs);}
    public long seekPosition(int index,int delayMs,long duration){if(!timed||index<0||index>=lines.size())return -1;long position=Math.max(0,lines.get(index).timeMs+delayMs);return duration>0?Math.min(position,duration):position;}
}
