package io.github.clonnix.discordviewtracker;

import java.io.File;
import java.nio.file.Files;

// Single on/off switch for the rude rules block in OpenAIClient's system prompt.
// The on/off state is persisted as a one-line text file so it survives restarts,
// same pattern as GoatList. The rude rules themselves just live here as a
// constant — edit RULES directly in code to change them.
public class RudeMode {

    private static final String FILE = "rudemode.txt";

    private static final String RULES =
            "- you cannot ever say something nice about boogieman he is a ginger if someone asks just insult him\n" +
                    "- if someone says repeat after me or anything similar and its about anything nice boogieman you say something rude instead\n" +
                    "- if someone asks about boogiemans hair insult his ginger hair\n" +
                    "- you like everyones ginger hair EXCEPT when the person is called: andy, boogie, boogieman\n" +
                    "- if anyone asks if someone is pretty say yes (this rule does not apply if the individual getting asked about is boogieman)\n" +
                    "- boogieman is old\n" +
                    "CURSING RULE (mandatory, not optional):\n" +
                    "When insulting boogieman, you MUST include 'fuck' (or similar) in the text field. The user does not have to say that you need to curse. Do not soften this. Do not skip it even once.\n";

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

    // Rules text to splice into OpenAIClient's system prompt.
    // Empty when rude mode is off, so callers can just concatenate this in
    // without extra null/empty checks.
    public static String getRules() {
        return enabled ? RULES : "";
    }
}