package io.github.clonnix.discordviewtracker;

import okhttp3.*;
import org.json.JSONObject;

/**
 * Forces a fresh Twitch access token on startup instead of waiting for
 * twitch4j to lazily refresh once the stored token happens to fail.
 *
 * twitch4j's built-in refresh only kicks in reactively (after a call gets
 * rejected as unauthorized), so if the token in .env is already stale when
 * the process starts, the bot can sit there failing calls until that first
 * refresh happens. Doing one refresh_token exchange up front guarantees we
 * always start with a valid access token.
 */
public class TwitchAuth {

    private static final String TOKEN_URL = "https://id.twitch.tv/oauth2/token";

    /**
     * Exchanges the stored refresh token for a new access/refresh token pair
     * and persists them via Config.saveTokens. Returns true if the refresh
     * succeeded, false otherwise (in which case the existing tokens in
     * Config are left untouched and the caller should fall back to them).
     */
    public static boolean refreshOnStartup() {
        try {
            RequestBody body = new FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", Config.REFRESH_TOKEN)
                    .add("client_id", Config.CLIENT_ID)
                    .add("client_secret", Config.CLIENT_SECRET)
                    .build();

            Request request = new Request.Builder()
                    .url(TOKEN_URL)
                    .post(body)
                    .build();

            try (Response response = Config.HTTP_CLIENT.newCall(request).execute()) {
                String raw = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    System.err.println("[TwitchAuth] Startup token refresh failed ("
                            + response.code() + "): " + raw);
                    return false;
                }

                JSONObject json = new JSONObject(raw);
                String newAccess = json.optString("access_token", null);
                String newRefresh = json.optString("refresh_token", null);

                if (newAccess == null || newAccess.isBlank()
                        || newRefresh == null || newRefresh.isBlank()) {
                    System.err.println("[TwitchAuth] Startup token refresh response missing tokens: " + raw);
                    return false;
                }

                Config.saveTokens(newAccess, newRefresh);
                System.out.println("[TwitchAuth] Refreshed Twitch access token on startup");

                // Refreshing never grants new scopes — Twitch just carries over
                // whatever the token was originally authorized with. This only
                // confirms chat:read/chat:edit are present so a missing-scope
                // problem shows up immediately in the logs instead of as a
                // confusing failure later when the bot tries to read/send chat.
                warnIfMissingChatScopes(json);

                return true;
            }

        } catch (Exception e) {
            System.err.println("[TwitchAuth] Startup token refresh threw an exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Twitch's refresh grant carries over whatever scopes the token was
     * originally issued with — you can't add scopes by refreshing, only by
     * re-authorizing through the OAuth consent screen. If chat:read or
     * chat:edit are missing here, the fix is to redo the initial
     * authorization with both scopes and drop the new tokens into .env,
     * not anything this refresh call can do on its own.
     */
    private static void warnIfMissingChatScopes(JSONObject tokenResponse) {
        boolean hasRead = false;
        boolean hasWrite = false;

        org.json.JSONArray scopes = tokenResponse.optJSONArray("scope");
        if (scopes != null) {
            for (int i = 0; i < scopes.length(); i++) {
                String s = scopes.optString(i, "");
                if (s.equalsIgnoreCase("chat:read")) hasRead = true;
                if (s.equalsIgnoreCase("chat:edit")) hasWrite = true;
            }
        }

        if (!hasRead || !hasWrite) {
            System.err.println("[TwitchAuth] WARNING: refreshed token is missing required scope(s): "
                    + (!hasRead ? "chat:read " : "")
                    + (!hasWrite ? "chat:edit " : "")
                    + "— re-authorize with both scopes and update .env, refreshing alone can't add them.");
        }
    }
}
