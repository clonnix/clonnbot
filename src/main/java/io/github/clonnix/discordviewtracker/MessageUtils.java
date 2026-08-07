package io.github.clonnix.discordviewtracker;

import com.github.twitch4j.TwitchClient;

public class MessageUtils {

    public static void send(TwitchClient client, String channel, String user, String msg) {
        if (msg == null) msg = "idk";

        msg = msg.replaceAll("\\s+", " ").trim();

        String prefix = "@" + user + " ";
        int maxLen    = 500 - prefix.length();
        if (msg.length() > maxLen)
            msg = msg.substring(0, maxLen);

        client.getChat().sendMessage(channel, prefix + msg);
    }

    public static String trimToLength(String text, int max) {
        if (text == null) return "idk";
        if (text.length() <= max) return text;
        int cut = text.lastIndexOf(' ', max);
        return (cut > 0 ? text.substring(0, cut) : text.substring(0, max)) + "...";
    }

    public static String clean(String text) {
        return text.replaceAll("[\\r\\n]+", " ").trim();
    }

    public static String normalizeLocation(String loc) {
        if (loc == null) return "Unknown";
        return loc.replace("  ", " ").trim();
    }
}