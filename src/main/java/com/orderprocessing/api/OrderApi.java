package com.orderprocessing.api;

import com.orderprocessing.dto.CreateOrderRequest;
import com.orderprocessing.dto.OrderResponse;
import com.orderprocessing.model.Order;
import com.orderprocessing.store.OrderStore;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderApi {
    private static final Logger logger = LoggerFactory.getLogger(OrderApi.class);
    private final OrderStore orderStore;
    private final Vertx vertx;

    public OrderApi(Vertx vertx, OrderStore orderStore) {
        this.vertx = vertx;
        this.orderStore = orderStore;
    }

    public Router createRouter() {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        
        router.post("/orders").handler(this::createOrder);
        router.get("/orders/:orderId").handler(this::getOrder);
        router.get("/health").handler(this::healthCheck);
        
        return router;
    }

    private void createOrder(RoutingContext ctx) {
        try {
            CreateOrderRequest request = ctx.body().asJsonObject().mapTo(CreateOrderRequest.class);

            String validationError = validateRequest(request);
            if (validationError != null) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error", validationError)
                        .encode());
                return;
            }

            orderStore.findByIdempotencyKey(request.getIdempotencyKey())
                .compose(existingOrder -> {
                    if (existingOrder != null) {
                        logger.info("Idempotent request detected for key: {}", request.getIdempotencyKey());
                        return Future.succeededFuture(existingOrder);
                    } else {
                        String orderId = UUID.randomUUID().toString();
                        Order order = new Order(
                            orderId,
                            request.getIdempotencyKey(),
                            request.getCustomerId(),
                            request.getItems(),
                            request.getTotalAmount()
                        );
                        return orderStore.createOrder(order);
                    }
                })
                .onSuccess(order -> {
                    OrderResponse response = new OrderResponse(
                        order.getOrderId(),
                        order.getState(),
                        order.getCreatedAt(),
                        order.getUpdatedAt()
                    );
                    
                    ctx.response()
                        .setStatusCode(202)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject.mapFrom(response).encode());
                    
                    logger.info("Order accepted: orderId={}, idempotencyKey={}", 
                        order.getOrderId(), order.getIdempotencyKey());
                })
                .onFailure(error -> {
                    logger.error("Failed to create order", error);
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                            .put("error", "Failed to process order request")
                            .encode());
                });
        } catch (Exception e) {
            logger.error("Error processing create order request", e);
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Invalid request format")
                    .encode());
        }
    }

    private void getOrder(RoutingContext ctx) {
        String orderId = ctx.pathParam("orderId");
        
        orderStore.findByOrderId(orderId)
            .onSuccess(order -> {
                if (order == null) {
                    ctx.response()
                        .setStatusCode(404)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                            .put("error", "Order not found")
                            .encode());
                } else {
                    OrderResponse response = new OrderResponse(
                        order.getOrderId(),
                        order.getState(),
                        order.getCreatedAt(),
                        order.getUpdatedAt()
                    );
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject.mapFrom(response).encode());
                }
            })
            .onFailure(error -> {
                logger.error("Failed to retrieve order: {}", orderId, error);
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error", "Failed to retrieve order")
                        .encode());
            });
    }

    private void healthCheck(RoutingContext ctx) {
        ctx.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("status", "healthy").encode());
    }

    private String validateRequest(CreateOrderRequest request) {
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().trim().isEmpty()) {
            return "idempotencyKey is required";
        }
        if (request.getCustomerId() == null || request.getCustomerId().trim().isEmpty()) {
            return "customerId is required";
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return "items are required";
        }
        if (request.getTotalAmount() == null || request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "totalAmount must be greater than 0";
        }
        return null;
    }
}

