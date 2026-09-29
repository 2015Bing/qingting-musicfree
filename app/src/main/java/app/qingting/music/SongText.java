package app.qingting.music;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SongText {
    private static final Pattern ENTITY=Pattern.compile("&(#(?:[xX][0-9a-fA-F]+|[0-9]+)|[A-Za-z]+);");
    private static final Pattern HIGHLIGHT=Pattern.compile("(?i)</?(?:em|strong|b|i|mark|span|font)(?:\\s+[^<>]*?)?\\s*/?>");
    private SongText() {}
    public static String clean(String text) {
        if(text==null)return "";
        for(int pass=0;pass<3;pass++) {
            Matcher m=ENTITY.matcher(text);StringBuffer out=new StringBuffer();
            while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(decode(m.group(1),m.group())));
            m.appendTail(out);String decoded=out.toString();if(decoded.equals(text))break;text=decoded;
        }
        return HIGHLIGHT.matcher(text).replaceAll("").replaceAll("[\\s\\p{Z}]+"," ").trim();
    }
    private static String decode(String entity,String original) {
        switch(entity) {
            case "nbsp": return " ";case "amp": return "&";case "lt": return "<";case "gt": return ">";
            case "quot": return "\"";case "apos": return "'";case "ensp":case "emsp":case "thinsp": return " ";
            case "ndash": return "–";case "mdash": return "—";case "hellip": return "…";
            case "lsquo": return "‘";case "rsquo": return "’";case "ldquo": return "“";case "rdquo": return "”";
            case "copy": return "©";case "reg": return "®";default: break;
        }
        if(entity.startsWith("#"))try {
            boolean hex=entity.length()>2&&(entity.charAt(1)=='x'||entity.charAt(1)=='X');
            int cp=Integer.parseInt(entity.substring(hex?2:1),hex?16:10);
            if(Character.isValidCodePoint(cp)&&cp>0&&!(cp>=0xD800&&cp<=0xDFFF))return new String(Character.toChars(cp));
        } catch(IllegalArgumentException ignored) {}
        return original;
    }
}
