package io.github.clonnix.discordviewtracker;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class WeatherClient {

    public static String get(String location) {
        try {
            HttpUrl geoUrl = new HttpUrl.Builder()
                    .scheme("https")
                    .host("geocoding-api.open-meteo.com")
                    .addPathSegment("v1")
                    .addPathSegment("search")
                    .addQueryParameter("name", location)
                    .addQueryParameter("count", "1")
                    .build();

            Request geoReq = new Request.Builder().url(geoUrl).build();

            try (Response geoRes = Config.HTTP_CLIENT.newCall(geoReq).execute()) {
                JSONObject geoJson = new JSONObject(geoRes.body().string());
                JSONArray results = geoJson.optJSONArray("results");

                if (results == null || results.length() == 0)
                    return "location not found";

                JSONObject place = results.getJSONObject(0);
                double lat  = place.getDouble("latitude");
                double lon  = place.getDouble("longitude");
                String name = place.getString("name");

                HttpUrl weatherUrl = new HttpUrl.Builder()
                        .scheme("https")
                        .host("api.open-meteo.com")
                        .addPathSegment("v1")
                        .addPathSegment("forecast")
                        .addQueryParameter("latitude",        String.valueOf(lat))
                        .addQueryParameter("longitude",       String.valueOf(lon))
                        .addQueryParameter("current_weather", "true")
                        .build();

                Request weatherReq = new Request.Builder().url(weatherUrl).build();

                try (Response wRes = Config.HTTP_CLIENT.newCall(weatherReq).execute()) {
                    JSONObject json    = new JSONObject(wRes.body().string());
                    JSONObject current = json.optJSONObject("current_weather");

                    if (current == null) return "weather unavailable";

                    return String.format(
                            "%s: %.0f°C wind %.0f km/h",
                            name,
                            current.optDouble("temperature"),
                            current.optDouble("windspeed")
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "weather error";
        }
    }
}