package br.com.rotacerta.app;

/** Aceita decimais brasileiro e inglês e evita interpretar 1.200 como R$ 1,20. */
public final class NumericInput {
    private NumericInput() {}
    public static double nonNegative(String source) {
        if (source == null) return 0;
        String raw=source.trim().replace(" ", "").replace("R$", "");
        if(raw.isEmpty()) return 0;
        int comma=raw.lastIndexOf(','), dot=raw.lastIndexOf('.');
        if (comma>=0 && dot>=0) {
            raw=comma>dot?raw.replace(".", "").replace(',', '.'):raw.replace(",", "");
        } else if(comma>=0) {
            raw=raw.replace(',', '.');
        } else if(dot>=0 && raw.matches("\\d{1,3}(\\.\\d{3})+")) {
            raw=raw.replace(".", "");
        }
        try {
            double x=Double.parseDouble(raw);
            return Double.isFinite(x)?Math.max(0,x):0;
        } catch (NumberFormatException e) { return 0; }
    }
}
