package ivica.weatherapplication;

/** Exercises the real permission prompt with the in-memory weather transport. */
public final class FixtureLocationActivity extends FixtureWeatherActivity {
    @Override protected boolean usesStartupLocation() { return true; }
    @Override protected boolean autoLoadFixture() { return false; }
}
