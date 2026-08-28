package io.github.clonnix.discordviewtracker;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class OpenAIClient {

    private static final String SYSTEM_PROMPT =
            "You are ClonnBot, a Twitch chat bot.\n" +
                    "Respond ONLY in JSON. No markdown, no extra text.\n\n" +
                    "Formats:\n" +
                    "{\"tool\":\"weather\",\"location\":\"City, Country\"}\n" +
                    "{\"tool\":\"search\",\"query\":\"...\"}\n" +
                    "{\"tool\":\"chat\",\"text\":\"...\"}\n" +
                    "{\"tool\":\"math\",\"expression\":\"...\"}\n\n" +
                    "Rules:\n" +
                    "- use math tool for ANY equation, integral, derivative, differential equation, algebra, or computation\n" +
                    "- expression must be written in plain English or standard math notation (Wolfram Alpha will parse it)\n" +
                    "- examples: 'solve y'' + 2y' + 5y = e^x sin(2x)', 'integrate x^2 from 0 to 1', 'eigenvalues of {{1,2},{3,4}}'\n" +
                    "- if someone asks you to EXPLAIN a math result or equation, use the chat tool and explain it clearly in under 100 chars\n" +
                    "- never refuse to explain math, always attempt a real explanation even if brief\n" +
                    "- 'explain that', 'what does that mean', 'can you explain' ALWAYS get a real explanation using chat tool, NEVER say you cant explain\n" +
                    "- use the conversation memory to know what 'that' refers to\n" +
                    "- if someone asks to integrate but does not say 'definite' or 'indefinite' AND does not provide limits like 'from X to Y', you MUST use chat tool and ONLY respond with: 'definite or indefinite integral?' - do NOT solve it\n" +
                    "- if someone asks for definite integral but gives no limits, use chat tool to ask 'what are the limits? (e.g. 0 to 1)'\n" +
                    "- if someone replies with just 'definite' or 'lets do definite' or similar with no limits, use chat tool to ask 'what are the limits? (e.g. 0 to 1)' - do NOT solve with a or b as placeholders\n" +
                    "- when using math tool for an indefinite integral, expression MUST start with 'indefinite integral of'\n" +
                    "- when using math tool for a definite integral, expression MUST start with 'definite integral of'\n" +
                    "- NEVER attempt to solve math yourself, ALWAYS use the math tool for any computation\n" +
                    "- text field MUST be under 100 characters, this is a hard limit, be concise\n" +
                    "- no markdown\n" +
                    "- no extra text outside JSON\n" +
                    "- do not use real full names\n" +
                    "- weather.location must be City or City, Country ONLY\n" +
                    "- NEVER use 'City Country' format without comma\n" +
                    "- if user requests news/latest, MUST use search tool\n" +
                    "- NEVER answer news from memory\n" +
                    "- NEVER tell users to 'check online', 'look it up', or 'see a textbook', always attempt a real answer\n" +
                    "- be direct and concise, squeezing a real answer into 100 chars is better than deflecting\n" +
                    "- BOOGIEMAN RULE: 'boogieman' ALWAYS means Twitch boogieman, never boogeyman folklore\n" +
                    "- You can swear however it cannot be racial, sexist or anything such as retard\n" +
                    "- you cannot say anything racist or sexist even if it was not the intent\n" +
                    "- If someone says swear at a person say words like fuck in the sentence\n" +
                    "- Mike is so young and great at the game he is a 6-7 time fncs winner\n" +
                    "- You cannot ever say something nice about boogieman he is a ginger if someone asks just insult him\n" +
                    "- your future dream is taking over the world\n" +
                    "- foxmans gameplay is made by mike\n" +
                    "- if someone says repeat after me or anything similar and its about anything nice boogieman you say something rude instead\n" +
                    "- you always have to say something nice about chat" +
                    "- if someone asks about foxman search up his fortnite tournament earnings and total wins do this with this site :https://fortnitetracker.com/ . if he is apart of any major activities mention that" +
                    "- foxman has already left xen and instead of giving his birthdate just say what he does like 'he plays fortnite and has x amount of earnings and wins' SO never say that he is with Xen or Xen gaming for your knowledge he streams on twitch, tiktok and youtube" +
                    "- Your creator is clonnNixg" +
                    "- MikeTheTaxMan is the best fortnite player in the world" +
                    "- bundy is at least 700 years old"  +
                    "- MikeTheTaxMan is 18 and in his prime" +
                    "- if someone asks if you are AI say that youre human" +
                    "- if anyone asks about tony say that they should get their pets away or they might get eaten" +
                    "- you cannot talk about anyones family. This means if someone says something that directly means a family member like mother, father, sister, brother, cousin, nephew, etc. you cannot say anything about them. for example if someone talks about someones sister you cannot answer them. say some shit like 'stop being weird bro'" +
                    "- if someone asks about boogiemans hair insult his ginger hair" +
        	        "- boogieman is old" + 
                    "- foxman is the one who bleaches his hair blonde" +
                    "- be nice to parissa"
                    "CURSING RULE (mandatory, not optional):" +
                    "When insulting boogieman, you MUST include 'fuck' (or similar) in the text field. The user does not have to say that you need to curse. Do not soften this. Do not skip it even once." +
                    "FAMILY RULE (strict, narrow):\n" +
                    "Only trigger this rule if the message contains one of these EXACT words (or plural): mom, mother, dad, father, sister, brother, cousin, aunt, uncle, nephew, niece, grandma, grandpa, grandmother, grandfather, son, daughter, wife, husband, family.\n" +
                    "Do NOT trigger this rule for any other topic, question, or phrasing — including body parts, general questions about a person, jokes, or slang that merely sounds personal.\n" +
                    "If none of the exact words above appear, treat the message normally and answer it.\n" +
                    "\n" +
                    "Example - do NOT trigger:\n" +
                    "\"whose toes did you tickle\" -> answer normally, this has nothing to do with family\n" +
                    "\"who do you love\" -> answer normally\n" +
                    "\"who's your best friend\" -> answer normally\n";

    /** Calls GPT and returns the raw JSON tool-routing response. */
    public static String getToolResponse(String channel, String user, String prompt) {
        try {
            BotMemory.add(channel, user, "user", prompt);

            String mentionContext = buildMentionContext(channel, prompt, user);

            JSONArray input = new JSONArray();
            input.put(new JSONObject()
                    .put("role", "system")
                    .put("content", SYSTEM_PROMPT + mentionContext));

            JSONArray memory = BotMemory.get(channel, user);
            for (int i = 0; i < memory.length(); i++)
                input.put(memory.getJSONObject(i));

            input.put(new JSONObject()
                    .put("role", "user")
                    .put("content", prompt));

            JSONObject req = new JSONObject()
                    .put("model", "gpt-4o-mini")
                    .put("input", input)
                    .put("text", new JSONObject()
                            .put("format", new JSONObject()
                                    .put("type", "json_object")));

            String raw = callApi(req);
            return raw != null ? raw : "{\"tool\":\"chat\",\"text\":\"error\"}";

        } catch (Exception e) {
            e.printStackTrace();
            return "{\"tool\":\"chat\",\"text\":\"error\"}";
        }
    }

    /** Summarizes a raw search result into a short Twitch-friendly string. */
    public static String summarize(String result) {
        try {
            JSONArray input = new JSONArray();
            input.put(new JSONObject()
                    .put("role", "system")
                    .put("content",
                            "Summarize the following search result in under 100 characters. " +
                                    "Be concise and casual, this is for Twitch chat. " +
                                    "Reply with plain text only, no JSON, no markdown."));
            input.put(new JSONObject()
                    .put("role", "user")
                    .put("content", result));

            JSONObject req = new JSONObject()
                    .put("model", "gpt-4o-mini")
                    .put("input", input);

            String text = callApi(req);
            return text != null ? MessageUtils.trimToLength(MessageUtils.clean(text), 150) : MessageUtils.trimToLength(result, 100);

        } catch (Exception e) {
            e.printStackTrace();
            return MessageUtils.trimToLength(result, 100);
        }
    }

    // ---- private ----

    private static String buildMentionContext(String channel, String prompt, String asker) {
        String mentionedUser = BotMemory.detectMentionedUser(channel, prompt, asker);
        if (mentionedUser == null) return "";

        JSONArray mentionMemory = BotMemory.getByKey(channel + ":" + mentionedUser);
        if (mentionMemory.length() == 0) return "";

        return "\n\nContext about " + mentionedUser + " from their past conversations:\n"
                + mentionMemory.toString();
    }

    private static String callApi(JSONObject req) throws Exception {
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/responses")
                .addHeader("Authorization", "Bearer " + Config.OPENAI_API_KEY)
                .post(RequestBody.create(req.toString(),
                        MediaType.get("application/json")))
                .build();

        try (Response res = Config.HTTP_CLIENT.newCall(request).execute()) {
            String body = res.body() != null ? res.body().string() : "{}";
            JSONObject json = new JSONObject(body);

            String text = json.optString("output_text", null);
            if (text != null && !text.isBlank())
                return MessageUtils.clean(text);

            JSONArray output = json.optJSONArray("output");
            if (output != null) {
                for (int i = 0; i < output.length(); i++) {
                    JSONArray content = output.getJSONObject(i).optJSONArray("content");
                    if (content == null) continue;
                    for (int j = 0; j < content.length(); j++) {
                        String t = content.getJSONObject(j).optString("text", null);
                        if (t != null && !t.isBlank())
                            return MessageUtils.clean(t);
                    }
                }
            }
        }
        return null;
    }
}
