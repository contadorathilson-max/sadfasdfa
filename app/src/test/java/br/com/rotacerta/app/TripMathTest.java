package br.com.rotacerta.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class TripMathTest {
    @Test public void novoAppComecaComOrcamentoZerado() {
        TripMath.Input in = new TripMath.Input();
        TripMath.Result r=TripMath.calculate(in);
        assertEquals(0, r.fuel, 0.001);
        assertEquals(0, r.total, 0.001);
        assertEquals(0, r.perPerson, 0.001);
    }
    @Test public void gasolinaSomentePorConsumoEPrecoDoLitro() {
        TripMath.Input in=new TripMath.Input();
        in.km=680; in.kmPerLiter=12; in.literPrice=6.20; in.persons=2;
        TripMath.Result r=TripMath.calculate(in);
        assertEquals(351.3333333, r.fuel, 0.001);
        assertEquals(r.total/2, r.perPerson, 0.001);
    }
    @Test public void hospedagemRefeicoesEReserva() {
        TripMath.Input in=new TripMath.Input();
        in.persons=3; in.lodgingTotal=800;
        in.lunches=2; in.lunchPerPerson=50; in.contingencyPercent=10;
        TripMath.Result r=TripMath.calculate(in);
        assertEquals(800, r.lodging, 0.0001);
        assertEquals(300, r.food, 0.0001);
        assertEquals(110, r.contingency, 0.0001);
        assertEquals(1210, r.total, 0.0001);
    }
    @Test public void consumoZeradoNaoFazDivisao() {
        TripMath.Input in=new TripMath.Input();
        in.km=800; in.kmPerLiter=0; in.literPrice=8; in.persons=1;
        assertEquals(0, TripMath.calculate(in).fuel, 0.0001);
    }
}
