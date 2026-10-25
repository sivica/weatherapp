package ivica.weatherapplication;

import java.util.Locale;

import ivica.weatherapplication.model.CurrentWeather;

/** Validates the fields used by the screen before any view is updated. */
final class WeatherDisplay {
    final String city;
    final String description;
    final String temperature;
    final String wind;
    final String heroTemperature;
    final Integer conditionId;

    private WeatherDisplay(String city, String description, double temperature, double windKmh, Integer conditionId) {
        this.city = city;
        this.conditionId = conditionId;
        this.heroTemperature = String.format(Locale.getDefault(), "%.0f°", temperature);
        this.description = description;
        this.temperature = String.format(Locale.getDefault(), "%.1f °C", temperature);
        this.wind = String.format(Locale.getDefault(), "%.1f km/h", windKmh);
    }

    static WeatherDisplay from(CurrentWeather weather) {
        if (weather == null || (weather.getCod() != null && weather.getCod() != 200)
                || empty(weather.getName()) || weather.getMain() == null
                || !finite(weather.getMain().getTemp()) || weather.getWind() == null
                || !finite(weather.getWind().getSpeed()) || weather.getWind().getSpeed() < 0
                || weather.getWeather() == null || weather.getWeather().isEmpty()
                || weather.getWeather().get(0) == null
                || empty(weather.getWeather().get(0).getDescription())) {
            throw new IllegalArgumentException("Incomplete weather response");
        }
        double windKmh = weather.getWind().getSpeed() * 3.6;
        if (!finite(windKmh)) {
            throw new IllegalArgumentException("Invalid wind speed");
        }
        return new WeatherDisplay(weather.getName().trim(),
                weather.getWeather().get(0).getDescription().trim(),
                weather.getMain().getTemp(), windKmh, weather.getWeather().get(0).getId());
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean finite(Double value) {
        return value != null && !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
