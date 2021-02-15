package ivica.weatherapplication;

import java.util.Map;

import ivica.weatherapplication.model.CurrentWeather;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.QueryMap;

public interface OpenWeatherService {

    String CURRENT = "/data/2.5/weather";

    @GET(CURRENT)
    Call<CurrentWeather> getCurrentWeatherByCityName(@QueryMap Map<String, String> options);
}
