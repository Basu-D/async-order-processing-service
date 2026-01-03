package com.orderprocessing.worker;

import com.orderprocessing.model.Order;
import com.orderprocessing.processor.OrderProcessor;
import com.orderprocessing.store.OrderStore;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderWorker extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(OrderWorker.class);
    
    private final OrderStore orderStore;
    private final OrderProcessor orderProcessor;
    private final int pollIntervalMs;
    private final int batchSize;
    private long timerId;

    public OrderWorker(OrderStore orderStore, OrderProcessor orderProcessor, int pollIntervalMs, int batchSize) {
        this.orderStore = orderStore;
        this.orderProcessor = orderProcessor;
        this.pollIntervalMs = pollIntervalMs;
        this.batchSize = batchSize;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        logger.info("Starting OrderWorker with pollInterval={}ms, batchSize={}", pollIntervalMs, batchSize);
        processPendingOrders();
        timerId = vertx.setPeriodic(pollIntervalMs, id -> processPendingOrders());
        startPromise.complete();
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        logger.info("Stopping OrderWorker");
        if (timerId > 0) {
            vertx.cancelTimer(timerId);
        }
        stopPromise.complete();
    }

    private void processPendingOrders() {
        orderStore.fetchPendingOrders(batchSize)
            .onSuccess(orders -> {
                if (orders.isEmpty()) {
                    logger.debug("No pending orders found");
                    return;
                }
                
                logger.info("Found {} pending order(s) to process", orders.size());

                for (Order order : orders) {
                    processOrder(order);
                }
            })
            .onFailure(error -> {
                logger.error("Failed to fetch pending orders", error);
            });
    }

    private void processOrder(Order order) {
        logger.info("Picked up order for processing: orderId={}", order.getOrderId());
        
        orderStore.markAsProcessing(order.getId())
            .compose(v -> {
                logger.debug("Order marked as PROCESSING: orderId={}", order.getOrderId());
                return orderProcessor.processOrder(order);
            })
            .onSuccess(v -> {
                logger.info("Order processing completed: orderId={}", order.getOrderId());
            })
            .onFailure(error -> {
                logger.error("Order processing failed: orderId={}", order.getOrderId(), error);
            });
    }
}

