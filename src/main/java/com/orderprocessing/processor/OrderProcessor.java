package com.orderprocessing.processor;

import com.orderprocessing.model.Order;
import com.orderprocessing.store.OrderStore;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public class OrderProcessor {
    private static final Logger logger = LoggerFactory.getLogger(OrderProcessor.class);
    private final OrderStore orderStore;

    public OrderProcessor(OrderStore orderStore) {
        this.orderStore = orderStore;
    }

    public Future<Void> processOrder(Order order) {
        logger.info("Processing order: orderId={}, customerId={}, totalAmount={}", 
            order.getOrderId(), order.getCustomerId(), order.getTotalAmount());

        return validateOrder(order)
            .compose(v -> {
                logger.debug("Order validated: {}", order.getOrderId());
                return processPayment(order);
            })
            .compose(v -> {
                logger.debug("Payment processed: {}", order.getOrderId());
                return reserveInventory(order);
            })
            .compose(v -> {
                logger.debug("Inventory reserved: {}", order.getOrderId());
                return createShippingLabel(order);
            })
            .compose(v -> {
                logger.debug("Shipping label created: {}", order.getOrderId());
                return orderStore.markAsCompleted(order.getId());
            })
            .onSuccess(v -> {
                logger.info("Order completed successfully: orderId={}", order.getOrderId());
            })
            .onFailure(error -> {
                logger.error("Order processing failed: orderId={}", order.getOrderId(), error);
                orderStore.markAsFailed(order.getId(), error.getMessage())
                    .onFailure(markFailedError -> {
                        logger.error("Failed to mark order as failed: orderId={}", order.getOrderId(), markFailedError);
                    });
            });
    }

    private Future<Void> validateOrder(Order order) {
        return Future.succeededFuture();
    }

    private Future<Void> processPayment(Order order) {
        return simulateAsyncOperation("payment processing", 100, 500);
    }

    private Future<Void> reserveInventory(Order order) {
        return simulateAsyncOperation("inventory reservation", 50, 300);
    }

    private Future<Void> createShippingLabel(Order order) {
        return simulateAsyncOperation("shipping label creation", 200, 400);
    }

    private Future<Void> simulateAsyncOperation(String operation, int minDelayMs, int maxDelayMs) {
        return Future.future(promise -> {
            int delay = ThreadLocalRandom.current().nextInt(minDelayMs, maxDelayMs);
            
            if (ThreadLocalRandom.current().nextDouble() < 0.05) {
                promise.fail(new RuntimeException("Simulated failure during " + operation));
                return;
            }
            
            new Thread(() -> {
                try {
                    Thread.sleep(delay);
                    promise.complete();
                } catch (InterruptedException e) {
                    promise.fail(e);
                }
            }).start();
        });
    }
}

