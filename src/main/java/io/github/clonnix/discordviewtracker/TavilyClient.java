package io.github.clonnix.discordviewtracker;

import okhttp3.*;
import org.json.JSONObject;

public class TavilyClient {

    public static String search(String query) {
        try {
            JSONObject body = new JSONObject()
                    .put("api_key", Config.TAVILY_API_KEY)
                    .put("query", query)
                    .put("search_depth", "basic")
                    .put("include_answer", true);

            Request request = new Request.Builder()
                    .url("https://api.tavily.com/search")
                    .post(RequestBody.create(body.toString(),
                            MediaType.get("application/json")))
                    .build();

            try (Response response = Config.HTTP_CLIENT.newCall(request).execute()) {
                JSONObject json = new JSONObject(response.body().string());
                return json.optString("answer", "no results");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "search failed";
        }
    }
}