package orbitscope.orbit;

/** Ground site used for "when can I see it?" pass predictions. */
public record Observer(String name, double latitudeDeg, double longitudeDeg, double altitudeM) {
    public static final Observer FREEPORT = new Observer(
            "Freeport, Bahamas",
            26.5333,
            -78.7000,
            10.0
    );
}
