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
        final boolean locationFailed;

        State(boolean loading, WeatherDisplay weather, WeatherController.Error error) {
            this(loading, weather, error, false);
        }

        State(boolean loading, WeatherDisplay weather, WeatherController.Error error, boolean locationFailed) {
            this.locationFailed = locationFailed;
            this.loading = loading;
            this.weather = weather;
            this.error = error;
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final WeatherController controller;
    private String requestedCity = "";
    private boolean locating;

    WeatherViewModel(OpenWeatherService service, String apiKey) {
        controller = new WeatherController(service, apiKey, this);
    }

    LiveData<State> state() { return state; }
    boolean isConfigured() { return controller.isConfigured(); }
    String requestedCity() { return requestedCity; }
    boolean isLocating() { return locating; }
    void beginLocation() {
        controller.cancelPending();
        locating = true;
        requestedCity = "";
        state.setValue(new State(true, null, null));
    }
    void load(String city) {
        locating = false;
        requestedCity = city == null ? "" : city.trim();
        controller.load(city);
    }
    void locationFailed() {
        locating = false;
        state.setValue(new State(false, null, null, true));
    }
    @Override public void clear() { state.setValue(new State(true, null, null)); }
    @Override public void show(WeatherDisplay weather) { state.setValue(new State(false, weather, null)); }
    @Override public void error(WeatherController.Error error) { state.setValue(new State(false, null, error)); }
    @Override protected void onCleared() { controller.close(); }
}
