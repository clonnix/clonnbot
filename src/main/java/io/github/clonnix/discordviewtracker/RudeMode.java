package io.github.clonnix.discordviewtracker;

import java.io.File;
import java.nio.file.Files;

// Single on/off switch for the rude rules block in OpenAIClient's system prompt.
// Persisted as a one-line text file so it survives restarts, same pattern as GoatList.
//
// The actual rude rule text lives in RULES_FILE so it can be edited without
// touching code, and is only handed back to callers when enabled is true.
public class RudeMode {

    private static final String FILE = "rudemode.txt";
    private static final String RULES_FILE = "ruderules.txt";

    private static boolean enabled = false;
    private static String rules = "";

    public static void load() {
        try {
            File f = new File(FILE);
            if (f.exists()) enabled = Boolean.parseBoolean(new String(Files.readAllBytes(f.toPath())).trim());
            System.out.println("rudemode loading worked");
        } catch (Exception e) { e.printStackTrace(); System.out.println("rudemode loading failed"); }

        try {
            File rf = new File(RULES_FILE);
            if (rf.exists()) {
                rules = new String(Files.readAllBytes(rf.toPath()));
            } else {
                System.out.println("ruderules.txt not found — rude mode will add nothing to the prompt");
            }
        } catch (Exception e) { e.printStackTrace(); System.out.println("ruderules loading failed"); }
    }

    public static boolean isEnabled() { return enabled; }

    // Flips the flag and returns the new state
    public static boolean toggle() {
        enabled = !enabled;
        try { Files.write(new File(FILE).toPath(), String.valueOf(enabled).getBytes()); }
        catch (Exception e) { e.printStackTrace(); }
        return enabled;
    }

    // Rules text to splice into OpenAIClient's system prompt.
    // Empty when rude mode is off (or ruderules.txt is missing), so callers
    // can just concatenate this in without extra null/empty checks.
    public static String getRules() {
        return enabled ? rules : "";
    }
}