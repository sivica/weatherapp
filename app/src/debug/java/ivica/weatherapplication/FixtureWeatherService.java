package ivica.weatherapplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ivica.weatherapplication.model.CurrentWeather;
import ivica.weatherapplication.model.Main;
import ivica.weatherapplication.model.Weather;
import ivica.weatherapplication.model.Wind;
import okhttp3.Request;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Debug-only manual transport. Every call is an in-memory fixture; no HTTP client exists. */
public final class FixtureWeatherService implements OpenWeatherService {
    public boolean configured = true;
    public final List<Pending> calls = new ArrayList<>();

    @Override public Call<CurrentWeather> getCurrentWeatherByCityName(Map<String, String> options) {
        Pending pending = new Pending(options.get("q"));
        calls.add(pending);
        return pending;
    }

    public static final class Pending implements Call<CurrentWeather> {
        private final String city;
        private Callback<CurrentWeather> callback;
        public boolean canceled;

        Pending(String city) { this.city = city; }
        public void succeed() {
            CurrentWeather weather = new CurrentWeather();
            weather.setName(city);
            weather.setCod(200);
            Main main = new Main();
            main.setTemp(12.5);
            main.setTempMax(19.0);
            weather.setMain(main);
            Wind wind = new Wind();
            wind.setSpeed(5.0);
            weather.setWind(wind);
            Weather description = new Weather();
            description.setDescription("clear sky");
            weather.setWeather(java.util.Collections.singletonList(description));
            callback.onResponse(this, Response.success(weather));
        }
        public void fail() { callback.onFailure(this, new java.io.IOException("Fixture failure")); }
        @Override public Response<CurrentWeather> execute() { throw new AssertionError("No HTTP"); }
        @Override public void enqueue(Callback<CurrentWeather> value) { callback = value; }
        @Override public boolean isExecuted() { return callback != null; }
        @Override public void cancel() { canceled = true; }
        @Override public boolean isCanceled() { return canceled; }
        @Override public Call<CurrentWeather> clone() { return new Pending(city); }
        @Override public Request request() { throw new AssertionError("No HTTP"); }
        @Override public Timeout timeout() { return Timeout.NONE; }
    }
}
