package ivica.weatherapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class StartupLocationControllerTest {
    private final FakeGateway gateway = new FakeGateway();
    private final RecordingResult result = new RecordingResult();
    private final StartupLocationController controller = new StartupLocationController(gateway, result);

    @Test public void missingWeatherKeySkipsPermissionsAndLocation() {
        controller.start(false);
        controller.permissionsResult(true, true);
        assertEquals(0, gateway.requests);
        assertEquals(0, gateway.fetches);
        assertEquals(0, result.failures);
    }

    @Test public void requestsPermissionThenAcceptsApproximateAccess() {
        controller.start(true);
        assertEquals(1, gateway.requests);
        assertEquals(0, gateway.fetches);
        controller.permissionsResult(false, true);
        assertEquals(1, gateway.fetches);
        gateway.callback.city("  Zagreb  ");
        assertEquals("Zagreb", result.city);
    }

    @Test public void existingApproximateOrPrecisePermissionSkipsPrompt() {
        gateway.coarse = true;
        controller.start(true);
        assertEquals(0, gateway.requests);
        assertEquals(1, gateway.fetches);
        gateway.coarse = false;
        gateway.fine = true;
        controller.start(true);
        assertEquals(0, gateway.requests);
        assertEquals(2, gateway.fetches);
    }

    @Test public void denialReportsErrorAndNeverFetchesLocation() {
        controller.start(true);
        controller.permissionsResult(false, false);
        assertEquals(1, result.failures);
        assertEquals(0, gateway.fetches);
    }

    @Test public void manualSearchOrCloseIgnoresLateLocationAndPermissionResults() {
        controller.start(true);
        controller.stop();
        controller.permissionsResult(true, true);
        assertEquals(0, gateway.fetches);
        gateway.coarse = true;
        controller.start(true);
        controller.stop();
        gateway.callback.city("Stale city");
        gateway.callback.failed();
        assertNull(result.city);
        assertEquals(0, result.failures);
    }

    @Test public void noCachedLocationOrUsableCityReportsOneError() {
        gateway.coarse = true;
        for (String city : new String[] {null, "", "   "}) {
            controller.start(true);
            gateway.callback.city(city);
        }
        assertEquals(3, result.failures);
        assertNull(result.city);
        gateway.callback.failed();
        assertEquals(3, result.failures);
    }

    @Test public void platformFailureIsReportedAndDuplicateCallbacksAreIgnored() {
        gateway.fine = true;
        controller.start(true);
        gateway.callback.failed();
        gateway.callback.city("Too late");
        assertEquals(1, result.failures);
        assertNull(result.city);
    }

    private static final class FakeGateway implements StartupLocationController.Gateway {
        boolean fine;
        boolean coarse;
        int requests;
        int fetches;
        StartupLocationController.Result callback;
        @Override public boolean fineGranted() { return fine; }
        @Override public boolean coarseGranted() { return coarse; }
        @Override public void requestPermissions() { requests++; }
        @Override public void findCity(StartupLocationController.Result value) {
            fetches++;
            callback = value;
        }
    }

    private static final class RecordingResult implements StartupLocationController.Result {
        String city;
        int failures;
        @Override public void city(String value) { city = value; }
        @Override public void failed() { failures++; }
    }
}
