package io.github.philipvella.vellaweather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

/**
 * The full forecast for the forecast screen, from the same Open-Meteo model as the line on Home: the weather now,
 * the next 24 hours and the next seven days. {@link #fetchJson} blocks; {@link #parse} turns the reply (fresh or
 * saved) into what the screen shows.
 */
final class Forecast {
    /** Hours shown in the hourly row, one every {@link #HOUR_STEP} hours. */
    static final int HOURS = 12, HOUR_STEP = 2;

    static final class Hour {
        String time;        // "14:00"
        int temp;
        String icon;
        int rain;           // chance of rain, %
    }

    static final class Day {
        String date;        // "2026-11-01"
        int max, min;
        String icon;
        String sky;
        int rain;
        String sunrise, sunset;   // "06:55"
        int wind;           // m/s
    }

    String nowTime;         // "2026-11-01T10:30"
    int temp, feels, humidity, wind;
    String icon, sky;
    Hour[] hours;
    Day[] days;

    static String fetchJson(double latitude, double longitude) throws IOException, JSONException {
        String where = String.format(Locale.ROOT, "latitude=%.4f&longitude=%.4f", latitude, longitude);
        return Now.get("https://api.open-meteo.com/v1/forecast?" + where + "&models=icon_seamless"
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,is_day,wind_speed_10m"
                + "&hourly=temperature_2m,relative_humidity_2m,weather_code,precipitation_probability,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,"
                + "wind_speed_10m_max&wind_speed_unit=ms&timezone=auto&forecast_days=7").toString();
    }

    static Forecast parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        Forecast f = new Forecast();
        JSONObject c = root.getJSONObject("current");
        f.nowTime = c.getString("time");
        double t = c.getDouble("temperature_2m"), rh = c.getDouble("relative_humidity_2m");
        int code = Now.snowIfCold(c.getInt("weather_code"), t, rh);
        boolean day = c.getInt("is_day") == 1;
        f.temp = (int) Math.round(t);
        f.feels = (int) Math.round(c.optDouble("apparent_temperature", t));
        f.humidity = (int) Math.round(rh);
        f.wind = (int) Math.round(c.optDouble("wind_speed_10m", 0));
        f.icon = Now.icon(code, day);
        f.sky = Now.sky(code, day);

        JSONObject h = root.getJSONObject("hourly");
        JSONArray times = h.getJSONArray("time");
        String[] all = new String[times.length()];
        for (int i = 0; i < all.length; i++) all[i] = times.getString(i);
        int first = firstHour(all, f.nowTime);
        int n = Math.max(0, Math.min(HOURS, (all.length - first + HOUR_STEP - 1) / HOUR_STEP));
        f.hours = new Hour[n];
        JSONArray ht = h.getJSONArray("temperature_2m"), hh = h.getJSONArray("relative_humidity_2m"),
                hc = h.getJSONArray("weather_code"), hp = h.optJSONArray("precipitation_probability"),
                hd = h.getJSONArray("is_day");
        for (int k = 0; k < n; k++) {
            int i = first + k * HOUR_STEP;
            Hour hour = new Hour();
            hour.time = clock(all[i]);
            double ti = ht.getDouble(i);
            hour.temp = (int) Math.round(ti);
            hour.icon = Now.icon(Now.snowIfCold(hc.getInt(i), ti, hh.optDouble(i, 100)), hd.getInt(i) == 1);
            hour.rain = hp == null ? 0 : hp.optInt(i, 0);
            f.hours[k] = hour;
        }

        JSONObject d = root.getJSONObject("daily");
        JSONArray dt = d.getJSONArray("time"), dc = d.getJSONArray("weather_code"),
                dmax = d.getJSONArray("temperature_2m_max"), dmin = d.getJSONArray("temperature_2m_min"),
                dp = d.optJSONArray("precipitation_probability_max"), sr = d.getJSONArray("sunrise"),
                ss = d.getJSONArray("sunset"), dw = d.optJSONArray("wind_speed_10m_max");
        f.days = new Day[dt.length()];
        for (int i = 0; i < f.days.length; i++) {
            Day one = new Day();
            one.date = dt.getString(i);
            double max = dmax.getDouble(i);
            one.max = (int) Math.round(max);
            one.min = (int) Math.round(dmin.getDouble(i));
            // No daily humidity: snow only when even the day's high is at freezing (the wet bulb at 100%).
            int dcode = Now.snowIfCold(dc.getInt(i), max, 100);
            one.icon = Now.icon(dcode, true);
            one.sky = Now.sky(dcode, true);
            one.rain = dp == null ? 0 : dp.optInt(i, 0);
            one.sunrise = clock(sr.getString(i));
            one.sunset = clock(ss.getString(i));
            one.wind = dw == null ? 0 : (int) Math.round(dw.optDouble(i, 0));
            f.days[i] = one;
        }
        return f;
    }

    /**
     * The first hourly slot to show: the hour now is in ("10:30" starts at "10:00"). The times are Open-Meteo's local
     * ISO times ("2026-11-01T10:00"), which sort as text.
     */
    static int firstHour(String[] times, String now) {
        String hour = now.length() >= 13 ? now.substring(0, 13) + ":00" : now;
        for (int i = 0; i < times.length; i++) if (times[i].compareTo(hour) >= 0) return i;
        return times.length;
    }

    /** "2026-11-01T06:55" to "06:55". */
    static String clock(String iso) {
        int t = iso.indexOf('T');
        return t < 0 ? iso : iso.substring(t + 1, Math.min(iso.length(), t + 6));
    }

    /** "Light rain": the words for a code, with a capital. */
    static String capital(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
