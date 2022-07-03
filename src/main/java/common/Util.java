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

}
