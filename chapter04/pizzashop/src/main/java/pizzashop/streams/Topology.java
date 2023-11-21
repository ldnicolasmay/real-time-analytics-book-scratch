package pizzashop.streams;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.WindowStore;
import pizzashop.serdes.JsonDeserializer;
import pizzashop.serdes.JsonSerializer;
import pizzashop.models.Order;

//import javax.enterprise.context.ApplicationScoped;
//import javax.enterprise.inject.Produces;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Duration;

@ApplicationScoped
public class Topology {
    @Produces
    public org.apache.kafka.streams.Topology buildTopology() {
        final Serde<Order> orderSerde = Serdes.serdeFrom(
            new JsonSerializer<>(), new JsonDeserializer<>(Order.class));

        StreamsBuilder builder = new StreamsBuilder();

        // Create a stream over the `orders` topic
        KStream<String, Order> orders = builder.stream(
            "orders", Consumed.with(Serdes.String(), orderSerde));

        // Define the window size of our state store
        Duration windowSize = Duration.ofSeconds(60);
        Duration advanceSize = Duration.ofSeconds(1);
        Duration gracePeriod = Duration.ofSeconds(60);
        TimeWindows timeWindow = TimeWindows.ofSizeAndGrace(windowSize, gracePeriod)
            .advanceBy(advanceSize);

        // Create an OrdersCountStore that keeps track of the
        // number of orders over the last two minutes
        orders.groupBy(
            (key, value) -> "count",
            Grouped.with(Serdes.String(), orderSerde))
            .windowedBy(timeWindow)
            .count(Materialized.as("OrdersCountStore")
        );

        // Create a RevenueStore that keeps track of the amount
        // of revenue generated over the last two minutes
        orders.groupBy(
            (key, value) -> "count",
            Grouped.with(Serdes.String(), orderSerde))
            .windowedBy(timeWindow)
            .aggregate(
                () -> 0.0,
                (key, value, aggregate) -> aggregate + value.price,
                Materialized.<String, Double, WindowStore<Bytes, byte[]>>as("RevenueStore")
                    .withValueSerde(Serdes.Double())
            );

        return builder.build();
    }
}
