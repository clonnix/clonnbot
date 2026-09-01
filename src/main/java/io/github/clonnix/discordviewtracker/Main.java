package io.github.clonnix.discordviewtracker;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    // Users allowed to manage cooldown settings — edit this list as needed
    private static final Set<String> COOLDOWN_ADMINS = Set.of("clonnnixg", "mikethetaxman");

    // Keyed by "channel:sender" — every user's last-response timestamp, always used.
    private static final Map<String, Long> perPersonCooldownMap = new ConcurrentHashMap<>();
    // Per-user cooldown durations in seconds, set with !cooldown @user <seconds>.
    // A user with no entry here just gets the default cooldown (Config.COOLDOWN_MS).
    private static final Map<String, Integer> userCooldownSeconds = new ConcurrentHashMap<>();

    // Global cooldown is off by default, toggled with !globalcooldown
    private static volatile boolean globalCooldownEnabled = false;

    public static void main(String[] args) {

        startHealthCheckServer();

        Config.validate();
        BotMemory.loadAll();
        BanList.load();
        GoatList.load();

        // Force a token refresh up front so we never start the bot on a
        // stale access token — Config.ACCESS_TOKEN / REFRESH_TOKEN are
        // updated in place (and persisted to .env) if this succeeds. If it
        // fails (e.g. network hiccup), we just fall back to whatever was
        // already loaded from .env and let twitch4j's lazy refresh handle it.
        TwitchAuth.refreshOnStartup();

        Map<String, Object> additional = new HashMap<>();
        additional.put("refresh_token", Config.REFRESH_TOKEN);

        OAuth2Credential credential =
                new OAuth2Credential("twitch", Config.ACCESS_TOKEN, additional);

        TwitchClient twitchClient = TwitchClientBuilder.builder()
                .withEnableChat(true)
                .withChatAccount(credential)
                .withClientId(Config.CLIENT_ID)
                .withClientSecret(Config.CLIENT_SECRET)
                .withEnableHelix(true)
                .build();

        // twitch4j refreshes expired tokens in-memory on the live `credential`
        // object automatically, but never writes them back to disk. Without
        // this, a restart reloads the stale tokens from .env and breaks again.
        // Poll periodically and persist whatever the credential currently holds.
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            String currentAccess = credential.getAccessToken();
            String currentRefresh = credential.getRefreshToken();
            if (currentAccess != null && currentRefresh != null &&
                    (!currentAccess.equals(Config.ACCESS_TOKEN) ||
                            !currentRefresh.equals(Config.REFRESH_TOKEN))) {
                Config.saveTokens(currentAccess, currentRefresh);
            }
        }, 1, 5, TimeUnit.MINUTES);

        // Also save once on shutdown so we don't lose a refresh that happened
        // right before the process exits.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            String currentAccess = credential.getAccessToken();
            String currentRefresh = credential.getRefreshToken();
            if (currentAccess != null && currentRefresh != null) {
                Config.saveTokens(currentAccess, currentRefresh);
            }
        }));

        String[] channels = {"boogieman", "foxman", "tismbean_lizzie", "clonnnixg", "soro1k"};

        for (String c : channels) {
            twitchClient.getChat().joinChannel(c);
            System.out.println("Joined: " + c);
        }

        twitchClient.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
            new Thread(() -> handleMessage(twitchClient, event)).start();
        });

        // Force a full chat disconnect/reconnect every 2 days. twitch4j's IRC
        // websocket has its own heartbeat/auto-reconnect, but long-lived TMI
        // connections can go stale or start dropping messages silently, so
        // this forces a clean cycle on a fixed schedule.
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            System.out.println("[Main] Scheduled Twitch chat reconnect...");
            twitchClient.getChat().disconnect();
            try {
                Thread.sleep(3000); // give the old socket time to fully close
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            twitchClient.getChat().connect();
            for (String c : channels) {
                twitchClient.getChat().joinChannel(c);
            }
            System.out.println("[Main] Rejoined channels after reconnect.");
        }, 2, 2, TimeUnit.DAYS);

        System.out.println("Bot running...");
    }

    // Render's Web Service type expects the app to bind to $PORT and answer
    // HTTP requests, purely as a liveness check — this bot doesn't otherwise
    // serve anything. Spin up a trivial server that just returns 200 OK, so
    // Render's port scanner is satisfied. No new dependency needed: this uses
    // the JDK's own built-in com.sun.net.httpserver.HttpServer.
    private static void startHealthCheckServer() {
        try {
            int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", exchange -> {
                byte[] body = "OK".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            System.out.println("Health check server listening on port " + port);
        } catch (IOException e) {
            // Non-fatal — the bot's actual job (Twitch chat) doesn't depend
            // on this. Log it and keep going rather than crash the process.
            System.err.println("[Main] Failed to start health check server: " + e.getMessage());
        }
    }

    private static void handleMessage(TwitchClient twitchClient, ChannelMessageEvent event) {
        String channel = event.getChannel().getName().toLowerCase();
        String message = event.getMessage();
        String sender  = event.getUser().getName().toLowerCase();

        // Fully ignore banned users — no processing, no cooldown, no response
        if (BanList.isBanned(sender)) return;

        if (!message.toLowerCase().contains("@" + Config.BOT_USERNAME.toLowerCase()))
            return;
        if (message.isEmpty()) return;

        if (message.toLowerCase().startsWith("!watchtime @clonnbot") || message.toLowerCase().startsWith("!followage @clonnbot")){
            Executors.newSingleThreadScheduledExecutor().schedule(() ->
                    MessageUtils.send(twitchClient, channel, sender,
                            "what ya looking for? why did you want to know?"),3, TimeUnit.SECONDS
            );
            return;
        }
        String cleaned = message
                .replaceAll("(?i)@" + Config.BOT_USERNAME, "")
                .trim();

        // !globalcooldown — toggles the per-channel global cooldown on/off
        if (cleaned.toLowerCase().startsWith("!globalcooldown")) {
            if (!COOLDOWN_ADMINS.contains(sender)) return;
            globalCooldownEnabled = !globalCooldownEnabled;
            MessageUtils.send(twitchClient, channel, sender,
                    "global cooldown is now " + (globalCooldownEnabled ? "ON" : "OFF"));
            return;
        }

        // !cooldown @user <seconds> — sets that user's personal cooldown, 0 clears it
        if (cleaned.toLowerCase().startsWith("!cooldown")) {
            if (!COOLDOWN_ADMINS.contains(sender)) return;
            String[] parts = cleaned.trim().split("\\s+");
            if (parts.length < 3) {
                MessageUtils.send(twitchClient, channel, sender,
                        "usage: !cooldown @user <seconds>");
                return;
            }
            String target = parts[1].toLowerCase().replaceAll("^@", "");
            if (target.isEmpty()) {
                MessageUtils.send(twitchClient, channel, sender,
                        "usage: !cooldown @user <seconds>");
                return;
            }
            try {
                int seconds = Math.max(0, Integer.parseInt(parts[2]));
                if (seconds == 0) {
                    userCooldownSeconds.remove(target);
                    MessageUtils.send(twitchClient, channel, sender,
                            "@" + target + "'s cooldown has been cleared");
                } else {
                    userCooldownSeconds.put(target, seconds);
                    MessageUtils.send(twitchClient, channel, sender,
                            "@" + target + "'s cooldown set to " + seconds + "s");
                }
            } catch (NumberFormatException e) {
                MessageUtils.send(twitchClient, channel, sender,
                        "usage: !cooldown @user <seconds>");
            }
            return;
        }

        long now = System.currentTimeMillis();
        String personKey = channel + ":" + sender;

        // Everyone gets the default cooldown unless they have a personal override.
        Integer personalSeconds = userCooldownSeconds.get(sender);
        long personalMs = (personalSeconds != null && personalSeconds > 0)
                ? personalSeconds * 1000L
                : Config.COOLDOWN_MS;

        // Global cooldown acts as a floor: it overwrites personal cooldowns that
        // are lower than it, but never shortens a personal cooldown that's
        // already stricter. Disabling it lets the personal cooldown apply again.
        long effectiveMs = globalCooldownEnabled
                ? Math.max(personalMs, Config.COOLDOWN_MS)
                : personalMs;

        // Atomic check-and-set: without this, two messages from the same
        // person processed on concurrent threads could both read the old
        // timestamp before either writes the new one, letting both through
        // and making the cooldown feel shorter than it actually is.
        final long finalEffectiveMs = effectiveMs;
        Long previous = perPersonCooldownMap.compute(personKey, (k, last) -> {
            if (last == null || now - last >= finalEffectiveMs) return now;
            return last;
        });
        if (!previous.equals(now)) return;

        // !ban command — only clonnnixg can use it
        if (cleaned.toLowerCase().startsWith("!ban ")) {
            if (!sender.equals("clonnnixg")) return;
            String target = cleaned.substring(5).trim().toLowerCase().replaceAll("^@", "");
            if (target.isEmpty()) return;
            if (BanList.isBanned(target)) {
                MessageUtils.send(twitchClient, channel, sender,
                        "@"+ target + " has already been banned cuz he was stoopid");
                return;
            }
            BanList.ban(target);
            MessageUtils.send(twitchClient, channel, sender,
                    "@"+ target + " shall not use this bot ever again");
            return;
        }
        if (cleaned.toLowerCase().startsWith("!unban")) {
            if (!sender.equals("clonnnixg")) return;
            String target = cleaned.substring(7).trim().toLowerCase().replaceAll("^@", "");
            if (target.isEmpty()) return;
            if (!BanList.isBanned(target)) {

                MessageUtils.send(twitchClient, channel, sender,
                        "@"+ target + " is not banned right now");
                return;
            }
            BanList.unban(target);
            MessageUtils.send(twitchClient, channel, sender,
                    "@"+ target + " he has been forgiven");
            return;
        }
        if (cleaned.toLowerCase().startsWith("!goat")){
            MessageUtils.send(twitchClient, channel, sender,
                    GoatList.get());
            return;
        }
        if (cleaned.toLowerCase().startsWith("!editgoat")){
            if (!sender.equals("clonnnixg") && !sender.equals("mikethetaxman")) return;
            String newText = cleaned.substring(10).trim();
            if (newText.isEmpty()) return;
            GoatList.set(newText);
            MessageUtils.send(twitchClient, channel, sender,
                    "goat message has been updated!");
            return;
        }
        System.out.println("[" + channel + "] " + sender + ": " + cleaned);

        try {
            String aiRaw = OpenAIClient.getToolResponse(channel, sender, cleaned);
            System.out.println("AI RAW: " + aiRaw);

            JSONObject tool = new JSONObject(aiRaw);
            String type = tool.optString("tool", "chat");
            String response;

            switch (type) {

                case "weather":
                    response = WeatherClient.get(
                            MessageUtils.normalizeLocation(tool.optString("location", cleaned)));
                    break;

                case "search":
                    String rawResult = TavilyClient.search(tool.optString("query", cleaned));
                    response = OpenAIClient.summarize(rawResult);
                    break;

                case "math":
                    String wolframResult = WolframClient.compute(tool.optString("expression", cleaned));
                    if (wolframResult.equals("math error") || wolframResult.equals("no answer") || wolframResult.isBlank()) {
                        String fallback = TavilyClient.search(tool.optString("expression", cleaned));
                        response = OpenAIClient.summarize(fallback);
                    } else {
                        response = wolframResult;
                    }
                    response = MessageUtils.trimToLength(response, Config.MAX_MATH_RESPONSE_CHARS);
                    BotMemory.add(channel, sender, "assistant", response);
                    MessageUtils.send(twitchClient, channel, sender, response);
                    return; // early return — math has its own char limit

                default:
                    response = tool.optString("text", "idk tbh");
            }

            response = MessageUtils.trimToLength(response, Config.MAX_RESPONSE_CHARS);
            BotMemory.add(channel, sender, "assistant", response);
            MessageUtils.send(twitchClient, channel, sender, response);

        } catch (Exception e) {
            e.printStackTrace();
            MessageUtils.send(twitchClient, channel, sender, "bot broke 💀");
        }
    }
}
