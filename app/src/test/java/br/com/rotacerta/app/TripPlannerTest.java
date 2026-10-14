package br.com.rotacerta.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class TripPlannerTest {
    @Test public void periodTwoDaysOneNight() {
        assertEquals(1,TripPlanner.nights("14/10/2026","15/10/2026"));
        assertEquals(2,TripPlanner.days("14/10/2026","15/10/2026"));
    }
    @Test public void sameDayHasNoNight() {
        assertEquals(0,TripPlanner.nights("14/10/2026","14/10/2026"));
        TripPlanner.Meals m=TripPlanner.suggestMeals(0,false);
        assertEquals(0,m.breakfast);assertEquals(1,m.lunch);assertEquals(0,m.dinner);assertEquals(1,m.snacks);
    }
    @Test public void breakfastIncludedIsZero() {
        TripPlanner.Meals m=TripPlanner.suggestMeals(3,true);
        assertEquals(0,m.breakfast);assertEquals(4,m.lunch);
        assertEquals(3,m.dinner);assertEquals(4,m.snacks);
    }
    @Test public void invalidDateNeverCreatesNight() {
        assertEquals(-1,TripPlanner.nights("31/02/2026","01/03/2026"));
        assertEquals(-1,TripPlanner.nights("15/10/2026","14/10/2026"));
        assertEquals(-1,TripPlanner.nights("",""));
    }
    @Test public void lodgingPerNightAndRoom() {
        assertEquals(300,TripPlanner.lodgingPerNight(900,3),.001);
        assertEquals(150,TripPlanner.lodgingPerRoomNight(900,3,2),.001);
    }
}
