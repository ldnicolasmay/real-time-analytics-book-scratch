package pizzashop.streams;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.*;

import pizzashop.models.KStreamsWindowStore;
import pizzashop.models.OrdersSummary;
import pizzashop.models.TimePeriod;

import jakarta.inject.Inject;
import java.time.Instant;
//import javax.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrdersQueries {
    @Inject
    KafkaStreams streams;

    public OrdersSummary orderSummary() {
        //streams.start(); // ???
        KStreamsWindowStore<Long> countStore = new KStreamsWindowStore<>(ordersCountStore());
        KStreamsWindowStore<Double> revenueStore = new KStreamsWindowStore<>(revenueStore());

        Instant now = Instant.now();
        Instant oneMinuteAgo = now.minusSeconds(60);
        Instant twoMinutesAgo = now.minusSeconds(120);

        long recentCount = countStore.firstEntry(oneMinuteAgo, now);
        double recentRevenue = revenueStore.firstEntry(oneMinuteAgo, now);
        long previousCount = countStore.firstEntry(twoMinutesAgo, oneMinuteAgo);
        double previousRevenue = revenueStore.firstEntry(twoMinutesAgo, oneMinuteAgo);

        TimePeriod currentTimePeriod = new TimePeriod(recentCount, recentRevenue);
        TimePeriod previousTimePeriod = new TimePeriod(previousCount, previousRevenue);

        return new OrdersSummary(currentTimePeriod, previousTimePeriod);
    }

    private ReadOnlyWindowStore<String, Long> ordersCountStore() {
        while (true) {
            try {
                streams.start();
                return streams.store(StoreQueryParameters.fromNameAndType(
                    "OrdersCountStore", QueryableStoreTypes.windowStore()
                ));
            } catch (InvalidStateStoreException e) {
                System.out.println("e = " + e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private ReadOnlyWindowStore<String, Double> revenueStore() {
        while (true) {
            try {
                streams.start();
                return streams.store(StoreQueryParameters.fromNameAndType(
                    "RevenueStore", QueryableStoreTypes.windowStore()
                ));
            } catch (InvalidStateStoreException e) {
                System.out.println("e = " + e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
