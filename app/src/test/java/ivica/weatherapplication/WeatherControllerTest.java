package ivica.weatherapplication;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ivica.weatherapplication.model.CurrentWeather;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static org.junit.Assert.*;

/** Fakes deliver callbacks even after cancellation to exercise real callback races. */
public class WeatherControllerTest {
    private final FakeService service = new FakeService();
    private final RecordingListener listener = new RecordingListener();
    private final WeatherController controller = new WeatherController(service, "fixture-only", listener);

    @Test public void newestSuccessWinsDespiteLateOlderSuccessAndFailure() {
        controller.load("First");
        controller.load("Second");
        assertTrue(service.calls.get(0).cancelled);
        service.calls.get(1).succeed(weather("Second"));
        service.calls.get(0).succeed(weather("First"));
        service.calls.get(0).fail(new IOException("old failure"));
        assertEquals("Second", listener.display.city);
        assertNull(listener.error);
        assertEquals(1, listener.shown);
    }

    @Test public void newestErrorCannotBeOverwrittenByOlderSuccess() {
        controller.load("First");
        controller.load("Second");
        service.calls.get(1).fail(new IOException("offline"));
        service.calls.get(0).succeed(weather("First"));
        assertEquals(WeatherController.Error.WEATHER_FAILED, listener.error);
        assertNull(listener.display);
    }

    @Test public void mapsNullIncompleteHttpAndConversionFailuresToError() {
        controller.load("City");
        service.calls.get(0).succeed(null);
        assertFailed();
        controller.load("City");
        service.calls.get(1).succeed(WeatherDisplayTest.fixture("incomplete-weather.json"));
        assertFailed();
        controller.load("City");
        service.calls.get(2).callback.onResponse(service.calls.get(2),
                Response.error(404, ResponseBody.create(null, "{}")));
        assertFailed();
        controller.load("City");
        service.calls.get(3).fail(new com.google.gson.JsonSyntaxException("malformed fixture"));
        assertFailed();
    }

    @Test public void newSearchClearsPreviousCityAndError() {
        controller.load("First");
        service.calls.get(0).succeed(weather("First"));
        controller.load("Second");
        assertNull(listener.display);
        assertNull(listener.error);
        service.calls.get(1).fail(new IOException("offline"));
        controller.load("Third");
        assertNull(listener.error);
    }

    @Test public void closeCancelsAndIgnoresCallbacksAndNewRequests() {
        controller.load("City");
        controller.close();
        service.calls.get(0).succeed(weather("Too late"));
        service.calls.get(0).fail(new IOException("too late"));
        controller.load("Another");
        assertTrue(service.calls.get(0).cancelled);
        assertEquals(1, service.calls.size());
        assertNull(listener.display);
        assertNull(listener.error);
    }

    @Test public void locationChoiceCancelsAndIgnoresEarlierWeather() {
        controller.load("Earlier city");
        controller.cancelPending();
        service.calls.get(0).succeed(weather("Too late"));
        service.calls.get(0).fail(new IOException("too late"));
        assertTrue(service.calls.get(0).cancelled);
        assertNull(listener.display);
        assertNull(listener.error);
        controller.load("Located city");
        service.calls.get(1).succeed(weather("Located city"));
        assertEquals("Located city", listener.display.city);
    }

    @Test public void blankCityInvalidatesEarlierRequestWithoutMakingAnother() {
        controller.load("First");
        controller.load("   ");
        service.calls.get(0).succeed(weather("First"));
        assertTrue(service.calls.get(0).cancelled);
        assertEquals(1, service.calls.size());
        assertEquals(WeatherController.Error.EMPTY_CITY, listener.error);
        assertNull(listener.display);
        controller.load(null);
        assertEquals(1, service.calls.size());
    }

    @Test public void missingKeyNeverCreatesNetworkCall() {
        for (String key : new String[] {null, "", "  "}) {
            WeatherController unconfigured = new WeatherController(service, key, listener);
            assertFalse(unconfigured.isConfigured());
            unconfigured.load("City");
            assertEquals(WeatherController.Error.MISSING_API_KEY, listener.error);
        }
        assertTrue(service.calls.isEmpty());
    }

    @Test public void requestOptionsAreIndependentTrimmedAndMetric() {
        controller.load("  Zagreb  ");
        controller.load("Split");
        assertNotSame(service.options.get(0), service.options.get(1));
        assertEquals("Zagreb", service.options.get(0).get("q"));
        assertEquals("Split", service.options.get(1).get("q"));
        assertEquals("metric", service.options.get(0).get("units"));
    }

    private void assertFailed() {
        assertEquals(WeatherController.Error.WEATHER_FAILED, listener.error);
        assertNull(listener.display);
    }

    private static CurrentWeather weather(String city) {
        CurrentWeather result = WeatherDisplayTest.fixture("current-weather.json");
        result.setName(city);
        return result;
    }

    private static final class FakeService implements OpenWeatherService {
        final List<FakeCall> calls = new ArrayList<>();
        final List<Map<String, String>> options = new ArrayList<>();
        @Override public Call<CurrentWeather> getCurrentWeatherByCityName(Map<String, String> values) {
            options.add(values);
            FakeCall call = new FakeCall();
            calls.add(call);
            return call;
        }
    }

    private static final class RecordingListener implements WeatherController.Listener {
        WeatherDisplay display;
        WeatherController.Error error;
        int shown;
        @Override public void clear() { display = null; error = null; }
        @Override public void show(WeatherDisplay weather) { display = weather; shown++; }
        @Override public void error(WeatherController.Error value) { error = value; }
    }

    private static final class FakeCall implements Call<CurrentWeather> {
        Callback<CurrentWeather> callback;
        boolean cancelled;
        void succeed(CurrentWeather body) { callback.onResponse(this, Response.success(body)); }
        void fail(Throwable failure) { callback.onFailure(this, failure); }
        @Override public Response<CurrentWeather> execute() { throw new AssertionError("No I/O"); }
        @Override public void enqueue(Callback<CurrentWeather> value) { callback = value; }
        @Override public boolean isExecuted() { return callback != null; }
        @Override public void cancel() { cancelled = true; }
        @Override public boolean isCanceled() { return cancelled; }
        @Override public Call<CurrentWeather> clone() { return new FakeCall(); }
        @Override public Request request() { throw new AssertionError("No HTTP request"); }
        @Override public Timeout timeout() { return Timeout.NONE; }
    }
}
