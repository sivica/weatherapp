package ivica.weatherapplication;

import android.Manifest;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.appcompat.widget.SearchView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WeatherScreenTest {
    @Before public void resetTransport() { FixtureWeatherActivity.fixture = new FixtureWeatherService(); }

    @Test public void loadingSuccessUsesCurrentTemperatureAndConvertedWind() throws Exception {
        try (ActivityScenario<FixtureWeatherActivity> scenario = ActivityScenario.launch(FixtureWeatherActivity.class)) {
            onView(withId(R.id.error_text_view)).check(matches(withText(R.string.weather_loading)));
            scenario.onActivity(activity -> FixtureWeatherActivity.fixture.calls.get(0).succeed());
            onView(withId(R.id.city_text_view)).check(matches(withText("Zagreb")));
            onView(withId(R.id.temperature_text_view)).check(matches(withText(
                    String.format(Locale.getDefault(), "%.1f °C", 12.5))));
            onView(withId(R.id.wind_text_view)).check(matches(withText(
                    String.format(Locale.getDefault(), "%.1f km/h", 18.0))));
            scenario.onActivity(WeatherScreenTest::assertContentAvoidsSystemBars);
            screenshot("weather-fixture-success.png");
        }
    }

    @Test public void errorThenRetrySucceeds() {
        try (ActivityScenario<FixtureWeatherActivity> scenario = ActivityScenario.launch(FixtureWeatherActivity.class)) {
            scenario.onActivity(activity -> FixtureWeatherActivity.fixture.calls.get(0).fail());
            onView(withId(R.id.error_text_view)).check(matches(withText(R.string.open_weather_failed)));
            scenario.onActivity(activity -> ((SearchView) activity.findViewById(R.id.search_view)).setQuery("Retry", true));
            onView(withId(R.id.error_text_view)).check(matches(withText(R.string.weather_loading)));
            scenario.onActivity(activity -> FixtureWeatherActivity.fixture.calls.get(1).succeed());
            onView(withId(R.id.city_text_view)).check(matches(withText("Retry")));
            onView(withId(R.id.error_text_view)).check(matches(withText("")));
        }
    }

    @Test public void repeatedSearchIgnoresLateOlderResults() {
        try (ActivityScenario<FixtureWeatherActivity> scenario = ActivityScenario.launch(FixtureWeatherActivity.class)) {
            scenario.onActivity(activity -> {
                SearchView search = activity.findViewById(R.id.search_view);
                search.setQuery("First", true);
                search.setQuery("Latest", true);
                FixtureWeatherService service = FixtureWeatherActivity.fixture;
                assertTrue(service.calls.get(0).canceled);
                assertTrue(service.calls.get(1).canceled);
                service.calls.get(2).succeed();
                service.calls.get(1).succeed();
                service.calls.get(0).fail();
            });
            onView(withId(R.id.city_text_view)).check(matches(withText("Latest")));
            onView(withId(R.id.error_text_view)).check(matches(withText("")));
        }
    }

    @Test public void recreationRetainsPendingWorkResultAndSearchQuery() {
        try (ActivityScenario<FixtureWeatherActivity> scenario = ActivityScenario.launch(FixtureWeatherActivity.class)) {
            scenario.onActivity(activity -> ((SearchView) activity.findViewById(R.id.search_view)).setQuery("Retained query", false));
            scenario.recreate();
            onView(withId(R.id.error_text_view)).check(matches(withText(R.string.weather_loading)));
            scenario.onActivity(activity -> {
                assertEquals(1, FixtureWeatherActivity.fixture.calls.size());
                SearchView search = activity.findViewById(R.id.search_view);
                assertEquals("Retained query", search.getQuery().toString());
                FixtureWeatherActivity.fixture.calls.get(0).succeed();
            });
            scenario.recreate();
            onView(withId(R.id.city_text_view)).check(matches(withText("Zagreb")));
            scenario.onActivity(activity -> assertEquals(1, FixtureWeatherActivity.fixture.calls.size()));
        }
    }

    @Test public void landscapeRotationRetainsWeather() {
        try (ActivityScenario<FixtureWeatherActivity> scenario = ActivityScenario.launch(FixtureWeatherActivity.class)) {
            scenario.onActivity(activity -> {
                FixtureWeatherActivity.fixture.calls.get(0).succeed();
                activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            });
            java.util.concurrent.atomic.AtomicBoolean landscape = new java.util.concurrent.atomic.AtomicBoolean();
            long deadline = android.os.SystemClock.uptimeMillis() + 10000;
            while (!landscape.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> landscape.set(activity.getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE));
                if (!landscape.get()) android.os.SystemClock.sleep(100);
            }
            assertTrue("Landscape rotation did not occur", landscape.get());
            onView(withId(R.id.city_text_view)).check(matches(withText("Zagreb")));
            scenario.onActivity(WeatherScreenTest::assertContentAvoidsSystemBars);
            scenario.onActivity(activity -> assertEquals(1, FixtureWeatherActivity.fixture.calls.size()));
        }
    }

    @Test public void missingKeyPreventsFixtureTransport() {
        FixtureWeatherActivity.fixture.configured = false;
        try (ActivityScenario<FixtureWeatherActivity> scenario = ActivityScenario.launch(FixtureWeatherActivity.class)) {
            onView(withId(R.id.error_text_view)).check(matches(withText(R.string.weather_key_missing)));
            scenario.onActivity(activity -> ((SearchView) activity.findViewById(R.id.search_view)).setQuery("City", true));
            assertTrue(FixtureWeatherActivity.fixture.calls.isEmpty());
            onView(withId(R.id.error_text_view)).check(matches(withText(R.string.weather_key_missing)));
        }
    }

    @Test public void productionMissingKeyShowsConfigurationMessage() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.error_text_view)).check(matches(withText(R.string.weather_key_missing)));
        }
    }

    @Test public void locationPermissionRefusalLeavesCitySearchAvailable() throws Exception {
        shell("pm revoke ivica.weatherapplication " + Manifest.permission.ACCESS_FINE_LOCATION);
        shell("pm revoke ivica.weatherapplication " + Manifest.permission.ACCESS_COARSE_LOCATION);
        try (ActivityScenario<FixtureLocationActivity> scenario = ActivityScenario.launch(FixtureLocationActivity.class)) {
            android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            AccessibilityNodeInfo deny = null;
            long deadline = android.os.SystemClock.uptimeMillis() + 10000;
            while (deny == null && android.os.SystemClock.uptimeMillis() < deadline) {
                AccessibilityNodeInfo root = automation.getRootInActiveWindow();
                if (root != null) {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(
                            "com.android.permissioncontroller:id/permission_deny_button");
                    if (!nodes.isEmpty()) deny = nodes.get(0);
                }
                if (deny == null) android.os.SystemClock.sleep(100);
            }
            assertNotNull("Location permission prompt did not appear", deny);
            assertTrue(deny.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            onView(withId(R.id.error_text_view)).check(matches(withText(R.string.location_failed)));
            assertTrue(FixtureWeatherActivity.fixture.calls.isEmpty());
            scenario.onActivity(activity -> ((SearchView) activity.findViewById(R.id.search_view)).setQuery("Manual", true));
            scenario.onActivity(activity -> FixtureWeatherActivity.fixture.calls.get(0).succeed());
            onView(withId(R.id.city_text_view)).check(matches(withText("Manual")));
        }
    }

    private static void assertContentAvoidsSystemBars(MainActivity activity) {
        assertEquals("The QA APK must use the final target SDK", 37,
                activity.getApplicationInfo().targetSdkVersion);
        android.view.View root = activity.findViewById(R.id.toolbar).getRootView();
        androidx.core.view.WindowInsetsCompat windowInsets =
                androidx.core.view.ViewCompat.getRootWindowInsets(root);
        assertNotNull("Window insets must be available", windowInsets);
        androidx.core.graphics.Insets safe = windowInsets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
                        | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
        int[] origin = new int[2];
        root.getLocationOnScreen(origin);
        for (int id : new int[] {R.id.toolbar, R.id.city_text_view,
                R.id.temperature_text_view, R.id.wind_text_view}) {
            android.view.View content = activity.findViewById(id);
            int[] position = new int[2];
            content.getLocationOnScreen(position);
            assertTrue("Content overlaps the status bar/cutout", position[1] >= origin[1] + safe.top);
            assertTrue("Content overlaps the left cutout", position[0] >= origin[0] + safe.left);
            assertTrue("Content overlaps the right system bar", position[0] + content.getWidth()
                    <= origin[0] + root.getWidth() - safe.right);
            assertTrue("Content overlaps the navigation bar", position[1] + content.getHeight()
                    <= origin[1] + root.getHeight() - safe.bottom);
        }
    }

    private static void shell(String command) throws Exception {
        ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) { /* Wait for command completion. */ }
        }
    }

    private static void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // Espresso checks view state before the compositor necessarily presents it.
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(300, 3000);
        android.os.SystemClock.sleep(200);
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null), name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        bitmap.recycle();
    }
}
