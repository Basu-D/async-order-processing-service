package com.orderprocessing;

import com.orderprocessing.api.OrderApi;
import com.orderprocessing.config.DatabaseConfig;
import com.orderprocessing.processor.OrderProcessor;
import com.orderprocessing.store.OrderStore;
import com.orderprocessing.worker.OrderWorker;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        String configPath = System.getenv("CONFIG_PATH");
        if (configPath == null || configPath.isEmpty()) {
            configPath = "src/main/resources/application.json";
        }
        
        io.vertx.config.ConfigStoreOptions fileStore = new io.vertx.config.ConfigStoreOptions()
            .setType("file")
            .setConfig(new JsonObject().put("path", configPath));
        
        io.vertx.config.ConfigStoreOptions envStore = new io.vertx.config.ConfigStoreOptions()
            .setType("env");
        
        io.vertx.config.ConfigRetrieverOptions options = new io.vertx.config.ConfigRetrieverOptions()
            .addStore(fileStore)
            .addStore(envStore);
        
        io.vertx.config.ConfigRetriever retriever = io.vertx.config.ConfigRetriever.create(vertx, options);
        
        retriever.getConfig()
            .onSuccess(config -> {
                logger.info("Configuration loaded");
                deployApplication(vertx, config);
            })
            .onFailure(error -> {
                logger.error("Failed to load configuration", error);
                System.exit(1);
            });
    }

    private static void deployApplication(Vertx vertx, JsonObject config) {
        var pool = DatabaseConfig.createPool(vertx, config);

        OrderStore orderStore = new OrderStore(pool);

        OrderProcessor orderProcessor = new OrderProcessor(orderStore);

        vertx.deployVerticle(new ApiVerticle(orderStore, config))
            .compose(apiId -> {
                logger.info("API server deployed: {}", apiId);

                JsonObject workerConfig = config.getJsonObject("worker", new JsonObject());
                int pollIntervalMs = workerConfig.getInteger("pollIntervalMs", 2000);
                int batchSize = workerConfig.getInteger("batchSize", 10);
                
                return vertx.deployVerticle(
                    new OrderWorker(orderStore, orderProcessor, pollIntervalMs, batchSize),
                    new DeploymentOptions().setInstances(1)
                );
            })
            .onSuccess(workerId -> {
                logger.info("Order worker deployed: {}", workerId);
                logger.info("Application started successfully");
            })
            .onFailure(error -> {
                logger.error("Failed to deploy application", error);
                System.exit(1);
            });
    }

    static class ApiVerticle extends AbstractVerticle {
        private final OrderStore orderStore;
        private final JsonObject config;
        
        public ApiVerticle(OrderStore orderStore, JsonObject config) {
            this.orderStore = orderStore;
            this.config = config;
        }

        @Override
        public void start(Promise<Void> startPromise) {
            int port = config.getJsonObject("server", new JsonObject()).getInteger("port", 8080);
            
            OrderApi orderApi = new OrderApi(vertx, orderStore);
            Router router = orderApi.createRouter();
            
            vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(server -> {
                    logger.info("HTTP server started on port {}", port);
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
        }
    }
}

