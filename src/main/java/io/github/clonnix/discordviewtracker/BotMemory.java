package io.github.clonnix.discordviewtracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class BotMemory {

    private static final Map<String, JSONArray> channelMemory = new HashMap<>();

    public static void add(String channel, String user, String role, String content) {
        String key = key(channel, user);
        channelMemory.putIfAbsent(key, new JSONArray());
        JSONArray mem = channelMemory.get(key);

        mem.put(new JSONObject()
                .put("role", role)
                .put("content", content));

        while (mem.length() > Config.MEMORY_LIMIT)
            mem.remove(0);

        save(key, mem);
    }

    public static JSONArray get(String channel, String user) {
        return channelMemory.getOrDefault(key(channel, user), new JSONArray());
    }

    /** Returns the username of the first known user in this channel mentioned in the prompt. */
    public static String detectMentionedUser(String channel, String prompt, String asker) {
        String lower = prompt.toLowerCase();
        String prefix = channel + ":";
        for (String key : channelMemory.keySet()) {
            if (!key.startsWith(prefix)) continue;
            String candidate = key.substring(prefix.length());
            if (candidate.equals(asker)) continue;
            if (candidate.length() < 4) continue;
            if (lower.contains(candidate)) return candidate;
        }
        return null;
    }

    public static JSONArray getByKey(String fullKey) {
        return channelMemory.getOrDefault(fullKey, new JSONArray());
    }

    public static void loadAll() {
        File dir = new File(Config.MEMORY_DIR);
        if (!dir.exists()) {
            System.out.println("No memory directory found, starting fresh.");
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File f : files) {
            try {
                String filename = f.getName().replace(".json", "");
                int sep = filename.indexOf("_");
                if (sep == -1) continue;
                String key = filename.substring(0, sep) + ":" + filename.substring(sep + 1);

                String content = new String(Files.readAllBytes(Paths.get(f.getPath())));
                JSONArray mem = new JSONArray(content);
                channelMemory.put(key, mem);
                System.out.println("Loaded memory: " + key + " (" + mem.length() + " messages)");
            } catch (Exception e) {
                System.err.println("Failed to load memory file " + f.getName() + ": " + e.getMessage());
            }
        }
    }

    // ---- private ----

    private static String key(String channel, String user) {
        return channel + ":" + user.toLowerCase();
    }

    private static void save(String key, JSONArray mem) {
        try {
            File dir = new File(Config.MEMORY_DIR);
            if (!dir.exists()) dir.mkdirs();

            String filename = Config.MEMORY_DIR + "/" + key.replace(":", "_") + ".json";
            try (FileWriter fw = new FileWriter(filename)) {
                fw.write(mem.toString(2));
            }
        } catch (Exception e) {
            System.err.println("Failed to save memory for " + key + ": " + e.getMessage());
        }
    }
}