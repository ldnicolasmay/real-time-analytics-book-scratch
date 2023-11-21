package pizzashop.rest;

import pizzashop.models.OrdersSummary;
import pizzashop.models.TimePeriod;
import pizzashop.streams.OrdersQueries;

import org.apache.pinot.client.Connection;
import org.apache.pinot.client.ConnectionFactory;
import org.apache.pinot.client.ResultSet;
import org.apache.pinot.client.ResultSetGroup;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import static org.jooq.impl.DSL.*;

@ApplicationScoped
@Path("/orders")
public class OrdersResource {
    @Inject
    OrdersQueries ordersQueries;

    private Connection connection = ConnectionFactory.fromHostList(
        System.getenv().getOrDefault("PINOT_BROKER",  "localhost:8099")
    );

    // This doesn't work, and I'm not going to learn a framework
    // to figure out how to get it to work. Moving on.
    @GET
    @Path("/overview")
    public Response overview() {
        OrdersSummary ordersSummary = ordersQueries.orderSummary();
        return Response.ok(ordersSummary).build();
    }

    @GET
    @Path("/overview2")
    public Response overview2() {
        ResultSet resultSet = runQuery(connection, "select count(*) from orders limit 10");
        long totalOrders = resultSet.getLong(0);

        String query = DSL.using(SQLDialect.POSTGRES).select(
            count()
                .filterWhere("ts > ago('PT1M')")
                .as("events1Min"),
            count()
                .filterWhere("ts <= ago('PT1M') AND ts > ago('PT2M')")
                .as("events1Min2Min"),
            sum(field("price").coerce(Long.class))
                .filterWhere("ts > ago('PT1M')")
                .as("total1Min"),
            sum(field("price").coerce(Long.class))
                .filterWhere("ts <= ago('PT1M') AND ts > ago('PT2M')")
                .as("total1Min2Min")
        ).from("orders").getSQL();

        ResultSet summaryResults = runQuery(connection, query);

        TimePeriod currentTimePeriod = new TimePeriod(summaryResults.getLong(0, 0), summaryResults.getDouble(0, 2));
        TimePeriod previousTimePeriod = new TimePeriod(summaryResults.getLong(0, 1), summaryResults.getDouble(0, 3));
        //OrdersSummary ordersSummary = new OrdersSummary(currentTimePeriod, previousTimePeriod);
        OrdersSummary ordersSummary = new OrdersSummary(currentTimePeriod, previousTimePeriod, totalOrders);

        return Response.ok(ordersSummary).build();
    }

    private static ResultSet runQuery(Connection connection, String query) {
        ResultSetGroup resultSetGroup = connection.execute(query);
        return resultSetGroup.getResultSet(0);
    }
}
