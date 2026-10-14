package br.com.rotacerta.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class DestinationServiceTest {
    @Test public void consultaOsmUsaCoordenadasERaio() {
        String q=DestinationService.buildOsmQuery(-12.69750,-38.32417,18000);
        assertTrue(q.contains("(around:18000,-12.697500,-38.324170)"));
        assertTrue(q.contains("tourism"));
        assertTrue(q.contains("natural"));
        assertTrue(q.contains("out center 250"));
    }
    @Test public void destinoSalvaCoordenadasParaRotas() {
        DestinationService.Place p=new DestinationService.Place("Praia teste","BA","OpenStreetMap",-12.10001,-38.20001,-12.69750,-38.32417);
        assertEquals("Praia teste [-12.100010,-38.200010]", p.routeLabel());
        assertEquals("-12.100010,-38.200010", RouteService.asMapsTarget(p.routeLabel()));
    }
}
