package ivica.weatherapplication;

import com.google.gson.Gson;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

import ivica.weatherapplication.model.CurrentWeather;

import static org.junit.Assert.*;

public class WeatherDisplayTest {
    private Locale previousLocale;

    @Before public void setLocale() {
        previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After public void restoreLocale() { Locale.setDefault(previousLocale); }

    static CurrentWeather fixture(String name) {
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(
                WeatherDisplayTest.class.getResourceAsStream("/fixtures/" + name)),
                StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, CurrentWeather.class);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test public void showsCurrentTemperatureAndConvertsMetricWindToKmh() {
        WeatherDisplay display = WeatherDisplay.from(fixture("current-weather.json"));
        assertEquals("Zagreb", display.city);
        assertEquals("clear sky", display.description);
        assertEquals("12.5 °C", display.temperature);
        assertEquals("13°", display.heroTemperature);
        assertEquals(Integer.valueOf(800), display.conditionId);
        assertEquals("18.0 km/h", display.wind);
    }

    @Test public void acceptsZeroWindAndNegativeCurrentTemperatureWithoutMaximum() {
        CurrentWeather weather = fixture("current-weather.json");
        weather.getMain().setTemp(-3.0);
        weather.getMain().setTempMax(null);
        weather.getWind().setSpeed(0.0);
        WeatherDisplay display = WeatherDisplay.from(weather);
        assertEquals("-3.0 °C", display.temperature);
        assertEquals("0.0 km/h", display.wind);
    }

    @Test public void sunArtworkRequiresMatchingDaytimeCloudMetadata() {
        for (int id : new int[] {801, 802}) {
            String dayIcon = id == 801 ? "02d" : "03d";
            String nightIcon = id == 801 ? "02n" : "03n";
            for (String icon : new String[] {dayIcon, nightIcon, null, "", "unknown", "01d"}) {
                CurrentWeather weather = fixture("current-weather.json");
                weather.getWeather().get(0).setId(id);
                weather.getWeather().get(0).setIcon(icon);
                WeatherDisplay display = WeatherDisplay.from(weather);
                assertEquals(icon, display.conditionIcon);
                assertEquals("condition " + id + ", icon " + icon,
                        dayIcon.equals(icon), display.showsPartlyCloudyIllustration());
            }
        }
        for (Integer id : new Integer[] {null, 800, 500, 600, 741, 803, 804}) {
            CurrentWeather weather = fixture("current-weather.json");
            weather.getWeather().get(0).setId(id);
            weather.getWeather().get(0).setIcon("02d");
            assertFalse(WeatherDisplay.from(weather).showsPartlyCloudyIllustration());
        }
    }

    @Test public void roundedZeroIsUnsignedAndAccessibleTemperatureKeepsItsPrecision() {
        double[] values = {-0.51, -0.5, -0.49, -0.4, -0.0, 0.0, 0.49, 0.5, 0.51};
        String[] hero = {"-1°", "-1°", "0°", "0°", "0°", "0°", "0°", "1°", "1°"};
        String[] precise = {"-0.5 °C", "-0.5 °C", "-0.5 °C", "-0.4 °C", "-0.0 °C", "0.0 °C", "0.5 °C", "0.5 °C", "0.5 °C"};
        for (int i = 0; i < values.length; i++) {
            CurrentWeather weather = fixture("current-weather.json");
            weather.getMain().setTemp(values[i]);
            WeatherDisplay display = WeatherDisplay.from(weather);
            assertEquals("hero for " + values[i], hero[i], display.heroTemperature);
            assertEquals("precise for " + values[i], precise[i], display.temperature);
        }
    }

    @Test public void rejectsIncompleteAndErrorFixturesAndNullBody() {
        rejects(null);
        rejects(fixture("incomplete-weather.json"));
        rejects(fixture("api-error.json"));
    }

    @Test public void malformedJsonFailsParsingBeforeDisplay() {
        assertThrows(com.google.gson.JsonSyntaxException.class,
                () -> fixture("malformed-weather.json"));
    }

    @Test public void rejectsEachMissingDisplayField() {
        for (int field = 0; field < 11; field++) {
            CurrentWeather weather = fixture("current-weather.json");
            switch (field) {
                case 0: weather.setName(null); break;
                case 1: weather.setName("  "); break;
                case 2: weather.setMain(null); break;
                case 3: weather.getMain().setTemp(null); break;
                case 4: weather.setWind(null); break;
                case 5: weather.getWind().setSpeed(null); break;
                case 6: weather.setWeather(null); break;
                case 7: weather.setWeather(Collections.emptyList()); break;
                case 8: weather.setWeather(Collections.singletonList(null)); break;
                case 9: weather.getWeather().get(0).setDescription(null); break;
                case 10: weather.getWeather().get(0).setDescription("  "); break;
                default: throw new AssertionError();
            }
            rejects(weather);
        }
    }

    @Test public void rejectsNonFiniteAndNegativeWindValues() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY}) {
            CurrentWeather weather = fixture("current-weather.json");
            weather.getMain().setTemp(value);
            rejects(weather);
        }
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, -1, Double.MAX_VALUE}) {
            CurrentWeather weather = fixture("current-weather.json");
            weather.getWind().setSpeed(value);
            rejects(weather);
        }
    }

    private static void rejects(CurrentWeather weather) {
        assertThrows(IllegalArgumentException.class, () -> WeatherDisplay.from(weather));
    }
}
