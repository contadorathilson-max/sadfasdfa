package br.com.rotacerta.app;

/** Tipo de transporte principal. Passagens são cotadas externamente e informadas pelo usuário. */
public final class TransportMode {
    public static final String CAR = "car";
    public static final String BUS = "bus";
    public static final String PLANE = "plane";
    private TransportMode() {}
    public static String normalize(String raw) {
        if (BUS.equals(raw)) return BUS;
        if (PLANE.equals(raw)) return PLANE;
        return CAR;
    }
    public static String label(String mode) {
        switch (normalize(mode)) {
            case BUS: return "Ônibus";
            case PLANE: return "Avião";
            default: return "Carro";
        }
    }
    public static boolean isCar(String mode) { return CAR.equals(normalize(mode)); }
}
