package com.orderprocessing.store;

import com.orderprocessing.model.Order;
import com.orderprocessing.model.OrderState;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderStore {
    private final Pool pool;

    public OrderStore(Pool pool) {
        this.pool = pool;
    }

    public Future<Order> createOrder(Order order) {
        String sql = """
            INSERT INTO orders (order_id, idempotency_key, customer_id, items, total_amount, state, created_at, updated_at)
            VALUES ($1, $2, $3, $4::jsonb, $5, $6, $7, $8)
            RETURNING id, order_id, idempotency_key, customer_id, items, total_amount, state, 
                      error_message, created_at, updated_at, processed_at
            """;

        OffsetDateTime createdAt = order.getCreatedAt() != null
            ? order.getCreatedAt().atOffset(ZoneOffset.UTC) 
            : OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime updatedAt = order.getUpdatedAt() != null 
            ? order.getUpdatedAt().atOffset(ZoneOffset.UTC) 
            : OffsetDateTime.now(ZoneOffset.UTC);
        
        return pool.withConnection(conn ->
            conn.preparedQuery(sql)
                .execute(Tuple.of(
                    order.getOrderId(),
                    order.getIdempotencyKey(),
                    order.getCustomerId(),
                    JsonObject.mapFrom(order.getItems()).encode(),
                    order.getTotalAmount(),
                    order.getState().name(),
                    createdAt,
                    updatedAt
                ))
                .map(rows -> {
                    if (rows.size() == 0) {
                        throw new RuntimeException("Failed to create order");
                    }
                    return mapRowToOrder(rows.iterator().next());
                })
        );
    }

    public Future<Order> findByIdempotencyKey(String idempotencyKey) {
        String sql = """
            SELECT id, order_id, idempotency_key, customer_id, items, total_amount, state,
                   error_message, created_at, updated_at, processed_at
            FROM orders
            WHERE idempotency_key = $1
            """;

        return pool.withConnection(conn ->
            conn.preparedQuery(sql)
                .execute(Tuple.of(idempotencyKey))
                .map(rows -> {
                    if (rows.size() == 0) {
                        return null;
                    }
                    return mapRowToOrder(rows.iterator().next());
                })
        );
    }

    public Future<Order> findByOrderId(String orderId) {
        String sql = """
            SELECT id, order_id, idempotency_key, customer_id, items, total_amount, state,
                   error_message, created_at, updated_at, processed_at
            FROM orders
            WHERE order_id = $1
            """;

        return pool.withConnection(conn ->
            conn.preparedQuery(sql)
                .execute(Tuple.of(orderId))
                .map(rows -> {
                    if (rows.size() == 0) {
                        return null;
                    }
                    return mapRowToOrder(rows.iterator().next());
                })
        );
    }

    public Future<List<Order>> fetchPendingOrders(int limit) {
        String sql = """
            SELECT id, order_id, idempotency_key, customer_id, items, total_amount, state,
                   error_message, created_at, updated_at, processed_at
            FROM orders
            WHERE state = 'RECEIVED'
            ORDER BY created_at ASC
            LIMIT $1
            FOR UPDATE SKIP LOCKED
            """;

        return pool.withConnection(conn ->
            conn.preparedQuery(sql)
                .execute(Tuple.of(limit))
                .map(rows -> {
                    List<Order> orders = new ArrayList<>();
                    for (Row row : rows) {
                        orders.add(mapRowToOrder(row));
                    }
                    return orders;
                })
        );
    }

    public Future<Void> updateOrderState(UUID orderId, OrderState newState, String errorMessage) {
        String sql = """
            UPDATE orders
            SET state = $1, error_message = $2, updated_at = $3, processed_at = CASE WHEN $1 IN ('COMPLETED', 'FAILED') THEN $3 ELSE processed_at END
            WHERE id = $4
            """;

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return pool.withConnection(conn ->
            conn.preparedQuery(sql)
                .execute(Tuple.of(newState.name(), errorMessage, now, orderId))
                .map(rows -> null)
        );
    }

    public Future<Void> markAsProcessing(UUID orderId) {
        return updateOrderState(orderId, OrderState.PROCESSING, null);
    }

    public Future<Void> markAsCompleted(UUID orderId) {
        return updateOrderState(orderId, OrderState.COMPLETED, null);
    }

    public Future<Void> markAsFailed(UUID orderId, String errorMessage) {
        return updateOrderState(orderId, OrderState.FAILED, errorMessage);
    }

    private Order mapRowToOrder(Row row) {
        Order order = new Order();
        order.setId(row.getUUID("id"));
        order.setOrderId(row.getString("order_id"));
        order.setIdempotencyKey(row.getString("idempotency_key"));
        order.setCustomerId(row.getString("customer_id"));

        JsonObject itemsJson = new JsonObject(row.getString("items"));
        order.setItems(itemsJson.getMap());
        
        order.setTotalAmount(row.getBigDecimal("total_amount"));
        order.setState(OrderState.valueOf(row.getString("state")));
        order.setErrorMessage(row.getString("error_message"));
        
        if (row.getOffsetDateTime("created_at") != null) {
            order.setCreatedAt(row.getOffsetDateTime("created_at").toInstant());
        }
        if (row.getOffsetDateTime("updated_at") != null) {
            order.setUpdatedAt(row.getOffsetDateTime("updated_at").toInstant());
        }
        if (row.getOffsetDateTime("processed_at") != null) {
            order.setProcessedAt(row.getOffsetDateTime("processed_at").toInstant());
        }
        
        return order;
    }
}

