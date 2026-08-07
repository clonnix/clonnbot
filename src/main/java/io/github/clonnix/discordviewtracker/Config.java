package io.github.clonnix.discordviewtracker;

import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.OkHttpClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class Config {

    // Path to the .env file. Assumes the app is run from the project root
    // (same directory the .env file lives in) — same assumption Dotenv's
    // default load() already makes.
    private static final File ENV_FILE = new File(".env");

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMalformed()
            .ignoreIfMissing()
            .load();

    private static String env(String key) {
        String value = dotenv.get(key);
        return value != null ? value : System.getenv(key);
    }

    public static final String CLIENT_ID      = env("TWITCH_CLIENT_ID");
    public static final String CLIENT_SECRET  = env("TWITCH_CLIENT_SECRET");
    // Not final anymore — updated in-memory whenever we persist a refresh,
    // so the rest of the running app always sees the latest value.
    public static volatile String ACCESS_TOKEN   = env("TWITCH_ACCESS_TOKEN");
    public static volatile String REFRESH_TOKEN  = env("TWITCH_REFRESH_TOKEN");
    public static final String OPENAI_API_KEY = env("OPENAI_API_KEY");
    public static final String TAVILY_API_KEY = env("TAVILY_API_KEY");
    public static final String WOLFRAM_APP_ID = env("WOLFRAM_APP_ID");
    public static final String BOT_USERNAME   = env("BOT_USERNAME");

    public static final long COOLDOWN_MS = 30_000;

    public static final int MEMORY_LIMIT          = 20;
    public static final String MEMORY_DIR         = "memory";
    public static final int MAX_RESPONSE_CHARS    = 100;
    public static final int MAX_MATH_RESPONSE_CHARS = 500;

    public static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(60))
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(30))
            .build();

    public static void validate() {
        if (CLIENT_ID == null || CLIENT_SECRET == null ||
                ACCESS_TOKEN == null || REFRESH_TOKEN == null ||
                OPENAI_API_KEY == null || TAVILY_API_KEY == null ||
                WOLFRAM_APP_ID == null || BOT_USERNAME == null) {
            throw new RuntimeException("Missing env variables");
        }
    }

    /**
     * Persists a refreshed access/refresh token pair to the .env file so a
     * restart picks up the latest tokens instead of stale ones. Also updates
     * the in-memory ACCESS_TOKEN/REFRESH_TOKEN fields immediately.
     *
     * Only the TWITCH_ACCESS_TOKEN and TWITCH_REFRESH_TOKEN lines are
     * touched — every other line in .env is preserved as-is. If either key
     * doesn't already exist in the file, it's appended.
     *
     * Safe to call from any thread; synchronized so concurrent refreshes
     * can't interleave writes and corrupt the file.
     */
    public static synchronized void saveTokens(String newAccessToken, String newRefreshToken) {
        ACCESS_TOKEN = newAccessToken;
        REFRESH_TOKEN = newRefreshToken;

        try {
            List<String> lines = new ArrayList<>();
            if (ENV_FILE.exists()) {
                lines.addAll(Files.readAllLines(ENV_FILE.toPath(), StandardCharsets.UTF_8));
            }

            boolean sawAccess = false;
            boolean sawRefresh = false;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith("TWITCH_ACCESS_TOKEN=")) {
                    lines.set(i, "TWITCH_ACCESS_TOKEN=" + newAccessToken);
                    sawAccess = true;
                } else if (line.startsWith("TWITCH_REFRESH_TOKEN=")) {
                    lines.set(i, "TWITCH_REFRESH_TOKEN=" + newRefreshToken);
                    sawRefresh = true;
                }
            }

            if (!sawAccess)  lines.add("TWITCH_ACCESS_TOKEN=" + newAccessToken);
            if (!sawRefresh) lines.add("TWITCH_REFRESH_TOKEN=" + newRefreshToken);

            // Write to a temp file first, then atomically replace — avoids
            // leaving a half-written/corrupt .env if the process dies mid-write.
            File parent = ENV_FILE.getAbsoluteFile().getParentFile();
            File tmp = new File(parent, ENV_FILE.getName() + ".tmp");
            Files.write(tmp.toPath(), lines, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), ENV_FILE.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            System.out.println("[Config] Saved refreshed Twitch tokens to .env");
        } catch (IOException e) {
            System.err.println("[Config] Failed to persist refreshed tokens to .env: " + e.getMessage());
            e.printStackTrace();
        }
    }
}