package pizzashop.rest;

import pizzashop.models.OrdersSummary;
import pizzashop.streams.OrdersQueries;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

// This doesn't work, and I'm not going to learn a framework
// to figure out how to get it to work. Moving on.
@ApplicationScoped
@Path("/orders")
public class OrdersResource {
    @Inject
    OrdersQueries ordersQueries;

    @Produces(MediaType.APPLICATION_JSON)
    @GET
    @Path("/overview")
    public Response overview() {
        OrdersSummary ordersSummary = ordersQueries.orderSummary();
        return Response.ok(ordersSummary).build();
    }
}

// Below works fine
//import jakarta.ws.rs.GET;
//import jakarta.ws.rs.Path;
//import jakarta.ws.rs.Produces;
//import jakarta.ws.rs.core.MediaType;
//
//@Path("/hello")
//public class OrdersResource {
//
//    @GET
//    @Produces(MediaType.TEXT_PLAIN)
//    public String hello() {
//        return "Hello from RESTEasy Reactive";
//    }
//}
