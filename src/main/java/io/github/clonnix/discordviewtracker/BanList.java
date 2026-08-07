package io.github.clonnix.discordviewtracker;

import org.json.JSONArray;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class BanList {

    private static final String FILE = "banlist.json";
    private static final Set<String> banned = new HashSet<>();

    public static void load() {
        File f = new File(FILE);
        if (!f.exists()) return;
        try {
            String content = new String(Files.readAllBytes(Paths.get(FILE)));
            JSONArray arr = new JSONArray(content);
            for (int i = 0; i < arr.length(); i++) {
                banned.add(arr.getString(i).toLowerCase());
            }
            System.out.println("Loaded banlist: " + banned.size() + " users");
        } catch (Exception e) {
            System.err.println("Failed to load banlist: " + e.getMessage());
        }
    }

    public static boolean isBanned(String username) {
        return banned.contains(username.toLowerCase());
    }

    public static void ban(String username) {
        banned.add(username.toLowerCase());
        save();
    }
    public static void unban(String username) {
        banned.remove(username.toLowerCase());
        save();
    }

    private static void save() {
        try {
            JSONArray arr = new JSONArray(banned);
            try (FileWriter fw = new FileWriter(FILE)) {
                fw.write(arr.toString(2));
            }
        } catch (Exception e) {
            System.err.println("Failed to save banlist: " + e.getMessage());
        }
    }
}
