package ivica.weatherapplication;

import android.os.Bundle;

/** Non-exported debug-only screen, launched explicitly by instrumentation. */
public class FixtureWeatherActivity extends MainActivity {
    public static FixtureWeatherService fixture = new FixtureWeatherService();

    @Override protected OpenWeatherService weatherService() { return fixture; }
    @Override protected String weatherApiKey() { return fixture.configured ? "fixture-only" : ""; }
    @Override protected boolean usesStartupLocation() { return false; }
    protected boolean autoLoadFixture() { return true; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state == null && autoLoadFixture()) getCurrentWeather("Zagreb");
    }
}
