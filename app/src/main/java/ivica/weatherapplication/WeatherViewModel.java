package ivica.weatherapplication;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/** Keeps in-flight work and its visible state across Activity recreation. */
final class WeatherViewModel extends ViewModel implements WeatherController.Listener {
    static final class State {
        final boolean loading;
        final WeatherDisplay weather;
        final WeatherController.Error error;

        State(boolean loading, WeatherDisplay weather, WeatherController.Error error) {
            this.loading = loading;
            this.weather = weather;
            this.error = error;
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final WeatherController controller;
    private String requestedCity = "";

    WeatherViewModel(OpenWeatherService service, String apiKey) {
        controller = new WeatherController(service, apiKey, this);
    }

    LiveData<State> state() { return state; }
    boolean isConfigured() { return controller.isConfigured(); }
    String requestedCity() { return requestedCity; }
    void load(String city) {
        requestedCity = city == null ? "" : city.trim();
        controller.load(city);
    }
    @Override public void clear() { state.setValue(new State(true, null, null)); }
    @Override public void show(WeatherDisplay weather) { state.setValue(new State(false, weather, null)); }
    @Override public void error(WeatherController.Error error) { state.setValue(new State(false, null, error)); }
    @Override protected void onCleared() { controller.close(); }
}
