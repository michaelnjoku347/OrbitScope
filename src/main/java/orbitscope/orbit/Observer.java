package orbitscope.orbit;

/** Where I'm standing. Default is home. */
public record Observer(String name, double latitudeDeg, double longitudeDeg, double altitudeM) {
    public static final Observer FREEPORT = new Observer(
            "Freeport, Bahamas",
            26.5333,
            -78.7000,
            10.0
    );
}
