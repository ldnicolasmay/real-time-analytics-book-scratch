package pizzashop.streams;

import io.debezium.serde.DebeziumSerdes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.WindowStore;

import pizzashop.models.*;
import pizzashop.serdes.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class Topology {
    @Produces
    public org.apache.kafka.streams.Topology buildTopology() {
        String ordersTopic = System.getenv().getOrDefault("ORDERS_TOPIC", "orders");
        String productsTopic = System.getenv().getOrDefault(
            "PRODUCTS_TOPIC", "mysql.pizzashop.products"
        );
        String enrichedOrderItemsTopic = System.getenv().getOrDefault(
            "ENRICHED_ORDER_ITEMS_TOPIC", "enriched-order-items"
        );
        String ordersStatusesTopic = System.getenv().getOrDefault(
            "ORDERS_STATUSES_TOPIC", "ordersStatuses"
        );
        String enrichedOrdersTopic = System.getenv().getOrDefault(
            "ENRICHED_ORDERS_TOPIC", "enriched-orders"
        );

        // Order ser-des
        final Serde<Order> orderSerde = Serdes.serdeFrom(
            new JsonSerializer<>(), new JsonDeserializer<>(Order.class)
        );

        // Product ser-des
        Serde<String> productKeySerde = DebeziumSerdes.payloadJson(String.class);
        productKeySerde.configure(Collections.emptyMap(), true);
        Serde<Product> productSerde = DebeziumSerdes.payloadJson(Product.class);
        productSerde.configure(Collections.singletonMap("from.field", "after"), false);

        // HydratedOrderItem ser-des
        final Serde<HydratedOrderItem> hydratedOrderItemsSerde = Serdes.serdeFrom(
            new JsonSerializer<>(), new JsonDeserializer<>(HydratedOrderItem.class)
        );

        // OrderItemWithContext ser-des
        OrderItemWithContextSerde orderItemWithContextSerde = new OrderItemWithContextSerde();

        // OrderStatus ser-des
        final Serde<OrderStatus> orderStatusSerde = Serdes.serdeFrom(
            new JsonSerializer<>(), new JsonDeserializer<>(OrderStatus.class)
        );

        // EnrichedOrder ser-des
        final Serde<EnrichedOrder> enrichedOrderSerde = Serdes.serdeFrom(
            new JsonSerializer<>(), new JsonDeserializer<>(EnrichedOrder.class)
        );

        // Start building streams
        StreamsBuilder builder = new StreamsBuilder();

        // Create a stream over the `orders` topic
        KStream<String, Order> orders = builder.stream(
            ordersTopic, Consumed.with(Serdes.String(), orderSerde)
        );

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

        orders.groupBy(
                (key, value) -> "count",
                Grouped.with(Serdes.String(), orderSerde))
            //.windowedBy(timeWindow)
            .count(Materialized.as("TotalOrdersStore")
            );

        // Create a table over `products` topic
        KTable<String, Product> products = builder.table(
            productsTopic, Consumed.with(productKeySerde, productSerde)
        );

        // Flatten items in order in stream
        KStream<String, OrderItemWithContext> orderItems = orders.flatMap(
            (key, value) -> {
                List<KeyValue<String, OrderItemWithContext>> result = new ArrayList<>();
                for (OrderItem item : value.items) {
                    OrderItemWithContext orderItemWithContext = new OrderItemWithContext();
                    orderItemWithContext.orderId = value.id;
                    orderItemWithContext.createdAt = value.createdAt;
                    orderItemWithContext.orderItem = item;
                    result.add(new KeyValue<>(String.valueOf(item.productId), orderItemWithContext));
                }
                return result;
            }
        );

        // Join orderItems and products
        KStream<String, HydratedOrderItem> hydratedOrderItems = orderItems.join(
            products,
            (orderItem, product) -> {
                HydratedOrderItem hydratedOrderItem = new HydratedOrderItem();
                hydratedOrderItem.orderId = orderItem.orderId;
                hydratedOrderItem.orderItem = orderItem.orderItem;
                hydratedOrderItem.product = product;
                hydratedOrderItem.createdAt = orderItem.createdAt;
                return hydratedOrderItem;
            },
            Joined.with(Serdes.String(), orderItemWithContextSerde, productSerde)
        );

        // Publish hydratedOrderItems KStream to `enriched-order-items` topic
        hydratedOrderItems.to(
            enrichedOrderItemsTopic, Produced.with(Serdes.String(), hydratedOrderItemsSerde)
        );

        // Create a stream over the `ordersStatuses` topic
        KStream<String, OrderStatus> ordersStatuses = builder.stream(
            ordersStatusesTopic, Consumed.with(Serdes.String(), orderStatusSerde)
        );

        // Join `orders` and `orderStatuses`
        KStream<String, EnrichedOrder> enrichedOrders = orders.join(
            ordersStatuses,
            (order, orderStatus) -> {
                EnrichedOrder enrichedOrder = new EnrichedOrder();
                enrichedOrder.id = order.id;
                enrichedOrder.items = order.items;
                enrichedOrder.userId = order.userId;
                enrichedOrder.status = orderStatus.status;
                enrichedOrder.createdAt = orderStatus.updatedAt;
                enrichedOrder.price = order.price;
                return enrichedOrder;
            },
            JoinWindows.ofTimeDifferenceAndGrace(Duration.ofHours(2), Duration.ofHours(4)),
            StreamJoined.with(Serdes.String(), orderSerde, orderStatusSerde)
        );

        // Publish enrichedOrders KStream to `enriched-orders` topic
        enrichedOrders.to(
            enrichedOrdersTopic, Produced.with(Serdes.String(), enrichedOrderSerde)
        );

        return builder.build();
    }
}
