package ivica.weatherapplication;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

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
import androidx.activity.SystemBarStyle;
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
        EdgeToEdge.enable(this, SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
                SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK));
        setContentView(binding.getRoot());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
        ViewCompat.setAccessibilityHeading(binding.appHeading, true);
        ViewCompat.setAccessibilityHeading(binding.cityTextView, true);
        ViewCompat.setAccessibilityHeading(binding.stateTitle, true);
        binding.searchView.setIconifiedByDefault(false);
        TextView searchText = binding.searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        searchText.setTextSize(16);
        searchText.setTextColor(getColor(R.color.weather_ink));
        searchText.setHintTextColor(getColor(R.color.weather_muted));
        binding.searchView.clearFocus();
        binding.getRoot().setFocusableInTouchMode(true);
        binding.getRoot().requestFocus();
        binding.weatherSource.setText(isFixtureData()
                ? R.string.weather_fixture_units : R.string.weather_units);
        binding.privacyPolicyButton.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.privacy_policy_url))));
            } catch (ActivityNotFoundException unavailable) {
                Toast.makeText(this, R.string.privacy_browser_unavailable, Toast.LENGTH_LONG).show();
            }
        });
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
            @Override public void failed() { weatherModel.locationFailed(); }
        });
        binding.retryButton.setOnClickListener(view -> {
            startupLocation.stop();
            getCurrentWeather(weatherModel.requestedCity());
        });
        binding.locationButton.setOnClickListener(view -> {
            startupLocation.stop();
            if (weatherModel.isConfigured()) {
                weatherModel.beginLocation();
                startupLocation.start(true);
            }
            else weatherModel.load(null);
        });
        if (weatherModel.state().getValue() == null) {
            render(new WeatherViewModel.State(false, null, null));
            startupLocation.start(usesStartupLocation() && weatherModel.isConfigured());
            if (!weatherModel.isConfigured()) weatherModel.load(null);
        } else if (weatherModel.isLocating()) {
            startupLocation.start(true);
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
    protected boolean usesStartupLocation() { return false; }
    protected boolean isFixtureData() { return false; }

    private void render(WeatherViewModel.State state) {
        WeatherDisplay weather = state.weather;
        boolean success = weather != null;
        binding.currentWeather.setVisibility(success ? View.VISIBLE : View.GONE);
        binding.windCard.setVisibility(success ? View.VISIBLE : View.GONE);
        binding.stateContent.setVisibility(success ? View.GONE : View.VISIBLE);
        binding.retryButton.setVisibility(state.error == WeatherController.Error.WEATHER_FAILED
                && !weatherModel.requestedCity().isEmpty() ? View.VISIBLE : View.GONE);
        binding.searchView.setQueryHint(getString(state.loading || state.error != null
                ? R.string.weather_search_another : R.string.weather_search_hint));
        binding.cityTextView.setText(success ? weather.city : "");
        binding.weatherTextView.setText(success ? capitalize(weather.description) : "");
        binding.temperatureTextView.setText(success ? weather.heroTemperature : "");
        binding.temperatureTextView.setContentDescription(success ? weather.temperature : null);
        binding.windTextView.setText(success ? weather.wind : "");
        // The approved sun artwork represents daytime partly cloudy conditions only.
        // Night and unknown icon metadata retain text without a misleading illustration.
        binding.conditionIllustration.setVisibility(success && weather.showsPartlyCloudyIllustration()
                ? View.VISIBLE : View.GONE);
        binding.errorTextView.setText("");
        if (success) return;
        int title = R.string.weather_empty_title;
        int illustration = R.drawable.weather_empty;
        CharSequence body = getString(R.string.weather_empty_body);
        if (state.loading) {
            title = R.string.weather_loading_title;
            illustration = R.drawable.weather_loading;
            body = getString(R.string.weather_loading);
        } else if (state.locationFailed) {
            body = getString(R.string.location_failed) + ". " + getString(R.string.weather_empty_body);
        } else if (state.error != null) {
            title = R.string.weather_error_title;
            illustration = R.drawable.weather_error;
            if (state.error == WeatherController.Error.MISSING_API_KEY) {
                body = getString(R.string.weather_key_missing);
            } else if (state.error == WeatherController.Error.EMPTY_CITY) {
                body = getString(R.string.weather_city_missing);
            } else {
                body = getString(R.string.weather_error_body, weatherModel.requestedCity());
                binding.retryButton.setText(getString(R.string.weather_retry_city, weatherModel.requestedCity()));
            }
        }
        binding.stateContent.setPadding(binding.stateContent.getPaddingLeft(),
                binding.stateContent.getPaddingTop(), binding.stateContent.getPaddingRight(),
                getResources().getDimensionPixelSize(state.error == null
                        ? R.dimen.weather_state_bottom : R.dimen.weather_error_bottom));
        binding.stateTitle.setText(title);
        binding.stateIllustration.setImageResource(illustration);
        binding.errorTextView.setText(body);
    }

    private static String capitalize(String description) {
        return description.substring(0, 1).toUpperCase(Locale.getDefault()) + description.substring(1);
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
