package br.com.rotacerta.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class TransportMathTest {
    @Test public void aviaoCalculaIdaEVoltaMaisTaxasSemCombustivelRodoviario() {
        TripMath.Input i=new TripMath.Input();
        i.transportMode=TransportMode.PLANE;i.roundTrip=true;i.persons=2;
        i.outboundFarePerPerson=400;i.returnFarePerPerson=550;
        i.baggagePerPerson=90;i.transfersTotal=100;i.localTransportTotal=140;
        i.transportFeesTotal=30;i.km=1000;i.kmPerLiter=10;i.literPrice=6.5;
        TripMath.Result r=TripMath.calculate(i);
        assertEquals(1900,r.tickets,.001);assertEquals(180,r.baggage,.001);
        assertEquals(2350,r.transport,.001);assertEquals(0,r.fuel,.001);
    }
    @Test public void onibusSomenteIdaNaoCobraVolta() {
        TripMath.Input i=new TripMath.Input();i.transportMode=TransportMode.BUS;
        i.persons=4;i.roundTrip=false;i.outboundFarePerPerson=110;
        i.returnFarePerPerson=200;
        assertEquals(440,TripMath.calculate(i).tickets,.001);
        i.roundTrip=true;
        assertEquals(1240,TripMath.calculate(i).tickets,.001);
    }
    @Test public void carroIgnoraPassagensAntigas() {
        TripMath.Input i=new TripMath.Input();i.transportMode=TransportMode.CAR;
        i.outboundFarePerPerson=800;i.km=120;i.kmPerLiter=10;i.literPrice=5;
        assertEquals(60,TripMath.calculate(i).total,.001);
    }
    @Test public void leituraDeNumerosDoBrasil() {
        assertEquals(1200,NumericInput.nonNegative("1.200"),.001);
        assertEquals(1234.56,NumericInput.nonNegative("1.234,56"),.001);
        assertEquals(12.5,NumericInput.nonNegative("12,5"),.001);
        assertEquals(0,NumericInput.nonNegative("valor inválido"),.001);
    }
}
