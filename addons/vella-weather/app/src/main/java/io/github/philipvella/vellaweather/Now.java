package io.github.philipvella.vellaweather;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The weather now at one place, from Open-Meteo (no key), as one line for Home: "22°C · light rain · wind 10 m/s".
 * The fetch and the rain-to-snow correction come from addons/weather (Forecast). Every call blocks.
 */
final class Now {
    static final long REFRESH_MS = 30 * 60_000L;
    static final long RETRY_MS = 5 * 60_000L;
    /** A line older than this is not shown: yesterday's weather would mislead. */
    static final long STALE_MS = 3 * 60 * 60_000L;
    private static final int TIMEOUT_MS = 8_000;

    private Now() { }

    /** The line, the icon's name, and the line in two parts (the temperature, then the rest), for Home. */
    static final class Reading {
        final String line;
        final String icon;
        final String temp;
        final String summary;

        Reading(String line, String icon, String temp, String summary) {
            this.line = line;
            this.icon = icon;
            this.temp = temp;
            this.summary = summary;
        }
    }

    /** Fetches the weather now and returns the line and its icon. */
    static Reading fetch(double latitude, double longitude) throws IOException, JSONException {
        String where = String.format(Locale.ROOT, "latitude=%.4f&longitude=%.4f", latitude, longitude);
        JSONObject reply = get("https://api.open-meteo.com/v1/forecast?" + where + "&models=icon_seamless"
                + "&current=temperature_2m,relative_humidity_2m,weather_code,is_day,wind_speed_10m"
                + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1"
                + "&wind_speed_unit=ms&timezone=auto");
        JSONObject current = reply.getJSONObject("current");
        JSONObject daily = reply.optJSONObject("daily");
        double temp = current.getDouble("temperature_2m");
        double humidity = current.getDouble("relative_humidity_2m");
        int code = snowIfCold(current.getInt("weather_code"), temp, humidity);
        boolean day = current.getInt("is_day") == 1;
        double wind = current.optDouble("wind_speed_10m", 0);
        String range = "";
        if (daily != null && daily.optJSONArray("temperature_2m_max") != null && daily.optJSONArray("temperature_2m_min") != null) {
            range = range(daily.getJSONArray("temperature_2m_max").optDouble(0, Double.NaN),
                    daily.getJSONArray("temperature_2m_min").optDouble(0, Double.NaN));
        }
        String summary = summary(code, day, wind);
        if (!range.isEmpty()) summary = sky(code, day) + " · " + range + " · wind " + Math.round(wind) + " m/s";
        return new Reading(line(temp, code, day, wind), icon(code, day), temp(temp), summary);
    }

    /**
     * The icon the launcher draws beside the line (docs/modules.md, weather_source): clear-day, clear-night,
     * partly-cloudy-day, partly-cloudy-night, cloudy, fog, drizzle, rain, snow or thunder.
     */
    static String icon(int code, boolean day) {
        if (code <= 1) return day ? "clear-day" : "clear-night";
        if (code == 2) return day ? "partly-cloudy-day" : "partly-cloudy-night";
        if (code == 45 || code == 48) return "fog";
        if (code >= 51 && code <= 57) return "drizzle";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snow";
        if (code >= 95) return "thunder";
        if (code >= 61) return "rain";
        return "cloudy";
    }

    /** Today's "High 14° Low 6°"; empty when either is missing. */
    static String range(double max, double min) {
        if (Double.isNaN(max) || Double.isNaN(min)) return "";
        return "High " + Math.round(max) + "° Low " + Math.round(min) + "°";
    }

    /** "22°C · light rain · wind 10 m/s". */
    static String line(double tempC, int code, boolean day, double windMs) {
        return temp(tempC) + " · " + summary(code, day, windMs);
    }

    /** "22°C". */
    static String temp(double tempC) {
        return Math.round(tempC) + "°C";
    }

    /** "Light rain · wind 10 m/s": the words under the temperature. */
    static String summary(int code, boolean day, double windMs) {
        return sky(code, day) + " · wind " + Math.round(windMs) + " m/s";
    }

    /** Lower-case words for a WMO weather code, as fine as the code goes. */
    static String sky(int code, boolean day) {
        switch (code) {
            case 0: return day ? "sunny" : "clear";
            case 1: return day ? "mostly sunny" : "mostly clear";
            case 2: return "partly cloudy";
            case 3: return "cloudy";
            case 45: case 48: return "fog";
            case 51: case 53: case 55: return "drizzle";
            case 56: case 57: return "freezing drizzle";
            case 61: return "light rain";
            case 63: return "rain";
            case 65: return "heavy rain";
            case 66: case 67: return "freezing rain";
            case 71: return "light snow";
            case 73: return "snow";
            case 75: return "heavy snow";
            case 77: return "snow grains";
            case 80: case 81: case 82: return "showers";
            case 85: case 86: return "snow showers";
            default: return code >= 95 ? "thunderstorm" : "cloudy";
        }
    }

    /** From addons/weather: at a wet-bulb temperature of 1 °C or below, rain and drizzle are shown as snow. */
    static int snowIfCold(int code, double tempC, double humidity) {
        if (wetBulb(tempC, humidity) > 1.0) return code;
        switch (code) {
            case 51: case 53: case 61: case 80: return 71;
            case 55: case 63: case 81: return 73;
            case 65: case 82: return 75;
            default: return code;
        }
    }

    /** Stull (2011), from the air temperature in °C and the relative humidity in %. */
    static double wetBulb(double t, double rh) {
        return t * Math.atan(0.151977 * Math.sqrt(rh + 8.313659)) + Math.atan(t + rh) - Math.atan(rh - 1.676331)
                + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh) - 4.686035;
    }

    static JSONObject get(String url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }
}
