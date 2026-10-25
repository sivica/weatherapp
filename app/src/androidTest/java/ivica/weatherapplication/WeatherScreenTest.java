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
import static androidx.test.espresso.action.ViewActions.*;
import static org.hamcrest.Matchers.containsString;
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
                    String.format(Locale.getDefault(), "%.0f°", 12.5))));
            onView(withId(R.id.wind_text_view)).check(matches(withText(
                    String.format(Locale.getDefault(), "%.1f km/h", 18.0))));
            scenario.onActivity(WeatherScreenTest::assertContentAvoidsSystemBars);
            screenshot("weather-fixture-success.png");
        }
    }

    @Test public void errorThenRetrySucceeds() {
        try (ActivityScenario<FixtureWeatherActivity> scenario = ActivityScenario.launch(FixtureWeatherActivity.class)) {
            scenario.onActivity(activity -> FixtureWeatherActivity.fixture.calls.get(0).fail());
            onView(withId(R.id.error_text_view)).check(matches(withText(
                    activityString(R.string.weather_error_body, "Zagreb"))));
            scenario.onActivity(activity -> ((SearchView) activity.findViewById(R.id.search_view)).setQuery("Unsubmitted city", false));
            onView(withId(R.id.retry_button)).perform(scrollTo(), click());
            onView(withId(R.id.error_text_view)).check(matches(withText(R.string.weather_loading)));
            scenario.onActivity(activity -> FixtureWeatherActivity.fixture.calls.get(1).succeed());
            onView(withId(R.id.city_text_view)).check(matches(withText("Zagreb")));
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
            onView(withId(R.id.location_button)).perform(scrollTo());
            try { screenshot("quiet-landscape.png"); } catch (Exception e) { throw new AssertionError(e); }
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

    @Test public void missingKeyMessageSurvivesRecreationWithoutRequests() {
        // Every screen test uses the fake transport, even when a local debug key exists.
        FixtureWeatherActivity.fixture.configured = false;
        try (ActivityScenario<FixtureWeatherActivity> scenario = ActivityScenario.launch(FixtureWeatherActivity.class)) {
            scenario.recreate();
            onView(withId(R.id.error_text_view)).check(matches(withText(R.string.weather_key_missing)));
            assertTrue(FixtureWeatherActivity.fixture.calls.isEmpty());
        }
    }

    @Test public void locationPermissionRefusalLeavesCitySearchAvailable() throws Exception {
        shell("pm revoke ivica.weatherapplication " + Manifest.permission.ACCESS_FINE_LOCATION);
        shell("pm revoke ivica.weatherapplication " + Manifest.permission.ACCESS_COARSE_LOCATION);
        // Repeated fixture runs must not inherit Android's permanent-denial flags.
        shell("pm clear-permission-flags ivica.weatherapplication " + Manifest.permission.ACCESS_FINE_LOCATION + " user-set user-fixed");
        shell("pm clear-permission-flags ivica.weatherapplication " + Manifest.permission.ACCESS_COARSE_LOCATION + " user-set user-fixed");
        FixtureWeatherActivity.fixture.autoLoad = false;
        try (ActivityScenario<FixtureWeatherActivity> scenario = ActivityScenario.launch(FixtureWeatherActivity.class)) {
            onView(withId(R.id.location_button)).perform(scrollTo(), click());
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
            onView(withId(R.id.error_text_view)).check(matches(withText(containsString(activityString(R.string.location_failed)))));
            assertTrue(FixtureWeatherActivity.fixture.calls.isEmpty());
            scenario.onActivity(activity -> ((SearchView) activity.findViewById(R.id.search_view)).setQuery("Manual", true));
            scenario.onActivity(activity -> FixtureWeatherActivity.fixture.calls.get(0).succeed());
            onView(withId(R.id.city_text_view)).check(matches(withText("Manual")));
        }
    }

    @Test public void emptySearchLoadingErrorAndRetryScreens() throws Exception {
        FixtureWeatherActivity.fixture.autoLoad = false;
        try (ActivityScenario<FixtureWeatherActivity> scenario = ActivityScenario.launch(FixtureWeatherActivity.class)) {
            onView(withId(R.id.state_title)).check(matches(withText(R.string.weather_empty_title)));
            screenshot("quiet-empty.png");
            onView(withId(R.id.search_view)).perform(scrollTo());
            onView(withId(androidx.appcompat.R.id.search_src_text)).perform(typeText("Skopje"), pressImeActionButton());
            onView(withId(R.id.state_title)).check(matches(withText(R.string.weather_loading_title)));
            screenshot("quiet-loading.png");
            scenario.onActivity(activity -> FixtureWeatherActivity.fixture.calls.get(0).fail());
            screenshot("quiet-error.png");
            scenario.recreate();
            onView(withId(R.id.retry_button)).check(matches(withText(activityString(R.string.weather_retry_city, "Skopje"))));
            onView(withId(R.id.retry_button)).perform(scrollTo(), click());
            scenario.onActivity(activity -> FixtureWeatherActivity.fixture.calls.get(1).succeed(24, 12.0 / 3.6, 801, "partly cloudy"));
            onView(withId(R.id.city_text_view)).check(matches(withText("Skopje")));
            onView(withId(R.id.temperature_text_view)).check(matches(withText("24°")));
            onView(withId(R.id.weather_text_view)).check(matches(withText("Partly cloudy")));
            scenario.onActivity(activity -> {
                assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.condition_illustration).getVisibility());
                assertContentAvoidsSystemBars(activity);
            });
            onView(withId(R.id.city_text_view)).perform(scrollTo());
            screenshot("quiet-success.png");
        }
    }

    @Test public void largeFontsKeepSearchAndLocationReachable() throws Exception {
        shell("settings put system font_scale 2.0");
        try (ActivityScenario<FixtureWeatherActivity> scenario = ActivityScenario.launch(FixtureWeatherActivity.class)) {
            scenario.onActivity(activity -> FixtureWeatherActivity.fixture.calls.get(0).succeed(24, 12.0 / 3.6, 801, "partly cloudy"));
            onView(withId(R.id.location_button)).perform(scrollTo());
            scenario.onActivity(activity -> {
                assertTrue(activity.getResources().getConfiguration().fontScale >= 1.9f);
                assertContentAvoidsSystemBars(activity);
                android.widget.TextView wind = activity.findViewById(R.id.wind_text_view);
                assertTrue(wind.getLayout().getHeight() <= wind.getHeight());
            });
            screenshot("quiet-large-font.png");
            onView(withId(R.id.search_view)).perform(scrollTo());
            onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText("Large font"), pressImeActionButton());
            assertEquals(2, FixtureWeatherActivity.fixture.calls.size());
        } finally { shell("settings put system font_scale 1.0"); }
    }

    private static void assertContentAvoidsSystemBars(MainActivity activity) {
        assertEquals("The QA APK must use the final target SDK", 37,
                activity.getApplicationInfo().targetSdkVersion);
        android.view.View root = activity.findViewById(R.id.weather_scroll).getRootView();
        androidx.core.view.WindowInsetsCompat windowInsets =
                androidx.core.view.ViewCompat.getRootWindowInsets(root);
        assertNotNull("Window insets must be available", windowInsets);
        androidx.core.graphics.Insets safe = windowInsets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
                        | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
        int[] origin = new int[2];
        root.getLocationOnScreen(origin);
        android.view.View viewport = activity.findViewById(R.id.weather_scroll);
        android.graphics.Rect visible = new android.graphics.Rect();
        assertTrue(viewport.getGlobalVisibleRect(visible));
        assertTrue("Viewport overlaps status bar", visible.top + viewport.getPaddingTop() >= origin[1] + safe.top);
        assertTrue("Viewport overlaps left cutout", visible.left + viewport.getPaddingLeft() >= origin[0] + safe.left);
        assertTrue("Viewport overlaps right system bar", visible.right - viewport.getPaddingRight() <= origin[0] + root.getWidth() - safe.right);
        assertTrue("Viewport overlaps navigation bar", visible.bottom - viewport.getPaddingBottom() <= origin[1] + root.getHeight() - safe.bottom);
    }

    private static String activityString(int id, Object... args) {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getString(id, args);
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
