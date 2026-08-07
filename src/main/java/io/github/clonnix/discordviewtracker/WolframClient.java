package io.github.clonnix.discordviewtracker;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class WolframClient {

    private static final Set<String> ALLOW_PODS = new HashSet<>(Arrays.asList(
            "Result", "Solution", "Solutions",
            "Definite integral", "Indefinite integral",
            "Derivative", "Eigenvalues", "Eigenvectors",
            "Determinant", "Limit", "Sum", "Value"
    ));

    public static String compute(String expression) {
        try {
            HttpUrl url = new HttpUrl.Builder()
                    .scheme("https")
                    .host("api.wolframalpha.com")
                    .addPathSegment("v2")
                    .addPathSegment("query")
                    .addQueryParameter("input", expression)
                    .addQueryParameter("appid", Config.WOLFRAM_APP_ID)
                    .addQueryParameter("format", "plaintext")
                    .addQueryParameter("output", "JSON")
                    .addQueryParameter("units", "metric")
                    .build();

            String exprLower = expression.toLowerCase();
            boolean isDefinite   = exprLower.contains(" from ") || exprLower.contains("integral_") || exprLower.contains("definite");
            boolean isIndefinite = exprLower.contains("indefinite");

            Request request = new Request.Builder().url(url).build();

            try (Response res = Config.HTTP_CLIENT.newCall(request).execute()) {
                if (res.body() == null) return "math error";

                JSONObject json = new JSONObject(res.body().string());
                JSONObject queryresult = json.optJSONObject("queryresult");

                if (queryresult == null || !queryresult.optBoolean("success", false))
                    return "no answer";

                JSONArray pods = queryresult.optJSONArray("pods");
                if (pods == null) return "no answer";

                StringBuilder sb = new StringBuilder();

                for (int i = 0; i < pods.length(); i++) {
                    JSONObject pod = pods.getJSONObject(i);
                    String title = pod.optString("title", "");

                    if (title.equals("Definite integral") && isIndefinite) continue;
                    if (title.equals("Indefinite integral") && isDefinite) continue;
                    if (!ALLOW_PODS.contains(title)) continue;

                    JSONArray subpods = pod.optJSONArray("subpods");
                    if (subpods == null) continue;

                    for (int j = 0; j < subpods.length(); j++) {
                        String text = subpods.getJSONObject(j)
                                .optString("plaintext", "").trim();
                        if (text.isEmpty()) continue;
                        if (sb.length() > 0) sb.append(" | ");
                        sb.append(title).append(": ").append(text);
                        if (sb.length() >= Config.MAX_MATH_RESPONSE_CHARS) break;
                    }

                    if (sb.length() >= Config.MAX_MATH_RESPONSE_CHARS) break;
                }

                return sb.length() > 0
                        ? sb.toString().replaceAll("\\s*\\(assuming[^)]*\\)", "").trim()
                        : "no answer";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "math error";
        }
    }
}