package br.com.rotacerta.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Rota Certa 3.2: planejamento multimodal com orçamento por transporte. */
public class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(19, 42, 59);
    private static final int INK = Color.rgb(34, 51, 65);
    private static final int MUTED = Color.rgb(111, 128, 142);
    private static final int TEAL = Color.rgb(8, 140, 122);
    private static final int AQUA = Color.rgb(216, 245, 236);
    private static final int PAGE = Color.rgb(245, 248, 250);
    private static final int LINE = Color.rgb(226, 233, 238);
    private static final int ORANGE = Color.rgb(251, 175, 98);
    private static final Locale BR = new Locale("pt", "BR");
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SharedPreferences prefs;
    private JSONObject draft = new JSONObject();
    private JSONArray expenses = new JSONArray();
    private JSONArray snapshots = new JSONArray();
    private LinearLayout content;
    private TextView totalView, perView, distanceView, fuelView, routeView, gpsView, spentView, restView;
    private final List<TextView> detailValues = new ArrayList<>();
    private final List<BudgetBar> detailBars = new ArrayList<>();
    private TextView fuelNotice, lodgingNotice, mealNotice, periodNotice, transportNotice;
    private BudgetBar spentBar;
    private final List<Integer> detailCategories = new ArrayList<>();
    private int activeTab = 0;
    private boolean loading = false, destinationPending = false, destinationLoading = false;
    private Location gps;
    private final Handler gpsHandler = new Handler(Looper.getMainLooper());
    private int gpsRequestId = 0;
    private RouteService.Result routeResult;
    private long routeRevision=0;
    private static final int BACKUP_EXPORT=91, BACKUP_IMPORT=92;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // Reinicia a versão anterior por solicitação: nenhum histórico prévio é reaproveitado.
        prefs = getSharedPreferences("rotacerta_v2", MODE_PRIVATE);
        if (!prefs.getBoolean("initialized", false)) {
            getSharedPreferences("rotacerta_v1", MODE_PRIVATE).edit().clear().apply();
            prefs.edit().putBoolean("initialized", true).apply();
        }
        draft = json(prefs.getString("draft", "{}"));
        migrateOldBudget();
        expenses = array(prefs.getString("expenses", "[]"));
        snapshots = array(prefs.getString("snapshots", "[]"));
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        render();
    }
    @Override protected void onPause() { super.onPause(); persist(); }
    @Override protected void onDestroy() {
        gpsRequestId++;gpsHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();super.onDestroy();
    }

    private JSONObject json(String raw) { try {return new JSONObject(raw);}catch(Exception e){return new JSONObject();} }
    private JSONArray array(String raw) { try {return new JSONArray(raw);}catch(Exception e){return new JSONArray();} }
    private void persist() { prefs.edit().putString("draft",draft.toString()).putString("expenses",expenses.toString())
            .putString("snapshots",snapshots.toString()).apply(); }
    private void put(String key,Object val) { try { draft.put(key,val); }catch(Exception ignored) {} }
    private String val(String key) {return draft.optString(key,"");}
    private int tripNights(){return TripPlanner.nights(val("departureDate"),val("returnDate"));}
    private int tripDays(){return TripPlanner.days(val("departureDate"),val("returnDate"));}
    private void migrateOldBudget(){
        if(!draft.has("lodgingTotal") && draft.has("roomRate")) {
            double total=parsed(draft.optString("roomRate",""))*parsed(draft.optString("rooms",""))*
                    Math.max(0,tripNights()<0?(int)parsed(draft.optString("nights","")):tripNights());
            if(total>0)put("lodgingTotal",String.valueOf(total));
        }
        draft.remove("roomRate");draft.remove("nights");
    }
    private boolean flag(String key,boolean def) {return draft.has(key)?draft.optBoolean(key,def):def;}
    private static double parsed(String s) { return NumericInput.nonNegative(s); }
    private double num(String key) {return parsed(val(key));}
    private int count(String key) {return Math.min(10000,(int)num(key));}
    private static String money(double n) { return NumberFormat.getCurrencyInstance(BR).format(n); }
    private static String km(double n) { return String.format(BR,"%,.1f",n); }
    private int dp(float v) { return (int)(getResources().getDisplayMetrics().density*v + .5f); }

    private GradientDrawable fill(int c,int r) {
        GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;
    }
    private GradientDrawable outline(int c,int r,int borderColor) {
        GradientDrawable d=fill(c,r);d.setStroke(dp(1),borderColor);return d;
    }
    private GradientDrawable heroBg() {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{NAVY,Color.rgb(23,75,83)});
        d.setCornerRadius(dp(24));return d;
    }
    private TextView txt(String text,int size,int color,boolean bold) {
        TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);
        v.setFontFeatureSettings("kern");
        if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        else v.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        v.setIncludeFontPadding(true);return v;
    }
    private LinearLayout col() {LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row() {LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void add(LinearLayout parent,View child,int top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);
    }
    private void twin(LinearLayout parent, View left, View right, int top) {
        LinearLayout r=row();
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-2,1);a.rightMargin=dp(6);r.addView(left,a);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,-2,1);b.leftMargin=dp(6);r.addView(right,b);
        add(parent,r,top);
    }
    private LinearLayout panel() {
        LinearLayout p=col();p.setPadding(dp(18),dp(18),dp(18),dp(19));
        p.setBackground(outline(Color.WHITE,21,LINE));
        add(content,p,11);return p;
    }
    private LinearLayout section(String title,String subtitle,String iconName) {
        LinearLayout head=row();
        Symbol symbol=new Symbol(iconName,TEAL);head.addView(symbol,new LinearLayout.LayoutParams(dp(23),dp(23)));
        LinearLayout titles=col();
        add(titles,txt(title,18,INK,true),0);
        if(!subtitle.isEmpty())add(titles,txt(subtitle,12,MUTED,false),4);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(9);head.addView(titles,tp);
        add(content,head,24);
        return panel();
    }
    private View spacer(int h){View s=new View(this);s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return s;}
    private void divider(LinearLayout parent) {View v=new View(this);v.setBackgroundColor(LINE);add(parent,v,17);
        LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)v.getLayoutParams();p.height=dp(1);v.setLayoutParams(p); }
    private LinearLayout button(String title,String icon, boolean primary,Runnable action) {
        LinearLayout b=row();b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14),dp(12),dp(14),dp(12));
        b.setMinimumHeight(dp(48));
        b.setBackground(primary?fill(TEAL,14):outline(Color.WHITE,14,LINE));
        if(icon!=null){Symbol s=new Symbol(icon,primary?Color.WHITE:TEAL);
            b.addView(s,new LinearLayout.LayoutParams(dp(19),dp(19)));}
        TextView t=txt(title,13,primary?Color.WHITE:INK,true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);if(icon!=null)lp.leftMargin=dp(9);
        b.addView(t,lp);b.setClickable(true);b.setOnClickListener(v->action.run());return b;
    }
    private void fullButton(LinearLayout parent,String title,String icon,boolean primary,Runnable action,int top) {
        add(parent,button(title,icon,primary,action),top);
    }
    private void chip(LinearLayout parent,String text) {
        TextView label=txt(text,11,Color.rgb(224,249,242),true);
        label.setPadding(dp(10),dp(7),dp(10),dp(7));
        label.setBackground(outline(Color.rgb(33,82,87),22,Color.rgb(55,106,111)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=dp(6);parent.addView(label,lp);
    }
    private void field(LinearLayout parent,String label,String key,String placeholder,boolean numeric,boolean multiline,boolean routeChange) {
        add(parent,txt(label,12,INK,true),15);
        EditText edit=new EditText(this);edit.setTextSize(15);edit.setTextColor(INK);
        edit.setHint(placeholder);edit.setHintTextColor(Color.rgb(157,170,180));
        edit.setSelectAllOnFocus(false);edit.setSingleLine(!multiline);
        if(multiline){edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            edit.setMinLines(3);edit.setGravity(Gravity.TOP|Gravity.START);}
        else if(numeric)edit.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        else edit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setPadding(dp(13),dp(12),dp(13),dp(12));edit.setBackground(outline(PAGE,12,LINE));
        edit.setText(val(key));
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) {
                put(key,s.toString());if(routeChange)invalidateRoute();updateStats();
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        add(parent,edit,7);
    }
    private LinearLayout dateField(String title,String key) {
        LinearLayout c=col();add(c,txt(title,12,INK,true),15);
        LinearLayout target=row();target.setPadding(dp(12),dp(12),dp(10),dp(12));
        target.setBackground(outline(PAGE,12,LINE));
        TextView chosen=txt(val(key).isEmpty()?"Escolher data":val(key),13,
                val(key).isEmpty()?MUTED:INK,false);
        target.addView(chosen,new LinearLayout.LayoutParams(0,-2,1));
        target.addView(new Symbol("calendar",TEAL),new LinearLayout.LayoutParams(dp(19),dp(19)));
        target.setOnClickListener(v->{
            Calendar now=Calendar.getInstance();
            String current=val(key);
            try {
                String[] p=current.split("/");
                if(p.length==3)now.set(Integer.parseInt(p[2]),Integer.parseInt(p[1])-1,Integer.parseInt(p[0]));
            }catch(Exception ignored){}
            DatePickerDialog dlg=new DatePickerDialog(this,(view,year,month,day)->{
                put(key,String.format(BR,"%02d/%02d/%04d",day,month+1,year));
                chosen.setText(val(key));chosen.setTextColor(INK);updateStats();persist();
            },now.get(Calendar.YEAR),now.get(Calendar.MONTH),now.get(Calendar.DAY_OF_MONTH));
            dlg.show();
        });
        add(c,target,7);return c;
    }
    private LinearLayout input(String label,String key,String hint,boolean number) {
        LinearLayout c=col();field(c,label,key,hint,number,false,false);return c;
    }
    private void switchLine(LinearLayout parent,String key,String label,String note,boolean def,boolean routeChange) {
        LinearLayout r=row();
        LinearLayout caption=col();add(caption,txt(label,14,INK,true),0);
        if(!note.isEmpty())add(caption,txt(note,11,MUTED,false),4);
        r.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);toggle.setChecked(flag(key,def));toggle.setShowText(false);
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
                new int[]{TEAL,Color.WHITE}));
        toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
                new int[]{Color.rgb(120,216,196),Color.rgb(198,207,214)}));
        toggle.setOnCheckedChangeListener((btn,checked)->{put(key,checked);if(routeChange)invalidateRoute();updateStats();});
        r.addView(toggle);add(parent,r,16);
    }

    private void render() {
        totalView=null;perView=null;distanceView=null;fuelView=null;routeView=null;gpsView=null;spentView=null;restView=null;
        detailValues.clear();detailCategories.clear();detailBars.clear();
        fuelNotice=null;lodgingNotice=null;mealNotice=null;periodNotice=null;transportNotice=null;spentBar=null;
        LinearLayout screen=col();screen.setBackgroundColor(PAGE);setContentView(screen);
        if(Build.VERSION.SDK_INT>=35)screen.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets b=insets.getInsets(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());
            screen.setPadding(b.left,b.top,b.right,b.bottom);return insets;
        });
        LinearLayout toolbar=row();toolbar.setPadding(dp(20),dp(17),dp(18),dp(17));toolbar.setBackgroundColor(NAVY);
        LinearLayout logo=new LinearLayout(this);logo.setGravity(Gravity.CENTER);logo.setBackground(fill(TEAL,13));
        logo.addView(new Symbol("route",Color.WHITE),new LinearLayout.LayoutParams(dp(25),dp(25)));
        toolbar.addView(logo,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout identity=col();add(identity,txt("ROTA CERTA",18,Color.WHITE,true),0);
        add(identity,txt("Sua viagem começa com um plano.",12,Color.rgb(182,210,214),false),3);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=dp(13);toolbar.addView(identity,ip);
        LinearLayout share=new LinearLayout(this);share.setPadding(dp(10),dp(10),dp(10),dp(10));
        share.addView(new Symbol("share",Color.WHITE),new LinearLayout.LayoutParams(dp(23),dp(23)));
        share.setOnClickListener(v->sharePlan());toolbar.addView(share);
        add(screen,toolbar,0);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);
        content=col();content.setPadding(dp(17),dp(12),dp(17),dp(35));scroll.addView(content);
        screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        drawBottom(screen);
        if(activeTab==0)drawRoutes();
        else if(activeTab==1)drawBudget();
        else if(activeTab==2)drawExpenses();
        else drawTrips();
        updateStats();
    }
    private void drawBottom(LinearLayout root) {
        LinearLayout nav=row();nav.setBackgroundColor(Color.WHITE);nav.setPadding(dp(5),dp(8),dp(5),dp(8));
        String[] titles={"Roteiro","Orçamento","Gastos","Viagens"};
        String[] icons={"map","wallet","receipt","suitcase"};
        for(int i=0;i<titles.length;i++) {
            final int index=i;boolean selected=i==activeTab;
            LinearLayout target=col();target.setGravity(Gravity.CENTER);target.setPadding(dp(1),dp(7),dp(1),dp(7));
            if(selected)target.setBackground(fill(AQUA,13));
            Symbol symbol=new Symbol(icons[i],selected?TEAL:MUTED);
            LinearLayout holder=row();holder.setGravity(Gravity.CENTER);
            holder.addView(symbol,new LinearLayout.LayoutParams(dp(22),dp(22)));
            target.addView(holder);
            TextView title=txt(titles[i],10,selected?TEAL:MUTED,selected);title.setGravity(Gravity.CENTER);
            add(target,title,4);
            LinearLayout.LayoutParams n=new LinearLayout.LayoutParams(0,dp(62),1);n.leftMargin=dp(3);n.rightMargin=dp(3);
            nav.addView(target,n);target.setOnClickListener(v->{activeTab=index;persist();render();});
        }
        View line=new View(this);line.setBackgroundColor(LINE);add(root,line,0);
        LinearLayout.LayoutParams l=(LinearLayout.LayoutParams)line.getLayoutParams();l.height=dp(1);line.setLayoutParams(l);
        add(root,nav,0);
    }
    private void hero(String eyebrow,String headline,String detail,boolean price) {
        LinearLayout card=col();card.setPadding(dp(23),dp(24),dp(23),dp(22));card.setBackground(heroBg());
        add(card,txt(eyebrow.toUpperCase(BR),11,Color.rgb(129,224,208),true),0);
        add(card,txt(headline,25,Color.WHITE,true),10);
        if(!detail.isEmpty())add(card,txt(detail,13,Color.rgb(196,220,224),false),8);
        if(price){
            add(card,spacer(12),2);
            totalView=txt(money(0),34,Color.WHITE,true);add(card,totalView,0);
            perView=txt(money(0)+" por pessoa",14,Color.rgb(141,232,207),true);add(card,perView,2);
            View v=new View(this);v.setBackgroundColor(Color.rgb(65,108,112));add(card,v,15);
            LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)v.getLayoutParams();p.height=dp(1);v.setLayoutParams(p);
            LinearLayout pills=row();chip(pills,"ORÇAMENTO AO VIVO");chip(pills,"SEM VALORES PRONTOS");add(card,pills,16);
        }
        add(content,card,0);
    }
    private String travelMode() { return TransportMode.normalize(val("transportMode")); }

    private void modeSelector(LinearLayout parent) {
        LinearLayout choices=row();
        String[] ids={TransportMode.CAR,TransportMode.BUS,TransportMode.PLANE};
        String[] icons={"car","bus","plane"};
        for(int i=0;i<ids.length;i++) {
            final String id=ids[i];
            boolean selected=travelMode().equals(id);
            LinearLayout tile=col();tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(8),dp(15),dp(8),dp(13));
            tile.setBackground(outline(selected?AQUA:PAGE,14,selected?TEAL:LINE));
            LinearLayout image=row();image.setGravity(Gravity.CENTER);
            image.addView(new Symbol(icons[i],selected?TEAL:MUTED),new LinearLayout.LayoutParams(dp(29),dp(29)));
            add(tile,image,0);
            TextView label=txt(TransportMode.label(id),13,selected?TEAL:INK,true);
            label.setGravity(Gravity.CENTER);add(tile,label,8);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(90),1);
            if(i>0)lp.leftMargin=dp(8);
            choices.addView(tile,lp);
            tile.setOnClickListener(v->{
                if(!travelMode().equals(id)) {
                    put("transportMode",id);
                    invalidateRoute();persist();render();
                }
            });
        }
        add(parent,choices,8);
    }

    private void openTicketSearch() {
        String target=TransportMode.PLANE.equals(travelMode())?
                "https://www.google.com/travel/flights":"https://www.google.com/maps/dir/?api=1&travelmode=transit";
        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(target)));}
        catch(Exception e){toast("Não foi possível abrir a pesquisa externa.");}
    }

    private void openDestinationMap() {
        List<String> stops=RouteService.parseStops(val("stops"));
        if(stops.isEmpty()) {toast("Informe pelo menos um destino.");return;}
        String place=RouteService.asMapsTarget(stops.get(stops.size()-1));
        String url="https://www.google.com/maps/search/?api=1&query="+encoded(place);
        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){toast("Não foi possível abrir o mapa.");}
    }

    private void drawRoutes() {
        hero("Novo planejamento","Para onde vamos?","Planeje seu roteiro de carro, ônibus ou avião, com custos específicos para cada opção.",false);
        LinearLayout travel=section("Como vamos viajar?","Selecione o transporte principal; você pode mudar depois.","route");
        modeSelector(travel);
        LinearLayout trip=section("Seu roteiro","Origem e destinos do trajeto","map");
        field(trip,"Nome da viagem","title","Ex.: Férias na praia",false,false,false);
        twin(trip,dateField("Data da ida","departureDate"),
                dateField("Data da volta","returnDate"),3);
        field(trip,"Quantidade de viajantes","persons","Ex.: 2",true,false,false);
        periodNotice=txt("Selecione as datas para calcular dias e noites.",12,TEAL,true);add(trip,periodNotice,9);
        field(trip,"Origem","origin","Cidade ou endereço de partida",false,false,true);
        gpsView=txt(gps==null?"GPS não ativado. Você pode usar o endereço acima.":"GPS conectado à localização atual.",12,MUTED,false);
        add(trip,gpsView,11);
        fullButton(trip,"Usar localização atual","pin",false,this::requestGps,11);
        field(trip,"Destinos / paradas (um por linha)","stops","Digite as cidades ou pontos que deseja visitar",false,true,true);
        fullButton(trip,"Descobrir destinos perto de mim","pin",false,this::chooseDestinations,11);
        fullButton(trip,"Buscar destinos pela cidade de origem","map",false,this::searchDestinationsFromOrigin,9);
        add(trip,txt("A busca usa o GPS ou a cidade informada acima. Resultados reais do OpenStreetMap; Google Places somente se configurado.",11,MUTED,false),9);
        divider(trip);
        switchLine(trip,"roundTrip","Incluir a volta","Conta a volta no roteiro de carro ou nas passagens de retorno",true,true);
        if(TransportMode.isCar(travelMode())) {
            switchLine(trip,"optimize","Otimizar paradas","Tentar reduzir o percurso entre as paradas",false,true);
            LinearLayout routeInfo=col();routeInfo.setPadding(dp(15),dp(14),dp(15),dp(14));
            routeInfo.setBackground(fill(Color.rgb(236,247,245),13));
            routeView=txt(draft.optString("routeDescription","Rota não calculada. Preencha os destinos e toque em calcular."),13,INK,false);
            add(routeInfo,routeView,0);add(trip,routeInfo,16);
            twin(trip,button("Calcular rota","route",true,this::calculateRoute),
                    button("Abrir Maps","map",false,this::openMaps),15);
            field(trip,"Distância manual (km) — opcional","manualKm","Ex.: 640",true,false,false);
            add(trip,txt("A rota usa OSRM/OpenStreetMap, sem trânsito ao vivo e sem garantia de evitar pedágios.",11,MUTED,false),13);
            LinearLayout car=section("Seu veículo","Informe apenas o carro, consumo e combustível","car");
            field(car,"Modelo do carro","car","Ex.: Hatch 1.0",false,false,false);
            twin(car,input("Consumo médio (km/l)","kmPerLiter","Ex.: 12,5",true),
                    input("Preço do combustível (R$/l)","literPrice","Ex.: 6,20",true),2);
            add(car,txt("Combustível estimado = distância ÷ consumo × preço do litro.",12,MUTED,false),14);
            fuelNotice=txt("",12,TEAL,true);add(car,fuelNotice,8);
        } else {
            LinearLayout help=col();help.setPadding(dp(14),dp(13),dp(14),dp(13));
            help.setBackground(fill(Color.rgb(236,247,245),13));
            add(help,txt(TransportMode.PLANE.equals(travelMode())?
                "Para voos, escolha os destinos e informe o preço das passagens na aba Orçamento.":
                "Para ônibus, informe o valor real da passagem de cada trecho na aba Orçamento.",13,INK,true),0);
            add(help,txt("O aplicativo não calcula preços ou disponibilidade de companhias em tempo real. Não utilizamos quilômetros de carro para estimar passagens.",12,MUTED,false),8);
            add(trip,help,14);
            twin(trip,button("Ver destino","map",true,this::openDestinationMap),
                button(TransportMode.PLANE.equals(travelMode())?"Pesquisar voos":"Ver ônibus","arrow",false,this::openTicketSearch),13);
        }
        LinearLayout summary=section("Estimativa de deslocamento","Custos calculados com seus valores","route");
        distanceView=txt("—",17,INK,true);fuelView=txt(money(0),17,TEAL,true);
        twin(summary,miniMetric(TransportMode.isCar(travelMode())?"DISTÂNCIA":"VIAJANTES",distanceView),
                miniMetric(TransportMode.isCar(travelMode())?"COMBUSTÍVEL":"TRANSPORTE",fuelView),0);
        fullButton(summary,"Continuar para o orçamento","arrow",true,()->{activeTab=1;render();},17);
    }
    private LinearLayout miniMetric(String title,TextView value) {
        LinearLayout c=col();c.setPadding(dp(12),dp(12),dp(10),dp(11));c.setBackground(fill(PAGE,13));
        add(c,txt(title,10,MUTED,true),0);add(c,value,6);return c;
    }
    private void drawBudget() {
        hero("Tudo sob controle","Seu orçamento","Informe valores uma vez. Datas e viajantes alimentam todos os cálculos.",true);
        LinearLayout people=section("Período e viajantes","Dados vinculados ao roteiro","calendar");
        int nights=tripNights(),days=tripDays(),travelers=count("persons");
        TextView dates=txt(nights<0?"Selecione as datas de ida e volta na aba Roteiro."
                :days+" dia(s) • "+nights+" noite(s) • "+(travelers>0?travelers+" pessoa(s)":"Informe os viajantes"),15,INK,true);
        add(people,dates,0);
        fullButton(people,"Alterar datas ou viajantes","calendar",false,()->{activeTab=0;render();},13);
        LinearLayout transit=section("Transporte · "+TransportMode.label(travelMode()),
            TransportMode.isCar(travelMode())?"Estimativa a partir do consumo do carro":"Passagens e despesas de deslocamento", "route");
        if(TransportMode.isCar(travelMode())) {
            add(transit,txt("O combustível usa a distância, o consumo em km/l e o valor do litro informados no Roteiro.",13,MUTED,false),0);
            fullButton(transit,"Ver dados do carro","car",false,()->{activeTab=0;render();},11);
        } else {
            add(transit,txt("Informe os preços POR PESSOA, para cada trecho. O valor total das passagens será multiplicado pelos viajantes.",12,MUTED,false),0);
            twin(transit,input("Passagem de ida / pessoa","fareOutbound","R$ 0,00",true),
                input("Passagem de volta / pessoa","fareReturn","R$ 0,00",true),10);
            add(transit,txt(flag("roundTrip",true)?"Ida e volta ativadas no Roteiro.":
                "Viagem sem volta: a passagem de retorno não entra no total.",12,TEAL,true),10);
            field(transit,"Bagagens extras / pessoa (total da viagem)","baggagePerPerson","R$ 0,00",true,false,false);
            twin(transit,input("Traslados para aeroportos/rodoviárias (TOTAL)","transferTotal","R$ 0,00",true),
                input("Transporte no destino (TOTAL)","localTransportTotal","R$ 0,00",true),5);
            field(transit,"Taxas adicionais de viagem (TOTAL)","transportFeesTotal","R$ 0,00",true,false,false);
            add(transit,txt("Traslados, transporte local e taxas são totais do grupo, não por pessoa. Preços e disponibilidade devem ser confirmados no operador.",12,MUTED,false),9);
        }
        transportNotice=txt("",14,TEAL,true);add(transit,transportNotice,13);
        LinearLayout lodging=section("Hospedagem","Informe o total combinado de toda a estadia","suitcase");
        field(lodging,"Valor TOTAL da hospedagem (R$)","lodgingTotal","Ex.: 600,00",true,false,false);
        field(lodging,"Quantidade de quartos (opcional)","rooms","Ex.: 1",true,false,false);
        lodgingNotice=txt("",13,TEAL,true);add(lodging,lodgingNotice,13);
        add(lodging,txt("O valor informado já é o total da estadia. Não será multiplicado outra vez pelas noites ou quartos.",11,MUTED,false),8);
        LinearLayout food=section("Alimentação inteligente","Informe somente quanto custa cada refeição por pessoa","receipt");
        switchLine(food,"breakfastIncluded","Café da manhã incluso na hospedagem",
                "Ao preencher automaticamente, os cafés serão zerados",false,false);
        fullButton(food,"Preencher refeições automaticamente","check",true,this::autoFillMeals,14);
        mealNotice=txt("",12,MUTED,false);add(food,mealNotice,9);
        twin(food,input("Café da manhã / pessoa","breakfastPrice","R$ 0,00",true),
                input("Quantidade de cafés","breakfastCount","Automático",true),0);
        twin(food,input("Almoço / pessoa","lunchPrice","R$ 0,00",true),
                input("Quantidade de almoços","lunchCount","Automático",true),0);
        twin(food,input("Jantar / pessoa","dinnerPrice","R$ 0,00",true),
                input("Quantidade de jantares","dinnerCount","Automático",true),0);
        twin(food,input("Lanches / pessoa / dia","snackPrice","R$ 0,00",true),
                input("Dias com lanches","snackDays","Automático",true),0);
        add(food,txt("Sugestão: café por noite (exceto quando incluso), almoço por dia, jantar por noite e lanches por dia. Você pode ajustar manualmente.",11,MUTED,false),12);
        LinearLayout extra=section("Outras despesas","Valores totais para o grupo","wallet");
        twin(extra,input("Passeios e entradas","attractions","R$ 0,00",true),
                input("Estacionamentos","parking","R$ 0,00",true),0);
        twin(extra,input("Pedágios","tolls","R$ 0,00",true),
                input("Outros gastos","other","R$ 0,00",true),0);
        field(extra,"Reserva para imprevistos (%)","contingency","Ex.: 10",true,false,false);
        LinearLayout breakdown=section("Resumo por categoria","Valores recalculados automaticamente","wallet");
        String[] labels=TransportMode.isCar(travelMode())?
            new String[]{"Combustível","Hospedagem","Alimentação","Passeios","Estacionamento","Pedágios","Outros","Imprevistos"}:
            new String[]{"Passagens","Bagagens","Traslados","Transporte local","Taxas de viagem",
                "Hospedagem","Alimentação","Passeios","Estacionamento","Pedágios","Outros","Imprevistos"};
        for(int i=0;i<labels.length;i++) {
            LinearLayout line=row();TextView caption=txt(labels[i],13,MUTED,false),cost=txt(money(0),13,INK,true);
            line.addView(caption,new LinearLayout.LayoutParams(0,-2,1));line.addView(cost);
            add(breakdown,line,13);detailCategories.add(i);detailValues.add(cost);
            BudgetBar bar=new BudgetBar(i==0?TEAL:i==1?Color.rgb(74,118,184):i==2?ORANGE:Color.rgb(113,180,175));
            add(breakdown,bar,7);detailBars.add(bar);
        }
        divider(breakdown);
        fullButton(breakdown,"Compartilhar orçamento","share",true,this::sharePlan,14);
    }
    private void drawExpenses() {
        hero("Gastos na prática","Acompanhe tudo","Registre despesas feitas e saiba quanto do orçamento ainda resta.",false);
        LinearLayout summary=section("Controle da viagem","Previsão e despesas realizadas","wallet");
        spentView=txt(money(0)+" gastos até agora",24,INK,true);add(summary,spentView,0);
        restView=txt("Orçamento restante: "+money(0),14,TEAL,true);add(summary,restView,7);
        spentBar=new BudgetBar(TEAL);add(summary,spentBar,13);
        fullButton(summary,"Adicionar uma despesa","plus",true,this::addExpenseDialog,16);
        LinearLayout entries=section("Lançamentos","Toque em qualquer item para excluí-lo","receipt");
        if(expenses.length()==0)empty(entries,"Ainda não há gastos lançados.","Quando registrar uma despesa, ela aparecerá aqui.");
        for(int i=expenses.length()-1;i>=0;i--) {
            JSONObject x=expenses.optJSONObject(i);if(x==null)continue;
            int index=i;
            LinearLayout item=row();item.setPadding(dp(13),dp(13),dp(12),dp(13));item.setBackground(fill(PAGE,13));
            LinearLayout label=col();add(label,txt(x.optString("title","Despesa"),14,INK,true),0);
            add(label,txt(x.optString("category","Outros")+" • "+x.optString("date",""),11,MUTED,false),5);
            item.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            item.addView(txt(money(x.optDouble("amount",0)),14,TEAL,true));
            item.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Remover gasto?")
                    .setMessage("O lançamento será excluído desta viagem.").setNegativeButton("Cancelar",null)
                    .setPositiveButton("Remover",(d,w)->{expenses.remove(index);persist();render();}).show());
            add(entries,item,9);
        }
        fullButton(entries,"Compartilhar fechamento","share",false,this::sharePlan,18);
    }
    private void drawTrips() {
        hero("Tudo organizado","Minhas viagens","Salve novos planejamentos ou comece outro do zero.",false);
        LinearLayout plans=section("Planejamentos salvos","Esta versão começa sem nenhuma viagem salva","suitcase");
        fullButton(plans,"Salvar viagem atual","plus",true,this::saveSnapshot,0);
        if(snapshots.length()==0)empty(plans,"Nenhuma viagem salva","Ao salvar uma viagem pela primeira vez, ela aparece nesta área.");
        for(int i=snapshots.length()-1;i>=0;i--) {
            JSONObject item=snapshots.optJSONObject(i);if(item==null)continue;
            final int index=i;
            LinearLayout itemCard=col();itemCard.setPadding(dp(15),dp(14),dp(15),dp(14));itemCard.setBackground(fill(PAGE,14));
            add(itemCard,txt(item.optString("title","Minha viagem"),16,INK,true),0);
            add(itemCard,txt(new SimpleDateFormat("dd/MM/yyyy • HH:mm",BR).format(new Date(item.optLong("created",0))),12,MUTED,false),5);
            twin(itemCard,button("Abrir","arrow",true,()->{
                draft=json(item.optJSONObject("draft")==null?"{}":item.optJSONObject("draft").toString());
                expenses=array(item.optJSONArray("expenses")==null?"[]":item.optJSONArray("expenses").toString());
                gps=null;routeResult=null;routeRevision++;migrateOldBudget();activeTab=0;persist();render();
            }),button("Excluir","trash",false,()->new AlertDialog.Builder(this)
                    .setTitle("Excluir viagem salva?")
                    .setNegativeButton("Cancelar",null)
                    .setPositiveButton("Excluir",(d,w)->{snapshots.remove(index);persist();render();}).show()),12);
            add(plans,itemCard,12);
        }
        LinearLayout checklist=section("Checklist para sair","Confirme o essencial antes de viajar","check");
        String[] items=TransportMode.isCar(travelMode())?
            new String[]{"Documentos e CNH","Pneus, estepe e calibragem","Óleo, água e combustível",
                "Hospedagem e reservas","Carregador e cartões","Água, protetor solar e remédios"}:
            new String[]{"Documento oficial para embarque","Passagens e horários confirmados",
                "Bagagem dentro das regras do operador","Traslado até aeroporto ou rodoviária",
                "Hospedagem e reservas","Carregador, cartões e itens pessoais"};
        for(int i=0;i<items.length;i++) {
            int j=i;String key="check_"+travelMode()+"_"+j;
            switchLinePrefs(checklist,key,items[i]);
        }
        LinearLayout settings=section("Recomeçar do zero","Apague os dados inseridos nesta versão","trash");
        add(settings,txt("O app foi entregue sem roteiro, histórico de consumo ou viagens preenchidas. Você pode limpar tudo novamente quando quiser.",12,MUTED,false),0);
        fullButton(settings,"Configurar sugestões do Google","pin",false,this::googleSettings,12);
        fullButton(settings,"Exportar backup das viagens","share",false,this::exportBackup,13);
        fullButton(settings,"Restaurar backup (.json)","suitcase",false,this::importBackup,9);
        add(settings,txt("Guarde o backup fora do app antes de desinstalar ou trocar de APK. O arquivo pode conter dados pessoais dos roteiros, mas não inclui tokens do Google.",12,MUTED,false),10);
        fullButton(settings,"Limpar roteiro, despesas e viagens","trash",false,()->new AlertDialog.Builder(this)
                .setTitle("Apagar todos os dados?")
                .setMessage("O planejamento atual, os gastos e as viagens salvas serão excluídos do aparelho.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Apagar tudo",(d,w)->resetAll()).show(),15);
    }
    private void switchLinePrefs(LinearLayout parent,String key,String label) {
        Switch s=new Switch(this);s.setText(label);s.setTextColor(INK);s.setTextSize(13);
        s.setPadding(0,dp(5),0,dp(5));s.setChecked(prefs.getBoolean(key,false));
        s.setOnCheckedChangeListener((btn,checked)->prefs.edit().putBoolean(key,checked).apply());add(parent,s,5);
    }
    private void empty(LinearLayout parent,String title,String subtitle) {
        LinearLayout c=col();c.setPadding(dp(14),dp(23),dp(14),dp(23));c.setGravity(Gravity.CENTER);
        c.setBackground(fill(PAGE,14));
        TextView titleView=txt(title,15,INK,true);titleView.setGravity(Gravity.CENTER);add(c,titleView,0);
        TextView desc=txt(subtitle,12,MUTED,false);desc.setGravity(Gravity.CENTER);add(c,desc,8);
        add(parent,c,15);
    }
    private void resetAll() {
        prefs.edit().clear().putBoolean("initialized",true).apply();
        draft=new JSONObject();expenses=new JSONArray();snapshots=new JSONArray();gps=null;routeResult=null;
        routeRevision++;destinationPending=false;
        activeTab=0;render();toast("Tudo limpo. Pronto para uma nova viagem!");
    }
    private TripMath.Input inputData() {
        TripMath.Input a=new TripMath.Input();
        a.transportMode=travelMode();a.roundTrip=flag("roundTrip",true);
        a.outboundFarePerPerson=num("fareOutbound");a.returnFarePerPerson=num("fareReturn");
        a.baggagePerPerson=num("baggagePerPerson");a.transfersTotal=num("transferTotal");
        a.localTransportTotal=num("localTransportTotal");a.transportFeesTotal=num("transportFeesTotal");
        a.km=num("manualKm")>0?num("manualKm"):num("routeKm");
        a.kmPerLiter=num("kmPerLiter");a.literPrice=num("literPrice");
        a.persons=Math.max(1,count("persons"));a.nights=Math.max(0,tripNights());
        a.rooms=Math.max(1,count("rooms"));a.roomPerNight=TripPlanner.lodgingPerRoomNight(num("lodgingTotal"),a.nights,a.rooms);
        a.lodgingTotal=num("lodgingTotal");
        a.breakfastPerPerson=num("breakfastPrice");a.breakfasts=count("breakfastCount");
        a.lunchPerPerson=num("lunchPrice");a.lunches=count("lunchCount");
        a.dinnerPerPerson=num("dinnerPrice");a.dinners=count("dinnerCount");
        a.snacksPerPerson=num("snackPrice");a.snackDays=count("snackDays");
        a.attractions=num("attractions");a.parking=num("parking");a.tolls=num("tolls");
        a.other=num("other");a.contingencyPercent=num("contingency");return a;
    }
    private double actualTotal() {
        double sum=0;for(int i=0;i<expenses.length();i++) {
            JSONObject x=expenses.optJSONObject(i);if(x!=null)sum+=Math.max(0,x.optDouble("amount",0));
        }return sum;
    }
    private void updateStats() {
        TripMath.Input i=inputData();TripMath.Result r=TripMath.calculate(i);
        if(totalView!=null)totalView.setText(money(r.total));
        if(perView!=null)perView.setText(money(r.perPerson)+" por pessoa");
        if(distanceView!=null)distanceView.setText(TransportMode.isCar(travelMode())?
                (i.km>0?km(i.km)+" km":"A calcular"):i.persons+" pessoa(s)");
        if(fuelView!=null)fuelView.setText(money(r.transport));
        if(transportNotice!=null)transportNotice.setText(TransportMode.isCar(travelMode())?
                "Combustível estimado: "+money(r.fuel):
                (i.outboundFarePerPerson<=0?"Informe o preço da passagem de ida para evitar um orçamento incompleto.\n":"")+
                (i.roundTrip&&i.returnFarePerPerson<=0?"Informe também a passagem de volta.\n":"")+
                "Transporte previsto para o grupo: "+money(r.transport)
                +" • "+money(r.transport/Math.max(1,i.persons))+" por pessoa");
        if(spentView!=null)spentView.setText(money(actualTotal())+" gastos até agora");
        if(restView!=null)restView.setText("Saldo estimado: "+money(r.total-actualTotal()));
        if(spentBar!=null)spentBar.setRatio(r.total>0?(float)(actualTotal()/r.total):0f);
        if(periodNotice!=null)periodNotice.setText(tripNights()<0?
                "Defina datas válidas para calcular dias e noites.":
                tripDays()+" dia(s) • "+tripNights()+" noite(s) • "+(count("persons")>0?count("persons")+" pessoa(s)":"informe as pessoas"));
        if(lodgingNotice!=null)lodgingNotice.setText(tripNights()<0?
                "Informe datas válidas no Roteiro para calcular a média por noite.":
                tripNights()==0 ? "Bate-volta: 0 noites. Verifique se haverá hospedagem." :
                "Média: "+money(TripPlanner.lodgingPerNight(num("lodgingTotal"),tripNights()))+
                " por noite"+(count("rooms")>0?" • "+money(TripPlanner.lodgingPerRoomNight(num("lodgingTotal"),tripNights(),count("rooms")))+" por quarto/noite":""));
        if(mealNotice!=null)mealNotice.setText(tripNights()<0?
                "Defina o período na aba Roteiro para automatizar as quantidades.":
                tripDays()+" dia(s), "+tripNights()+" noite(s), "+Math.max(1,count("persons"))+" viajante(s). Toque em preencher para aplicar.");
        if(fuelNotice!=null)fuelNotice.setText(i.km>0 && (i.kmPerLiter<=0 || i.literPrice<=0)
                ? "Preencha consumo e preço do litro para incluir combustível no total."
                : i.km<=0?"Calcule uma rota ou informe a distância para estimar o combustível.":"");
        double[] cats=TransportMode.isCar(travelMode())?
            new double[]{r.fuel,r.lodging,r.food,r.activities,r.parking,r.tolls,r.other,r.contingency}:
            new double[]{r.tickets,r.baggage,r.transfers,r.localTransport,r.travelFees,r.lodging,r.food,
                r.activities,r.parking,r.tolls,r.other,r.contingency};
        for(int j=0;j<detailValues.size();j++) {
            double category=cats[detailCategories.get(j)];
            detailValues.get(j).setText(money(category));
            detailBars.get(j).setRatio(r.total>0?(float)(category/r.total):0f);
        }
    }
    private void invalidateRoute() {
        routeRevision++;
        draft.remove("routeKm");draft.remove("routeMinutes");draft.remove("routeDescription");routeResult=null;
        if(routeView!=null)routeView.setText("Rota alterada. Toque em calcular novamente.");
    }
    private void autoFillMeals() {
        int nights=tripNights();
        if(nights<0){
            new AlertDialog.Builder(this).setTitle("Período necessário")
                    .setMessage("Escolha a ida e a volta na aba Roteiro para calcular automaticamente as refeições.")
                    .setPositiveButton("Ir ao roteiro",(d,w)->{activeTab=0;render();})
                    .setNegativeButton("Cancelar",null).show();return;
        }
        if(count("persons")<1){toast("Informe a quantidade de pessoas na aba Roteiro.");return;}
        TripPlanner.Meals m=TripPlanner.suggestMeals(nights,flag("breakfastIncluded",false));
        put("breakfastCount",String.valueOf(m.breakfast));
        put("lunchCount",String.valueOf(m.lunch));
        put("dinnerCount",String.valueOf(m.dinner));
        put("snackDays",String.valueOf(m.snacks));
        persist();render();toast("Refeições preenchidas. Ajuste as quantidades se necessário.");
    }
    private void chooseDestinations() {
        if(destinationLoading){toast("Aguarde a busca atual.");return;}
        destinationPending=true;
        if(Build.VERSION.SDK_INT>=23 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},43);
        }else fetchGps(); // Sempre renovar coordenadas antes de pesquisar
    }
    private void searchDestinations(Location current) {
        if(!destinationPending)return;
        destinationPending=false;destinationLoading=true;
        toast("Buscando locais para visitar na região...");
        if(gpsView!=null)gpsView.setText("Consultando atrações próximas. A busca pode levar alguns segundos...");
        final String proxy=prefs.getString("google_proxy_url","");
        final String token=prefs.getString("google_proxy_token","");
        executor.execute(()->{
            try {
                DestinationService.Outcome result=DestinationService.search(current.getLatitude(),current.getLongitude(),proxy,token);
                runOnUiThread(()->{destinationLoading=false;
                    if(gpsView!=null)gpsView.setText("Busca concluída: "+result.places.size()+" destinos encontrados.");
                    showDestinations(result);});
            }catch(Exception e){runOnUiThread(()->{destinationLoading=false;
                if(gpsView!=null)gpsView.setText("Não foi possível consultar os destinos. Tente novamente.");
                new AlertDialog.Builder(this).setTitle("Destinos indisponíveis")
                    .setMessage("Não conseguimos consultar os serviços de lugares. Verifique a internet e tente novamente.\n\n"+e.getMessage())
                    .setPositiveButton("OK",null).show();});}
        });
    }
    private void showDestinations(DestinationService.Outcome result) {
        List<DestinationService.Place> options=result.places;
        if(options.isEmpty()){toast("Nenhuma atração encontrada nesta região. Tente novamente em outra área.");return;}
        String[] names=new String[options.size()];boolean[] selected=new boolean[options.size()];
        List<String> existing=RouteService.parseStops(val("stops"));
        for(int i=0;i<options.size();i++){
            names[i]=options.get(i).display();selected[i]=existing.contains(options.get(i).routeLabel());
        }
        new AlertDialog.Builder(this).setTitle(options.size()+" destinos • "+result.provider)
                .setMultiChoiceItems(names,selected,(d,which,on)->selected[which]=on)
                .setNeutralButton("Configurar Google",(d,w)->googleSettings())
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Adicionar ao roteiro",(d,w)->{
                    List<String> stops=RouteService.parseStops(val("stops"));
                    for(int i=0;i<options.size();i++) {
                        String name=options.get(i).routeLabel();
                        if(selected[i]&&!stops.contains(name))stops.add(name);
                        if(!selected[i])stops.remove(name);
                    }
                    if(stops.size()>8){
                        new AlertDialog.Builder(this).setTitle("Muitas paradas")
                            .setMessage("Você selecionou "+stops.size()+" destinos. Escolha até 8 paradas por roteiro para calcular a rota. Você pode salvar mais de uma viagem.")
                            .setPositiveButton("Entendi",null).show();
                        return;
                    }
                    put("stops",String.join("\n",stops));invalidateRoute();persist();render();
                })
                .show();
    }
    private void googleSettings() {
        LinearLayout form=col();form.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText endpoint=new EditText(this);endpoint.setText(prefs.getString("google_proxy_url",""));
        endpoint.setSingleLine(true);endpoint.setHint("https://seu-worker.workers.dev");add(form,txt("Endereço HTTPS do seu proxy Google Places",13,INK,true),0);add(form,endpoint,7);
        EditText token=new EditText(this);token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setHint("Token do proxy");token.setText(prefs.getString("google_proxy_token",""));
        add(form,txt("Token de acesso",13,INK,true),15);add(form,token,7);
        add(form,txt("O Google requer API e faturamento ativos. A chave Google fica no servidor, nunca no código Android. Sem proxy, use as sugestões abertas do OpenStreetMap.",12,MUTED,false),15);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Google Places (opcional)")
            .setView(form)
            .setNeutralButton("Limpar",(d,w)->{
                prefs.edit().remove("google_proxy_url").remove("google_proxy_token").apply();
                toast("Google desconectado. OSM continua disponível.");
            })
            .setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String url=endpoint.getText().toString().trim();
            if(!url.isEmpty()&&!url.startsWith("https://")){
                endpoint.setError("O endereço deve usar HTTPS.");return;
            }
            prefs.edit().putString("google_proxy_url",url)
                .putString("google_proxy_token",token.getText().toString().trim()).apply();
            dialog.dismiss();toast("Configuração salva. Atualize as sugestões para usar Google Places.");
        }));
        dialog.show();
    }
    private void calculateRoute() {
        if(!TransportMode.isCar(travelMode())) {toast("Rotas rodoviárias de carro só estão disponíveis no modo Carro.");return;}
        if(loading){toast("Aguarde a consulta da rota.");return;}
        List<String> stops=RouteService.parseStops(val("stops"));
        if(stops.isEmpty()){toast("Informe pelo menos um destino.");return;}
        if(gps==null && val("origin").trim().isEmpty()){
            toast("Informe a cidade de partida ou use o GPS.");return;
        }
        final String origin=val("origin");
        final Location loc=origin.trim().isEmpty()?gps:null;
        final boolean roundtrip=flag("roundTrip",true),optimize=flag("optimize",false);
        final long requestRevision=routeRevision;
        loading=true;if(routeView!=null)routeView.setText("Calculando rota rodoviária…");
        executor.execute(()->{
            try {
                RouteService.Result res=RouteService.calculate(getApplicationContext(),origin,loc,stops,roundtrip,optimize);
                runOnUiThread(()->{
                    loading=false;
                    if(requestRevision!=routeRevision || !TransportMode.isCar(travelMode())) return;
                    routeResult=res;put("routeKm",res.distanceKm);put("routeMinutes",res.durationMinutes);
                    String desc=km(res.distanceKm)+" km • "+km(res.durationMinutes/60)+" h de deslocamento estimado"
                            +(res.optimized?" • paradas otimizadas":"")+"\n"+res.description;
                    put("routeDescription",desc);if(routeView!=null)routeView.setText(desc);
                    updateStats();persist();toast("Rota calculada!");
                });
            }catch(Exception e) {
                runOnUiThread(()->{
                    loading=false;
                    if(requestRevision!=routeRevision || !TransportMode.isCar(travelMode())) return;
                    invalidateRoute();
                    if(routeView!=null)routeView.setText("Não foi possível calcular: "+e.getMessage()+
                            "\nInforme a distância manualmente se preferir.");
                    updateStats();
                });
            }
        });
    }
    /** Busca pela cidade escrita em Origem, útil se GPS estiver sem sinal. */
    private void searchDestinationsFromOrigin() {
        if(destinationLoading){toast("Aguarde a busca atual.");return;}
        String origin=val("origin").trim();
        if(origin.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Informe uma cidade")
                .setMessage("No campo Origem, informe uma cidade com estado, por exemplo: Camaçari, BA. Depois toque em Buscar destinos pela cidade de origem.")
                .setPositiveButton("Entendi",null).show();
            return;
        }
        destinationPending=true;
        if(gpsView!=null)gpsView.setText("Localizando a cidade informada...");
        executor.execute(()->{
            try {
                RouteService.Point point=RouteService.geocode(getApplicationContext(),origin);
                Location city=new Location("origem-informada");
                city.setLatitude(point.lat);city.setLongitude(point.lon);
                runOnUiThread(()->searchDestinations(city));
            } catch(Exception e){runOnUiThread(()->{
                destinationPending=false;
                if(gpsView!=null)gpsView.setText("Não foi possível encontrar a cidade de origem.");
                new AlertDialog.Builder(this).setTitle("Origem não encontrada")
                    .setMessage("Confira o nome da cidade e o estado. Você também pode informar latitude, longitude.\n\n"+e.getMessage())
                    .setPositiveButton("OK",null).show();
            });}
        });
    }
    private void requestGps() {
        if(Build.VERSION.SDK_INT>=23 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},42);
        }else fetchGps();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==42 || request==43) {
            boolean granted=false;for(int g:grants)if(g==PackageManager.PERMISSION_GRANTED)granted=true;
            if(granted)fetchGps();
            else locationUnavailable("A localização não foi autorizada. Você pode usar a cidade digitada no campo Origem.");
        }
    }
    @SuppressWarnings("MissingPermission")
    private void fetchGps() {
        try {
            LocationManager lm=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
            if(lm==null){locationUnavailable("Serviço de localização indisponível.");return;}
            boolean fine=Build.VERSION.SDK_INT<23 ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
            boolean net=lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            boolean satellite=fine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if(!net&&!satellite){locationUnavailable("Ative a localização do celular e tente novamente.");return;}
            Location cached=null;
            for(String provider:new String[]{LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER}) {
                if(provider.equals(LocationManager.GPS_PROVIDER)&&!satellite)continue;
                if(provider.equals(LocationManager.NETWORK_PROVIDER)&&!net)continue;
                Location item=lm.getLastKnownLocation(provider);
                if(item!=null && (cached==null||item.getTime()>cached.getTime()))cached=item;
            }
            long age=cached==null?Long.MAX_VALUE:System.currentTimeMillis()-cached.getTime();
            if(cached!=null && age>=0 && age<=120000){setGps(cached);return;}
            int id=++gpsRequestId;
            final Location recent=cached;
            if(gpsView!=null)gpsView.setText("Obtendo GPS... aguarde até 20 segundos.");
            gpsHandler.postDelayed(()->{
                if(gpsRequestId==id){gpsRequestId++;
                    finishGpsUnavailable(recent,"O GPS demorou mais que o esperado.");
                }
            },20000);
            String provider=net?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;
            String fallback=net&&satellite?LocationManager.GPS_PROVIDER:null;
            requestProviderLocation(lm,provider,fallback,id,recent);
        } catch(Exception e){locationUnavailable("Falha ao consultar GPS: "+e.getMessage());}
    }
    @SuppressWarnings("MissingPermission")
    private void requestProviderLocation(LocationManager lm,String provider,String fallback,int id,Location recent) {
        try {
            if(Build.VERSION.SDK_INT>=30){
                lm.getCurrentLocation(provider,null,getMainExecutor(),loc->{
                    if(gpsRequestId!=id)return;
                    if(loc!=null){gpsRequestId++;setGps(loc);return;}
                    if(fallback!=null)requestProviderLocation(lm,fallback,null,id,recent);
                    else {gpsRequestId++;finishGpsUnavailable(recent,"Não foi possível obter a posição atual.");}
                });
            }else{
                lm.requestSingleUpdate(provider,new LocationListener(){
                    @Override public void onLocationChanged(Location loc){
                        if(gpsRequestId!=id)return;
                        gpsRequestId++;setGps(loc);
                    }
                    @Override public void onStatusChanged(String p,int status,Bundle extras){}
                    @Override public void onProviderEnabled(String p){}
                    @Override public void onProviderDisabled(String p){}
                },Looper.getMainLooper());
            }
        }catch(Exception e){if(gpsRequestId==id){gpsRequestId++;finishGpsUnavailable(recent,"O GPS não respondeu: "+e.getMessage());}}
    }
    private void finishGpsUnavailable(Location recent,String message) {
        long age=recent==null?Long.MAX_VALUE:System.currentTimeMillis()-recent.getTime();
        if(recent!=null&&age>=0&&age<=15*60*1000){
            toast("Usando uma localização recente do aparelho.");
            setGps(recent);
        }else locationUnavailable(message);
    }
    private void locationUnavailable(String message) {
        gpsRequestId++;
        destinationPending=false;
        if(gpsView!=null)gpsView.setText("GPS indisponível. Informe uma cidade no campo Origem.");
        new AlertDialog.Builder(this).setTitle("Não foi possível usar o GPS")
            .setMessage(message+"\n\nVocê também pode informar a cidade de partida e descobrir as atrações por ela.")
            .setPositiveButton("Buscar pela origem",(d,w)->searchDestinationsFromOrigin())
            .setNegativeButton("Cancelar",null).show();
    }
    private void setGps(Location loc) {
        gps=loc;invalidateRoute();
        if(gpsView!=null)gpsView.setText("Localização pronta: "+String.format(Locale.US,"%.5f, %.5f",loc.getLatitude(),loc.getLongitude()));
        if(destinationPending)searchDestinations(loc);
        else toast("Localização ativada. Agora calcule a rota.");
    }
    private void openMaps() {
        if(!TransportMode.isCar(travelMode())) {openDestinationMap();return;}
        List<String> stops=RouteService.parseStops(val("stops"));
        if(stops.isEmpty()){toast("Preencha os destinos antes de abrir o Maps.");return;}
        if(gps==null && val("origin").trim().isEmpty()){
            toast("Informe origem ou ative o GPS.");return;
        }
        List<String> ordered=new ArrayList<>();
        if(routeResult!=null) {
            for(RouteService.Point point:routeResult.routePoints)ordered.add(point.asMaps());
        }else {
            ordered.add(!val("origin").trim().isEmpty()?val("origin"):
                    gps.getLatitude()+","+gps.getLongitude());
            for(String stop:stops)ordered.add(RouteService.asMapsTarget(stop));
            if(flag("roundTrip",true))ordered.add(ordered.get(0));
        }
        if(ordered.size()<2){toast("Adicione uma parada.");return;}
        StringBuilder url=new StringBuilder("https://www.google.com/maps/dir/?api=1&travelmode=driving&avoid=tolls");
        url.append("&origin=").append(encoded(ordered.get(0)));
        url.append("&destination=").append(encoded(ordered.get(ordered.size()-1)));
        if(ordered.size()>2) {
            url.append("&waypoints=");
            for(int i=1;i<ordered.size()-1;i++) {
                if(i>1)url.append("%7C");url.append(encoded(ordered.get(i)));
            }
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url.toString())));
            if(ordered.size()>5)toast("O Maps pode limitar as paradas. Confira a rota exibida.");
        }catch(Exception e){toast("Não encontramos aplicativo para abrir o mapa.");}
    }
    private static String encoded(String s) {
        try {return URLEncoder.encode(s,"UTF-8");}
        catch(java.io.UnsupportedEncodingException e){return s.replace(" ","%20");}
    }
    /** Exportação e importação manual usando o seletor nativo de arquivos do Android. */
    private void exportBackup() {
        persist();
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,"rotacerta-backup.json");
        try {startActivityForResult(intent,BACKUP_EXPORT);}
        catch(Exception e){toast("Não foi possível abrir o seletor de arquivos.");}
    }
    private void importBackup() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        try {startActivityForResult(intent,BACKUP_IMPORT);}
        catch(Exception e){toast("Não foi possível abrir o seletor de arquivos.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null)return;
        Uri file=data.getData();
        if(request==BACKUP_EXPORT) {
            try(OutputStream stream=getContentResolver().openOutputStream(file)) {
                if(stream==null)throw new Exception("Arquivo não pode ser gravado.");
                JSONObject backup=new JSONObject();
                backup.put("schemaVersion",1);
                backup.put("draft",new JSONObject(draft.toString()));
                backup.put("expenses",new JSONArray(expenses.toString()));
                backup.put("snapshots",new JSONArray(snapshots.toString()));
                stream.write(backup.toString(2).getBytes(StandardCharsets.UTF_8));
                toast("Backup exportado com sucesso.");
            }catch(Exception e){toast("Erro ao exportar backup: "+e.getMessage());}
        } else if(request==BACKUP_IMPORT) {
            try(InputStream in=getContentResolver().openInputStream(file)) {
                if(in==null)throw new Exception("Arquivo não pode ser aberto.");
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                byte[] block=new byte[4096];int len,bytes=0;
                while((len=in.read(block))!=-1) {
                    bytes+=len;
                    if(bytes>3*1024*1024)throw new Exception("Arquivo muito grande (limite: 3 MB).");
                    buffer.write(block,0,len);
                }
                JSONObject backup=new JSONObject(buffer.toString("UTF-8"));
                if(backup.optInt("schemaVersion",0)!=1)throw new Exception("Formato de backup incompatível.");
                JSONObject importedDraft=backup.getJSONObject("draft");
                JSONArray importedExpenses=backup.getJSONArray("expenses");
                JSONArray importedSnapshots=backup.getJSONArray("snapshots");
                new AlertDialog.Builder(this).setTitle("Restaurar backup?")
                    .setMessage("Isso substituirá o roteiro, os gastos e as viagens salvas neste aparelho. A configuração Google Places não será alterada.")
                    .setNegativeButton("Cancelar",null)
                    .setPositiveButton("Restaurar",(d,w)->{
                        draft=importedDraft;expenses=importedExpenses;snapshots=importedSnapshots;
                        gps=null;routeResult=null;routeRevision++;migrateOldBudget();
                        activeTab=0;persist();render();toast("Backup restaurado.");
                    }).show();
            }catch(Exception e){toast("Backup inválido: "+e.getMessage());}
        }
    }

    private void addExpenseDialog() {
        LinearLayout form=col();form.setPadding(dp(20),dp(6),dp(20),dp(6));
        EditText description=new EditText(this);description.setHint("Descrição: combustível, almoço...");
        description.setTextSize(15);description.setSingleLine(true);add(form,description,4);
        EditText amount=new EditText(this);amount.setHint("Valor em reais");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setTextSize(15);add(form,amount,6);
        String[] cats={"Alimentação","Combustível","Passagens","Bagagens","Traslados",
            "Transporte local","Taxas de viagem","Hospedagem","Passeio","Pedágio","Estacionamento","Outros"};
        Spinner spinner=new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,cats));
        add(form,spinner,7);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Nova despesa")
            .setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Registrar",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            double value=parsed(amount.getText().toString());
            if(value<=0){amount.setError("Informe um valor maior que zero.");return;}
            JSONObject expense=new JSONObject();
            try {
                String name=description.getText().toString().trim();
                expense.put("title",name.isEmpty()?"Despesa":name);
                expense.put("category",cats[spinner.getSelectedItemPosition()]);
                expense.put("amount",value);
                expense.put("date",new SimpleDateFormat("dd/MM/yyyy",BR).format(new Date()));
                expenses.put(expense);persist();dialog.dismiss();render();
            }catch(Exception e){toast("Não foi possível registrar a despesa.");}
        }));
        dialog.show();
    }
    private void saveSnapshot() {
        if(val("title").trim().isEmpty() && val("stops").trim().isEmpty()){
            toast("Defina um nome ou destino antes de salvar a viagem.");return;
        }
        JSONObject copy=new JSONObject();
        try {
            String title=val("title").trim().isEmpty()?"Minha viagem":val("title").trim();
            copy.put("title",title);copy.put("created",System.currentTimeMillis());
            copy.put("draft",new JSONObject(draft.toString()));
            copy.put("expenses",new JSONArray(expenses.toString()));
            snapshots.put(copy);persist();render();toast("Viagem salva!");
        }catch(Exception e){toast("Não foi possível salvar viagem.");}
    }
    private String summary() {
        TripMath.Input a=inputData();TripMath.Result r=TripMath.calculate(a);
        StringBuilder b=new StringBuilder();
        b.append("ROTA CERTA | ").append(val("title").isEmpty()?"Minha viagem":val("title")).append("\n\n");
        b.append("Ida: ").append(val("departureDate").isEmpty()?"a definir":val("departureDate"));
        b.append(" | Volta: ").append(val("returnDate").isEmpty()?"a definir":val("returnDate")).append("\n");
        b.append("Origem: ").append(!val("origin").trim().isEmpty()?val("origin"):
                gps==null?"não informada":"Localização GPS").append("\n");
        b.append("Paradas:\n").append(val("stops")).append("\n");
        b.append("Transporte: ").append(TransportMode.label(a.transportMode)).append("\n");
        if(TransportMode.isCar(travelMode())) {
            b.append("Veículo: ").append(val("car")).append("\n");
            b.append("Consumo: ").append(val("kmPerLiter")).append(" km/l | Litro: R$ ").append(val("literPrice")).append("\n");
            b.append("Distância rodoviária: ").append(a.km>0?km(a.km)+" km":"a calcular").append("\n");
        } else {
            b.append("Passagem ida/pessoa: ").append(money(a.outboundFarePerPerson)).append("\n");
            if(a.roundTrip)b.append("Passagem volta/pessoa: ").append(money(a.returnFarePerPerson)).append("\n");
            b.append("Bagagens/pessoa: ").append(money(a.baggagePerPerson)).append("\n");
        }
        b.append("Pessoas: ").append(a.persons).append(" • Noites: ").append(tripNights()<0?"a definir":tripNights()).append("\n\n");
        b.append("PREVISÃO DE GASTOS\n");
        if(TransportMode.isCar(travelMode()))b.append("Combustível: ").append(money(r.fuel)).append("\n");
        else {
            b.append("Passagens: ").append(money(r.tickets)).append("\n");
            b.append("Bagagens: ").append(money(r.baggage)).append("\n");
            b.append("Traslados: ").append(money(r.transfers)).append("\n");
            b.append("Transporte local: ").append(money(r.localTransport)).append("\n");
            b.append("Taxas de viagem: ").append(money(r.travelFees)).append("\n");
        }
        b.append("Hospedagem: ").append(money(r.lodging)).append("\n");
        b.append("Alimentação: ").append(money(r.food)).append("\n");
        b.append("Passeios: ").append(money(r.activities)).append("\n");
        b.append("Estacionamento: ").append(money(r.parking)).append("\n");
        b.append("Pedágios: ").append(money(r.tolls)).append("\n");
        b.append("Outros: ").append(money(r.other)).append("\n");
        b.append("Reserva: ").append(money(r.contingency)).append("\n");
        b.append("TOTAL: ").append(money(r.total)).append("\n");
        b.append("POR PESSOA: ").append(money(r.perPerson)).append("\n");
        b.append("GASTOS REALIZADOS: ").append(money(actualTotal())).append("\n\n");
        b.append("Valores estimativos. Confira rota, abastecimento e preços antes da viagem.");
        return b.toString();
    }
    private void sharePlan() {
        Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT,summary());
        startActivity(Intent.createChooser(intent,"Compartilhar planejamento"));
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}

    private final class BudgetBar extends View {
        private final int activeColor;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float ratio=0;
        BudgetBar(int color){super(MainActivity.this);activeColor=color;
            setLayoutParams(new LinearLayout.LayoutParams(-1,dp(7)));}
        void setRatio(float value){ratio=Math.min(1,Math.max(0,value));invalidate();}
        @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec),dp(7));
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float w=getWidth(),h=getHeight();
            paint.setColor(LINE);canvas.drawRoundRect(0,0,w,h,h/2,h/2,paint);
            paint.setColor(activeColor);
            if(ratio>0)canvas.drawRoundRect(0,0,Math.min(w,Math.max(h,w*ratio)),h,h/2,h/2,paint);
        }
    }
    /** Ícones vetoriais desenhados no Android, sem dependências externas. */
    private final class Symbol extends View {
        private final String name;
        private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int tint;
        Symbol(String icon,int color){super(MainActivity.this);name=icon;tint=color;}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);
            pen.setColor(tint);pen.setStrokeWidth(1.9f);pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setStrokeJoin(Paint.Join.ROUND);pen.setStyle(Paint.Style.STROKE);
            switch(name) {
                case "map":
                    canvas.drawLine(3,6,9,3,pen);canvas.drawLine(9,3,15,6,pen);
                    canvas.drawLine(15,6,21,3,pen);canvas.drawLine(3,6,3,20,pen);
                    canvas.drawLine(3,20,9,17,pen);canvas.drawLine(9,3,9,17,pen);
                    canvas.drawLine(9,17,15,20,pen);canvas.drawLine(15,6,15,20,pen);
                    canvas.drawLine(15,20,21,17,pen);canvas.drawLine(21,3,21,17,pen);break;
                case "calendar":
                    canvas.drawRoundRect(3,5,21,21,2,2,pen);canvas.drawLine(3,10,21,10,pen);
                    canvas.drawLine(8,3,8,7,pen);canvas.drawLine(16,3,16,7,pen);
                    canvas.drawLine(8,14,10,14,pen);canvas.drawLine(14,14,16,14,pen);break;
                case "pin":
                    Path pin=new Path();pin.moveTo(12,22);pin.cubicTo(10,18,5,14,5,10);
                    pin.cubicTo(5,0,19,0,19,10);pin.cubicTo(19,14,14,20,12,22);
                    canvas.drawPath(pin,pen);canvas.drawCircle(12,10,2.6f,pen);break;
                case "route":
                    canvas.drawCircle(5,19,2,pen);canvas.drawCircle(19,5,2,pen);
                    Path road=new Path();road.moveTo(7,19);road.cubicTo(24,19,1,5,17,5);canvas.drawPath(road,pen);break;
                case "bus":
                    canvas.drawRoundRect(4,3,20,19,3,3,pen);
                    canvas.drawLine(4,11,20,11,pen);canvas.drawLine(9,3,9,11,pen);
                    canvas.drawLine(15,3,15,11,pen);
                    canvas.drawCircle(8,20,1.5f,pen);canvas.drawCircle(16,20,1.5f,pen);break;
                case "plane":
                    Path plane=new Path();plane.moveTo(3,13);plane.lineTo(10,11);
                    plane.lineTo(19,2);plane.lineTo(22,3);plane.lineTo(15,13);
                    plane.lineTo(19,19);plane.lineTo(17,21);plane.lineTo(12,17);
                    plane.lineTo(8,21);plane.lineTo(6,20);plane.lineTo(9,15);
                    plane.close();canvas.drawPath(plane,pen);break;
                case "car":
                    canvas.drawRoundRect(3,9,21,18,2,2,pen);canvas.drawLine(6,9,8,5,pen);
                    canvas.drawLine(8,5,16,5,pen);canvas.drawLine(16,5,18,9,pen);
                    canvas.drawLine(6,18,6,21,pen);canvas.drawLine(18,18,18,21,pen);
                    canvas.drawLine(3,13,6,13,pen);canvas.drawLine(18,13,21,13,pen);break;
                case "wallet":
                    canvas.drawRoundRect(3,6,21,19,2,2,pen);
                    canvas.drawLine(3,9,21,9,pen);canvas.drawRoundRect(14,11,22,16,1.5f,1.5f,pen);
                    canvas.drawCircle(17,13.5f,.6f,pen);break;
                case "receipt":
                    Path receipt=new Path();receipt.moveTo(5,3);receipt.lineTo(19,3);receipt.lineTo(19,21);
                    receipt.lineTo(16,19);receipt.lineTo(13,21);receipt.lineTo(10,19);receipt.lineTo(7,21);
                    receipt.lineTo(5,19);receipt.close();canvas.drawPath(receipt,pen);
                    canvas.drawLine(8,8,16,8,pen);canvas.drawLine(8,12,16,12,pen);canvas.drawLine(8,16,13,16,pen);break;
                case "suitcase":
                    canvas.drawRoundRect(3,8,21,21,2.5f,2.5f,pen);
                    canvas.drawRoundRect(8,3,16,8,1.5f,1.5f,pen);
                    canvas.drawLine(3,14,21,14,pen);canvas.drawLine(10,12,10,16,pen);
                    canvas.drawLine(14,12,14,16,pen);break;
                case "share":
                    canvas.drawCircle(5,12,2,pen);canvas.drawCircle(19,5,2,pen);
                    canvas.drawCircle(19,19,2,pen);canvas.drawLine(7,11,17,6,pen);canvas.drawLine(7,13,17,18,pen);break;
                case "plus":
                    canvas.drawLine(12,4,12,20,pen);canvas.drawLine(4,12,20,12,pen);break;
                case "arrow":
                    canvas.drawLine(4,12,20,12,pen);canvas.drawLine(14,6,20,12,pen);
                    canvas.drawLine(20,12,14,18,pen);break;
                case "check":
                    canvas.drawCircle(12,12,9,pen);canvas.drawLine(7,12,11,16,pen);
                    canvas.drawLine(11,16,17,9,pen);break;
                case "trash":
                    canvas.drawRoundRect(6,7,18,21,1,1,pen);canvas.drawLine(4,6,20,6,pen);
                    canvas.drawLine(9,3,15,3,pen);canvas.drawLine(10,10,10,18,pen);
                    canvas.drawLine(14,10,14,18,pen);break;
                default: canvas.drawCircle(12,12,9,pen);
            }
            canvas.restore();
        }
    }
}
