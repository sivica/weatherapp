package ivica.weatherapplication;

/** Coordinates optional startup location; approximate access is sufficient for a city. */
final class StartupLocationController {
    interface Result {
        void city(String city);
        void failed();
    }

    interface Gateway {
        boolean fineGranted();
        boolean coarseGranted();
        void requestPermissions();
        void findCity(Result result);
    }

    private final Gateway gateway;
    private final Result listener;
    private boolean active;
    private long generation;

    StartupLocationController(Gateway gateway, Result listener) {
        this.gateway = gateway;
        this.listener = listener;
    }

    void start(boolean weatherConfigured) {
        ++generation;
        active = weatherConfigured;
        if (!active) return;
        if (gateway.fineGranted() || gateway.coarseGranted()) fetch();
        else gateway.requestPermissions();
    }

    void permissionsResult(boolean fine, boolean coarse) {
        if (!active) return;
        if (fine || coarse) fetch();
        else {
            active = false;
            listener.failed();
        }
    }

    void stop() { active = false; ++generation; }

    private void fetch() {
        long request = generation;
        gateway.findCity(new Result() {
            @Override public void city(String city) {
                if (!active || request != generation) return;
                active = false;
                if (city == null || city.trim().isEmpty()) listener.failed();
                else listener.city(city.trim());
            }

            @Override public void failed() {
                if (!active || request != generation) return;
                active = false;
                listener.failed();
            }
        });
    }
}
