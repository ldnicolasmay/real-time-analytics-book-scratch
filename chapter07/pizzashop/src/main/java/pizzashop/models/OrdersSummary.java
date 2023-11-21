package pizzashop.models;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class OrdersSummary {
    private TimePeriod currentTimePeriod;
    private TimePeriod previousTimePeriod;
    private long totalOrders; // new

    public OrdersSummary(TimePeriod currentTimePeriod, TimePeriod previousTimePeriod, long totalOrders) {
        this.currentTimePeriod = currentTimePeriod;
        this.previousTimePeriod = previousTimePeriod;
        this.totalOrders = totalOrders;
    }

    public TimePeriod getCurrentTimePeriod() {
        return currentTimePeriod;
    }

    public TimePeriod getPreviousTimePeriod() {
        return previousTimePeriod;
    }

    public long getTotalOrders() {
        return totalOrders;
    }
}
