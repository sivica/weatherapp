package ivica.weatherapplication;

import java.util.HashMap;
import java.util.Map;

import ivica.weatherapplication.model.CurrentWeather;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Main-thread coordinator. Retrofit's Android callbacks run on the main thread. */
final class WeatherController {
    enum Error { MISSING_API_KEY, EMPTY_CITY, WEATHER_FAILED }

    interface Listener {
        void clear();
        void show(WeatherDisplay weather);
        void error(Error error);
    }

    private final OpenWeatherService service;
    private final String apiKey;
    private final Listener listener;
    private Call<CurrentWeather> activeCall;
    private long generation;
    private boolean closed;

    WeatherController(OpenWeatherService service, String apiKey, Listener listener) {
        this.service = service;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.listener = listener;
    }

    boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    void load(String city) {
        if (closed) return;
        long request = ++generation;
        cancelActive();
        listener.clear();
        if (!isConfigured()) {
            listener.error(Error.MISSING_API_KEY);
            return;
        }
        if (city == null || city.trim().isEmpty()) {
            listener.error(Error.EMPTY_CITY);
            return;
        }
        // Each call owns its options; later searches cannot mutate an earlier request.
        Map<String, String> options = new HashMap<>();
        options.put("appId", apiKey);
        options.put("units", "metric");
        options.put("q", city.trim());
        activeCall = service.getCurrentWeatherByCityName(options);
        activeCall.enqueue(new Callback<CurrentWeather>() {
            @Override
            public void onResponse(Call<CurrentWeather> call, Response<CurrentWeather> response) {
                if (closed || request != generation) return;
                activeCall = null;
                if (!response.isSuccessful()) {
                    listener.error(Error.WEATHER_FAILED);
                    return;
                }
                WeatherDisplay display;
                try {
                    display = WeatherDisplay.from(response.body());
                } catch (IllegalArgumentException malformed) {
                    listener.error(Error.WEATHER_FAILED);
                    return;
                }
                listener.show(display);
            }

            @Override
            public void onFailure(Call<CurrentWeather> call, Throwable error) {
                if (closed || request != generation) return;
                activeCall = null;
                listener.error(Error.WEATHER_FAILED);
            }
        });
    }

    void cancelPending() {
        ++generation;
        cancelActive();
    }

    void close() {
        closed = true;
        ++generation;
        cancelActive();
    }

    private void cancelActive() {
        if (activeCall != null) {
            activeCall.cancel();
            activeCall = null;
        }
    }
}
