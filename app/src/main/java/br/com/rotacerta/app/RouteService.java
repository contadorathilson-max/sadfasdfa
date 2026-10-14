package br.com.rotacerta.app;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Consulta de rota real pela malha viária. Uso pessoal/demonstração da API pública OSRM. */
public final class RouteService {
    private RouteService() {}

    public static final class Point {
        public final double lat, lon;
        public final String label;
        public Point(double lat, double lon, String label) {
            this.lat = lat; this.lon = lon; this.label = label;
        }
        public String asPath() { return String.format(Locale.US, "%.6f,%.6f", lon, lat); }
        public String asMaps() { return String.format(Locale.US, "%.6f,%.6f", lat, lon); }
    }

    public static final class Result {
        public double distanceKm, durationMinutes;
        public List<Point> routePoints = new ArrayList<>();
        public String description;
        public boolean optimized;
    }

    public static List<String> parseStops(String raw) {
        List<String> result = new ArrayList<>();
        for (String s : raw.split("\\r?\\n")) {
            String t = s.trim();
            if (!t.isEmpty()) result.add(t);
        }
        return result;
    }

    public static Point geocode(Context context, String name) throws Exception {
        String s = name.trim();
        // Sugestão selecionada no app: Nome do lugar [latitude,longitude].
        // Mantém o nome legível e utiliza as coordenadas reais fornecidas pelo catálogo.
        java.util.regex.Matcher place = java.util.regex.Pattern.compile(
            "^(.*?)\\s+\\[(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)\\]$"
        ).matcher(s);
        if(place.matches()){
            double lat=Double.parseDouble(place.group(2)),lon=Double.parseDouble(place.group(3));
            if(lat>=-90&&lat<=90&&lon>=-180&&lon<=180)
                return new Point(lat,lon,place.group(1));
        }
        // Coordenadas informadas manualmente em 'latitude, longitude'.
        if (s.matches("-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?")) {
            String[] parts = s.split(",");
            double lat = Double.parseDouble(parts[0].trim());
            double lon = Double.parseDouble(parts[1].trim());
            if (lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180) return new Point(lat, lon, s);
            throw new Exception("Coordenadas fora do intervalo: " + s);
        }
        if (!Geocoder.isPresent()) throw new Exception("Geocodificação indisponível. Informe 'latitude, longitude'.");
        Geocoder g = new Geocoder(context, new Locale("pt", "BR"));
        @SuppressWarnings("deprecation")
        List<Address> matches = g.getFromLocationName(s, 1);
        if (matches == null || matches.isEmpty())
            throw new Exception("Endereço não encontrado: " + s + ". Tente cidade/estado ou coordenadas.");
        Address a = matches.get(0);
        return new Point(a.getLatitude(), a.getLongitude(), s);
    }

    /** Extrai coordenadas de sugestões salvas para abrir o Google Maps diretamente. */
    public static String asMapsTarget(String stop) {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile(
            "^.*?\\s+\\[(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)\\]$"
        ).matcher(stop.trim());
        return m.matches()?m.group(1)+","+m.group(2):stop;
    }

    public static Result calculate(Context context, String originText, Location gps, List<String> destinations,
                                   boolean roundtrip, boolean optimize) throws Exception {
        if (destinations.isEmpty()) throw new Exception("Informe ao menos um destino, um por linha.");
        if (destinations.size() > 8) throw new Exception("O limite é de 8 paradas por viagem.");
        Point origin = gps != null ? new Point(gps.getLatitude(), gps.getLongitude(), "Minha localização")
                : geocode(context, originText);
        List<Point> initial = new ArrayList<>();
        initial.add(origin);
        for (String place : destinations) initial.add(geocode(context, place));

        boolean canOptimize = optimize && destinations.size() >= 2;
        List<Point> requestPoints = new ArrayList<>(initial);
        if (!canOptimize && roundtrip) requestPoints.add(origin);
        StringBuilder coords = new StringBuilder();
        for (Point p : requestPoints) {
            if (coords.length() > 0) coords.append(';');
            coords.append(p.asPath());
        }
        String endpoint = canOptimize ? "trip" : "route";
        String options = canOptimize
                ? (roundtrip ? "?roundtrip=true&source=first&overview=false" :
                    "?roundtrip=false&source=first&destination=last&overview=false")
                : "?overview=false&steps=false&alternatives=false";
        URL u = new URL("https://router.project-osrm.org/" + endpoint + "/v1/driving/" + coords + options);
        HttpURLConnection connection = (HttpURLConnection) u.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "RotaCertaAndroid/1.0 (personal travel planner)");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(16000);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new Exception("Serviço de rotas indisponível (HTTP " + status + ").");
            StringBuilder json = new StringBuilder();
            try (InputStream in = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
            }
            JSONObject root = new JSONObject(json.toString());
            if (!"Ok".equals(root.optString("code")))
                throw new Exception("Não foi encontrada uma rota rodoviária entre todos os pontos.");
            JSONObject found = root.getJSONArray(canOptimize ? "trips" : "routes").getJSONObject(0);
            Result out = new Result();
            out.distanceKm = found.getDouble("distance") / 1000.0;
            out.durationMinutes = found.getDouble("duration") / 60.0;
            out.optimized = canOptimize;
            if (canOptimize) {
                JSONArray waypoints = root.getJSONArray("waypoints");
                List<Integer> order = new ArrayList<>();
                for (int i = 0; i < initial.size(); i++) order.add(i);
                order.sort(Comparator.comparingInt(i -> waypoints.optJSONObject(i).optInt("waypoint_index", i)));
                for (int i : order) out.routePoints.add(initial.get(i));
                if (roundtrip) out.routePoints.add(origin);
            } else out.routePoints.addAll(requestPoints);
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < out.routePoints.size(); i++) {
                if (i > 0) text.append("  →  ");
                text.append(out.routePoints.get(i).label);
            }
            out.description = text.toString();
            return out;
        } finally {
            connection.disconnect();
        }
    }
}
