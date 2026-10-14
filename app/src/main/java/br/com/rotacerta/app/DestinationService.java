package br.com.rotacerta.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Busca live por coordenadas. Google via proxy autenticado opcional, OSM como alternativa gratuita. */
public final class DestinationService {
    private DestinationService() {}
    public static final class Place {
        public final String name, detail, source;
        public final double lat, lon, km;
        Place(String name,String detail,String source,double lat,double lon,double baseLat,double baseLon) {
            this.name=name;this.detail=detail;this.source=source;this.lat=lat;this.lon=lon;
            this.km=distance(baseLat,baseLon,lat,lon);
        }
        // Nome legível + coordenadas exatas, para a rota não depender de geocodificar o nome.
        public String routeLabel() {return name+String.format(Locale.US," [%.6f,%.6f]",lat,lon);}
        public String display() {return name+"  •  "+String.format(new Locale("pt","BR"),"%.0f km",km)
                +(detail.isEmpty()?"":"\n"+detail);}
    }
    public static final class Outcome {
        public final List<Place> places;
        public final String provider;
        Outcome(List<Place> items,String source){places=items;provider=source;}
    }
    private static double distance(double a,double b,double x,double y){
        double dLat=Math.toRadians(x-a),dLon=Math.toRadians(y-b);
        double h=Math.pow(Math.sin(dLat/2),2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(x))*Math.pow(Math.sin(dLon/2),2);
        return 12742*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));
    }
    public static Outcome search(double lat,double lon,String proxyUrl,String token) throws Exception {
        if (proxyUrl!=null && proxyUrl.startsWith("https://")) {
            try {
                List<Place> google=searchGoogle(lat,lon,proxyUrl,token);
                if(google.size()>=30)return new Outcome(google,"Google Places");
                try {
                    List<Place> osm=searchOsm(lat,lon,30,google);
                    return new Outcome(osm,"Google Places + OpenStreetMap");
                }catch(Exception ignored) {
                    if(!google.isEmpty())return new Outcome(google,"Google Places (resultado parcial)");
                }
            }catch(Exception googleProblem) {
                // O usuario recebe a indicacao do provedor ativo na lista, sem disfarcar a origem.
            }
        }
        return new Outcome(searchOsm(lat,lon,30,new ArrayList<>()),"OpenStreetMap (Google nao configurado ou indisponivel)");
    }
    private static List<Place> searchGoogle(double lat,double lon,String proxy,String token) throws Exception {
        if (token==null || token.trim().isEmpty()) throw new Exception("Token do proxy nao configurado");
        URL url=new URL(proxy.replaceAll("/+$", "")+"/destinos?lat="+lat+"&lon="+lon);
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setConnectTimeout(11000);c.setReadTimeout(18000);
        c.setRequestProperty("Authorization","Bearer "+token);
        c.setRequestProperty("Accept","application/json");
        try {
            if(c.getResponseCode()!=200)throw new Exception("Google/proxy HTTP "+c.getResponseCode());
            JSONObject root=new JSONObject(read(c.getInputStream()));JSONArray a=root.optJSONArray("places");
            List<Place> out=new ArrayList<>(); if(a==null)return out;
            Map<String,Place> uniq=new LinkedHashMap<>();
            for(int i=0;i<a.length();i++){
                JSONObject p=a.optJSONObject(i);if(p==null)continue;
                String name=p.optString("name","").trim(),address=p.optString("address","").trim();
                double plat=p.optDouble("lat",Double.NaN),plon=p.optDouble("lon",Double.NaN);
                if(name.isEmpty()||!Double.isFinite(plat)||!Double.isFinite(plon))continue;
                Place item=new Place(name,address,"Google Places",plat,plon,lat,lon);
                uniq.putIfAbsent(name.toLowerCase(Locale.ROOT)+"/"+Math.round(plat*1000)+"/"+Math.round(plon*1000),item);
            }
            out.addAll(uniq.values());sort(out);return out;
        } finally {c.disconnect();}
    }
    /** Busca incremental em instâncias públicas atuais do Overpass.
     *  Nunca retorna exemplos artificiais: apenas atrações mapeadas com nome e coordenadas.
     */
    private static List<Place> searchOsm(double lat,double lon,int min,List<Place> first) throws Exception {
        Map<String,Place> places=new LinkedHashMap<>();
        for(Place p:first)places.put(key(p),p);
        Exception lastFailure=null;
        boolean completedAtLeastOneQuery=false;
        // A instância kumi.systems foi substituída por overpass.private.coffee.
        String[] servers={
                "https://overpass.private.coffee/api/interpreter",
                "https://overpass-api.de/api/interpreter"
        };
        // Consultas menores ajudam a reduzir timeout e sobrecarga dos servidores gratuitos.
        for(int radius:new int[]{18000,45000,90000}) {
            if(places.size()>=min)break;
            String query=buildOsmQuery(lat,lon,radius);
            for(String api:servers) {
                HttpURLConnection c=null;
                try {
                    String data="data="+URLEncoder.encode(query,"UTF-8");
                    c=(HttpURLConnection)new URL(api).openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(9000);
                    c.setReadTimeout(24000);
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
                    c.setRequestProperty("Accept","application/json");
                    c.setRequestProperty("User-Agent","RotaCerta-Android/3.1 (OpenStreetMap destinations; personal trip planner)");
                    try(java.io.OutputStream os=c.getOutputStream()) {
                        os.write(data.getBytes(StandardCharsets.UTF_8));
                    }
                    int status=c.getResponseCode();
                    if(status!=200)throw new Exception("Servidor de destinos respondeu HTTP "+status);
                    JSONArray a=new JSONObject(read(c.getInputStream())).optJSONArray("elements");
                    if(a==null)throw new Exception("Resposta de destinos sem resultados válidos");
                    completedAtLeastOneQuery=true;
                    for(int i=0;i<a.length();i++) {
                        JSONObject e=a.optJSONObject(i);if(e==null)continue;
                        JSONObject tags=e.optJSONObject("tags");if(tags==null)continue;
                        String name=tags.optString("name:pt",tags.optString("name","")).trim();
                        if(name.length()<3)continue;
                        JSONObject center=e.optJSONObject("center");
                        double x=e.has("lat")?e.optDouble("lat",Double.NaN):center==null?Double.NaN:center.optDouble("lat",Double.NaN);
                        double y=e.has("lon")?e.optDouble("lon",Double.NaN):center==null?Double.NaN:center.optDouble("lon",Double.NaN);
                        if(!Double.isFinite(x)||!Double.isFinite(y))continue;
                        String town=tags.optString("addr:city",tags.optString("is_in:city",""));
                        Place p=new Place(name,town,"OpenStreetMap",x,y,lat,lon);
                        if(p.km<=radius/1000.0+2)places.putIfAbsent(key(p),p);
                    }
                    lastFailure=null;
                    break; // consulta bem-sucedida neste servidor, passa para o próximo raio
                }catch(Exception e){ lastFailure=e; }
                finally {if(c!=null)c.disconnect();}
            }
        }
        if(places.isEmpty()&&!completedAtLeastOneQuery) {
            String extra=lastFailure==null?"Servidor sem resposta":lastFailure.getMessage();
            throw new Exception("Falha na consulta OpenStreetMap: "+extra+". Tente de novo com internet ativa.");
        }
        List<Place> result=new ArrayList<>(places.values());sort(result);
        if(result.size()>65)return new ArrayList<>(result.subList(0,65));
        return result;
    }
    static String buildOsmQuery(double lat,double lon,int radius) {
        String where=String.format(Locale.US,"(around:%d,%.6f,%.6f)",radius,lat,lon);
        return "[out:json][timeout:24];("+
                "nwr[\"name\"][\"tourism\"~\"^(attraction|museum|viewpoint|zoo|theme_park|gallery|aquarium|picnic_site)$\"]"+where+";"+
                "nwr[\"name\"][\"natural\"~\"^(beach|waterfall|cave_entrance|peak)$\"]"+where+";"+
                "nwr[\"name\"][\"leisure\"~\"^(park|nature_reserve|water_park)$\"]"+where+";"+
                "nwr[\"name\"][\"historic\"~\"^(castle|monument|fort|ruins|archaeological_site)$\"]"+where+";"+
                ");out center 250;";
    }
    private static String key(Place p){return p.name.toLowerCase(Locale.ROOT)+"/"+Math.round(p.lat*100)+"/"+Math.round(p.lon*100);}
    private static void sort(List<Place> p){Collections.sort(p,Comparator.comparingDouble(x->x.km));}
    private static String read(InputStream in)throws Exception{
        try(BufferedReader b=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
            StringBuilder s=new StringBuilder();String line;while((line=b.readLine())!=null) {
                s.append(line);if(s.length()>2500000)throw new Exception("Resposta excessiva");
            }return s.toString();
        }
    }
}
