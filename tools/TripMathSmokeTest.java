import br.com.rotacerta.app.TripMath;
import br.com.rotacerta.app.TripPlanner;
import br.com.rotacerta.app.TransportMode;
import br.com.rotacerta.app.NumericInput;

public class TripMathSmokeTest {
    private static void eq(double actual,double expected,String what) {
        if(Math.abs(actual-expected)>0.0001)throw new AssertionError(what+": "+actual+" != "+expected);
    }
    public static void main(String[] args) {
        TripMath.Input empty=new TripMath.Input();
        eq(TripMath.calculate(empty).total,0,"Orçamento limpo");
        TripMath.Input car=new TripMath.Input();
        car.transportMode=TransportMode.CAR; car.km=680;car.kmPerLiter=12;
        car.literPrice=6.2;car.persons=2;car.lodgingTotal=300;
        car.lunches=2;car.lunchPerPerson=60;car.outboundFarePerPerson=999;
        TripMath.Result a=TripMath.calculate(car);
        eq(a.fuel,351.3333333333333,"Combustível");
        eq(a.tickets,0,"Carro não cobra passagem antiga");
        eq(a.total,351.3333333333333+300+240,"Total carro");
        TripMath.Input plane=new TripMath.Input();
        plane.transportMode=TransportMode.PLANE;plane.persons=3;plane.roundTrip=true;
        plane.outboundFarePerPerson=450;plane.returnFarePerPerson=600;
        plane.baggagePerPerson=75;plane.transfersTotal=160;
        plane.localTransportTotal=280;plane.transportFeesTotal=35;
        plane.km=1000;plane.literPrice=7;plane.kmPerLiter=8;
        plane.lodgingTotal=900;plane.contingencyPercent=10;
        TripMath.Result b=TripMath.calculate(plane);
        eq(b.fuel,0,"Avião sem combustível de carro");
        eq(b.tickets,3150,"Passagens ida e volta para 3");
        eq(b.baggage,225,"Bagagem 3 pessoas");
        eq(b.transport,3150+225+160+280+35,"Custo de deslocamento");
        eq(b.total,(b.transport+900)*1.10,"Reserva sobre orçamento");
        eq(b.perPerson,b.total/3,"Orçamento por pessoa");
        plane.roundTrip=false;
        eq(TripMath.calculate(plane).tickets,1350,"Só passagem de ida");
        TripMath.Input bus=new TripMath.Input();bus.transportMode=TransportMode.BUS;
        bus.persons=2;bus.roundTrip=true;bus.outboundFarePerPerson=110;
        bus.returnFarePerPerson=130;bus.localTransportTotal=80;
        eq(TripMath.calculate(bus).transport,560,"Ônibus com custos locais");
        eq(NumericInput.nonNegative("1.200"),1200,"Milhar pt-BR");
        eq(NumericInput.nonNegative("1.234,56"),1234.56,"Decimais pt-BR");
        eq(NumericInput.nonNegative("1,234.56"),1234.56,"Decimais en-US");
        eq(NumericInput.nonNegative("12,5"),12.5,"Consumo do carro");
        eq(NumericInput.nonNegative("-50"),0,"Valor negativo");
        eq(TripPlanner.nights("14/10/2026","17/10/2026"),3,"Noites");
        TripPlanner.Meals m=TripPlanner.suggestMeals(3,false);
        if(m.breakfast!=3||m.lunch!=4||m.dinner!=3||m.snacks!=4)
            throw new AssertionError("Refeições erradas");
        eq(TripPlanner.lodgingPerNight(600,3),200,"Média por noite");
        System.out.println("OK: carro, onibus, aviao, bilhetes por pessoa, ida/volta, bagagem, traslados, alimentacao, hospedagem, numero brasileiro");
    }
}
