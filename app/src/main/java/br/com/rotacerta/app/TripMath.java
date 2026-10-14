package br.com.rotacerta.app;

/** Orçamento puro, sem chamadas de rede ou custos de passagens inventados. */
public final class TripMath {
    private TripMath() {}

    public static final class Input {
        public String transportMode = TransportMode.CAR;
        public boolean roundTrip;
        public double km, kmPerLiter, literPrice;
        /** Preços por viajante, por trecho; retorno só é cobrado se roundTrip=true. */
        public double outboundFarePerPerson, returnFarePerPerson, baggagePerPerson;
        /** Totais para todo o grupo, sem multiplicação pela quantidade de pessoas. */
        public double transfersTotal, localTransportTotal, transportFeesTotal;
        public int persons, nights, rooms, breakfasts, lunches, dinners, snackDays;
        public double lodgingTotal, roomPerNight, breakfastPerPerson, lunchPerPerson, dinnerPerPerson, snacksPerPerson;
        public double attractions, parking, tolls, other, contingencyPercent;
    }
    public static final class Result {
        public double fuel, tickets, baggage, transfers, localTransport, travelFees, transport;
        public double lodging, food, activities, parking, tolls, other, contingency, base, total, perPerson;
    }

    public static Result calculate(Input a) {
        Result r = new Result();
        int people = Math.max(1, a.persons);
        if (TransportMode.isCar(a.transportMode)) {
            r.fuel = a.kmPerLiter > 0 ? Math.max(0, a.km) * Math.max(0, a.literPrice) / a.kmPerLiter : 0;
        } else {
            double singleFare=Math.max(0,a.outboundFarePerPerson);
            double backFare=a.roundTrip?Math.max(0,a.returnFarePerPerson):0;
            r.tickets=people*(singleFare+backFare);
            r.baggage=people*Math.max(0,a.baggagePerPerson);
            r.transfers=Math.max(0,a.transfersTotal);
            r.localTransport=Math.max(0,a.localTransportTotal);
            r.travelFees=Math.max(0,a.transportFeesTotal);
        }
        r.transport=r.fuel+r.tickets+r.baggage+r.transfers+r.localTransport+r.travelFees;
        r.lodging=Math.max(0,a.lodgingTotal);
        r.food=people*(Math.max(0,a.breakfasts)*Math.max(0,a.breakfastPerPerson)
            +Math.max(0,a.lunches)*Math.max(0,a.lunchPerPerson)
            +Math.max(0,a.dinners)*Math.max(0,a.dinnerPerPerson)
            +Math.max(0,a.snackDays)*Math.max(0,a.snacksPerPerson));
        r.activities=Math.max(0,a.attractions);
        r.parking=Math.max(0,a.parking);
        r.tolls=Math.max(0,a.tolls);
        r.other=Math.max(0,a.other);
        r.base=r.transport+r.lodging+r.food+r.activities+r.parking+r.tolls+r.other;
        r.contingency=r.base*Math.max(0,Math.min(a.contingencyPercent,100))/100;
        r.total=r.base+r.contingency;
        r.perPerson=r.total/people;
        return r;
    }
}
