package io.github.clonnix.discordviewtracker;

import java.io.File;
import java.nio.file.Files;

// Single on/off switch for the rude rules block in OpenAIClient's system prompt.
// Persisted as a one-line text file so it survives restarts, same pattern as GoatList.
public class RudeMode {

    private static final String FILE = "rudemode.txt";
    private static boolean enabled = false;

    public static void load() {
        try {
            File f = new File(FILE);
            if (f.exists()) enabled = Boolean.parseBoolean(new String(Files.readAllBytes(f.toPath())).trim());
            System.out.println("rudemode loading worked");
        } catch (Exception e) { e.printStackTrace(); System.out.println("rudemode loading failed"); }
    }

    public static boolean isEnabled() { return enabled; }

    // Flips the flag and returns the new state
    public static boolean toggle() {
        enabled = !enabled;
        try { Files.write(new File(FILE).toPath(), String.valueOf(enabled).getBytes()); }
        catch (Exception e) { e.printStackTrace(); }
        return enabled;
    }
}
