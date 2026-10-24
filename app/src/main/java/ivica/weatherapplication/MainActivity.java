package ivica.weatherapplication;

import android.Manifest;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.activity.EdgeToEdge;
import androidx.core.view.WindowInsetsCompat;

import ivica.weatherapplication.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private AndroidCityLocation cityLocation;
    private StartupLocationController startupLocation;
    private WeatherViewModel weatherModel;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        binding.searchView.setQueryHint(getString(R.string.city));
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                startupLocation.stop();
                getCurrentWeather(query);
                binding.searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        weatherModel = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return modelClass.cast(new WeatherViewModel(weatherService(), weatherApiKey()));
            }
        }).get(WeatherViewModel.class);
        weatherModel.state().observe(this, this::render);
        if (savedInstanceState != null) {
            binding.searchView.setQuery(savedInstanceState.getString("search_query", ""), false);
        }
        ActivityResultLauncher<String[]> permissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), grants ->
                        startupLocation.permissionsResult(
                                Boolean.TRUE.equals(grants.get(Manifest.permission.ACCESS_FINE_LOCATION)),
                                Boolean.TRUE.equals(grants.get(Manifest.permission.ACCESS_COARSE_LOCATION))));
        cityLocation = new AndroidCityLocation(this, () -> permissionRequest.launch(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        }));
        startupLocation = new StartupLocationController(cityLocation, new StartupLocationController.Result() {
            @Override public void city(String city) { getCurrentWeather(city); }
            @Override public void failed() { binding.errorTextView.setText(R.string.location_failed); }
        });
        if (weatherModel.state().getValue() == null) {
            startupLocation.start(usesStartupLocation() && weatherModel.isConfigured());
            if (!weatherModel.isConfigured()) weatherModel.load(null);
        }
    }

    public void getCurrentWeather(String city) {
        weatherModel.load(city);
    }

    protected OpenWeatherService weatherService() {
        return OpenWeather.getClient().create(OpenWeatherService.class);
    }

    protected String weatherApiKey() {
        // Credentials must never be sourced from a tracked resource. Fail closed
        // if the legacy empty placeholder is accidentally populated.
        if (!getString(R.string.open_weather_api_key).isEmpty()) return "";
        return BuildConfig.OPENWEATHER_API_KEY;
    }
    protected boolean usesStartupLocation() { return true; }

    private void render(WeatherViewModel.State state) {
        WeatherDisplay weather = state.weather;
        binding.cityTextView.setText(weather == null ? "" : weather.city);
        binding.weatherTextView.setText(weather == null ? "" : weather.description);
        binding.temperatureTextView.setText(weather == null ? "" : weather.temperature);
        binding.windTextView.setText(weather == null ? "" : weather.wind);
        if (state.loading) binding.errorTextView.setText(R.string.weather_loading);
        else if (state.error == null) binding.errorTextView.setText("");
        else {
            int message = state.error == WeatherController.Error.MISSING_API_KEY
                    ? R.string.weather_key_missing
                    : state.error == WeatherController.Error.EMPTY_CITY
                    ? R.string.weather_city_missing : R.string.open_weather_failed;
            binding.errorTextView.setText(message);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("search_query", binding.searchView.getQuery().toString());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        startupLocation.stop();
        cityLocation.close();
        binding = null;
        super.onDestroy();
    }
}
