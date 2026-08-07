package io.github.clonnix.discordviewtracker;

import java.io.File;
import java.nio.file.Files;

public class GoatList {
    private static final String FILE = "goat.txt";
    private static String message = "";

    public static void load() {
        try {
            File f = new File(FILE);
            if (f.exists()) message = new String(Files.readAllBytes(f.toPath())).trim();
            System.out.println("goat loading worked");
        } catch (Exception e) { e.printStackTrace(); System.out.println("goat loading failed"); }
    }
    public static String get() {return message;}

    public static void set(String text) {
        message = text;
        try {Files.write(new File(FILE).toPath(), text.getBytes()); }
        catch (Exception e) {e.printStackTrace();}
    }
}
