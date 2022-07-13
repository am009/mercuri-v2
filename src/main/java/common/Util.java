package common;

import java.io.FileNotFoundException;
import java.util.Scanner;

public class Util {
    /**
     * Load .env file to environment variables.
     * 
     */
    public static void loadDotEnv() {
        var path = System.getProperty("user.dir") + "/.env";
        // read .env file
        Scanner scanner;
        try {
            scanner = new Scanner(new java.io.File(path));
        } catch (FileNotFoundException e) {
            return;
        }
        while (scanner.hasNextLine()) {
            var line = scanner.nextLine();
            // trim
            line = line.trim();
            // skip comments
            if (line.startsWith("#")) {
                continue;
            }
            // remove `export ` prefix
            if (line.startsWith("export ")) {
                line = line.substring(7);
            }
            var index = line.indexOf("=");
            if (index == -1) {
                continue;
            }
            var key = line.substring(0, index);
            var value = line.substring(index + 1);
            System.setProperty(key, value);
        }
    }

    /**
     * 调用前先手动去除前后双引号
     * unescape string literal rule
     * fragment CHAR_LITERAL
        :   ~["\\\r\n]
        |   '\\' ['"?abfnrtv\\]
        |   '\\\n'
        |   '\\\r\n'
        ;
     * @param s
     * @return
     */
    public static String unescapeStringLiteral(String s) {
        // TODO 反斜杠换行？
        return s.replace("\\\\", "\\")
          .replace("\\?", "\u003f")
          .replace("\\v", "\u000b")
          .replace("\\a", "\u0007")
          .replace("\\t", "\t")
          .replace("\\b", "\b")
          .replace("\\n", "\n")
          .replace("\\r", "\r")
          .replace("\\f", "\f")
          .replace("\\'", "\'")
          .replace("\\\"", "\"");
    }

    // ----------------------------------------------------
    // https://gist.github.com/alcides/0bedeabd0c078298af27d544a64df307
    public static String floatToLLVM(float f) {
        return "0x" + toHexString(Double.doubleToRawLongBits((double) f));
    }
    
    public static String doubleToLLVM(double d) {
        return "0x" + toHexString(Double.doubleToRawLongBits(d));
    }
    
    private static String toHexString(long l) {
        int count = (l == 0L) ? 1 : ((64 - Long.numberOfLeadingZeros(l)) + 3) / 4;
        StringBuilder buffer = new StringBuilder(count);
        long k = l;
        do {
            long t = k & 15L;
            if (t > 9) {
                t = t - 10 + 'A';
            } else {
                t += '0';
            }
            count -= 1;
            buffer.insert(0, (char) t);
            k = k >> 4;
        } while (count > 0);
        return buffer.toString();
    }

}
