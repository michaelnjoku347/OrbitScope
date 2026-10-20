package orbitscope.orbit;

public record Observer(String name, double latitudeDeg, double longitudeDeg, double altitudeM) {
    public static final Observer NEW_YORK = new Observer(
            "New York",
            43.0,
            -75.5,
            10.0
    );
}
