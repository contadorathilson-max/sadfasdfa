package br.com.rotacerta.app;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Regras testaveis de noites, dias e refeicoes. Sem valores iniciais. */
public final class TripPlanner {
    private TripPlanner() {}
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu", new Locale("pt", "BR"))
            .withResolverStyle(ResolverStyle.STRICT);

    /** Retorna -1 quando as datas nao existem ou sao invalidas. */
    public static int nights(String departure, String arrival) {
        try {
            LocalDate start = LocalDate.parse(departure, FORMAT);
            LocalDate end = LocalDate.parse(arrival, FORMAT);
            long days = ChronoUnit.DAYS.between(start, end);
            return days < 0 || days > 3650 ? -1 : (int) days;
        } catch (Exception e) { return -1; }
    }
    public static int days(String departure, String arrival) {
        int nights=nights(departure,arrival);
        return nights<0?-1:nights+1;
    }
    public static final class Meals {
        public final int breakfast, lunch, dinner, snacks;
        private Meals(int breakfast,int lunch,int dinner,int snacks) {
            this.breakfast=breakfast;this.lunch=lunch;this.dinner=dinner;this.snacks=snacks;
        }
    }
    /** Cafe da manha por noite (exceto se incluido), almoco por dia e jantar por noite. */
    public static Meals suggestMeals(int nights, boolean breakfastIncluded) {
        if(nights < 0) throw new IllegalArgumentException("Informe datas validas antes de preencher refeicoes.");
        int days=nights+1;
        return new Meals(breakfastIncluded?0:nights, days, nights, days);
    }
    public static double lodgingPerNight(double total,int nights) {
        return nights>0?Math.max(0,total)/nights:0;
    }
    public static double lodgingPerRoomNight(double total,int nights,int rooms) {
        return nights>0 && rooms>0?Math.max(0,total)/(nights*rooms):0;
    }
}
