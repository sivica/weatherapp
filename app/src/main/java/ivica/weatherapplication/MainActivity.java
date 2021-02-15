package ivica.weatherapplication;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;

import com.dmgdesignuk.locationutils.easyaddressutility.EasyAddressUtility;
import com.dmgdesignuk.locationutils.easylocationutility.EasyLocationUtility;
import com.dmgdesignuk.locationutils.easylocationutility.LocationRequestCallback;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import ivica.weatherapplication.databinding.ActivityMainBinding;
import ivica.weatherapplication.model.CurrentWeather;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String APPID = "appId";
    private static final String UNITS = "units";
    private static final String METRIC = "metric";
    private static final String QUERY = "q";

    private EasyLocationUtility locationUtility;
    private EasyAddressUtility addressUtility;
    private Map<String, String> options;
    private OpenWeatherService openWeatherService;
    private CurrentWeather currentWeather;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        binding.searchView.setQueryHint(getString(R.string.city));
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                getCurrentWeather(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        locationUtility = new EasyLocationUtility(this);
        addressUtility = new EasyAddressUtility(this.getApplicationContext());
        getLastLocation();

        openWeatherService = OpenWeather.getClient().create(OpenWeatherService.class);
        options = new HashMap<>();
        options.put(APPID, getString(R.string.open_weather_api_key));
        options.put(UNITS, METRIC);
    }

    public void getCurrentWeather(String city) {
        options.put(QUERY, city);
        openWeatherService.getCurrentWeatherByCityName(options).enqueue(new Callback<CurrentWeather>() {
            @Override
            public void onResponse(@NonNull Call<CurrentWeather> call, @NonNull Response<CurrentWeather> response) {
                if (response.code() == HttpURLConnection.HTTP_OK){
                    currentWeather = response.body();
                    binding.cityTextView.setText(currentWeather.getName());
                    binding.weatherTextView.setText(currentWeather.getWeather().get(0).getDescription());
                    binding.temperatureTextView.setText(currentWeather.getMain().getTempMax() + " C");
                    binding.windTextView.setText(""+currentWeather.getWind().getSpeed()+" km/h");
                    binding.errorTextView.setText("");
                } else{
                    binding.errorTextView.setText(getString(R.string.open_weather_failed));
                }
            }

            @Override
            public void onFailure(@NonNull Call<CurrentWeather> call, @NonNull Throwable throwable) {
                binding.errorTextView.setText(getString(R.string.open_weather_failed));
            }
        });
    }

    public void getLastLocation(){
        if (locationUtility.permissionIsGranted()){
            locationUtility.checkDeviceSettings(EasyLocationUtility.RequestCodes.LAST_KNOWN_LOCATION);
            locationUtility.getLastKnownLocation(new LocationRequestCallback() {
                @Override
                public void onLocationResult(Location location) {
                    getAddressElementsFromLocation(location);
                }

                @Override
                public void onFailedRequest(String result) {
                    binding.errorTextView.setText(getString(R.string.location_failed));
                }
            });
        } else {
            locationUtility.requestPermission(EasyLocationUtility.RequestCodes.LAST_KNOWN_LOCATION);
        }
    }

    public void getAddressElementsFromLocation(Location location){
        String city = addressUtility.getAddressElement(EasyAddressUtility.AddressCodes.CITY_NAME, location);
        getCurrentWeather(city);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean requestGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestGranted) {
            getLastLocation();
        } else {
            binding.errorTextView.setText(getString(R.string.location_failed));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            binding.errorTextView.setText(getString(R.string.location_failed));
        } else {
            getLastLocation();
        }
    }
}