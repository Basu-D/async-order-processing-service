package com.orderprocessing.config;

import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

public class DatabaseConfig {
    
    public static Pool createPool(io.vertx.core.Vertx vertx, JsonObject config) {
        JsonObject dbConfig = config.getJsonObject("database", new JsonObject());
        
        String host = System.getenv("DATABASE_HOST");
        if (host == null || host.isEmpty()) {
            host = dbConfig.getString("host", "localhost");
        }
        
        String portStr = System.getenv("DATABASE_PORT");
        int port = portStr != null && !portStr.isEmpty() 
            ? Integer.parseInt(portStr) 
            : dbConfig.getInteger("port", 5432);
        
        String database = System.getenv("DATABASE_DATABASE");
        if (database == null || database.isEmpty()) {
            database = dbConfig.getString("database", "orderdb");
        }
        
        String user = System.getenv("DATABASE_USER");
        if (user == null || user.isEmpty()) {
            user = dbConfig.getString("user", "postgres");
        }
        
        String password = System.getenv("DATABASE_PASSWORD");
        if (password == null || password.isEmpty()) {
            password = dbConfig.getString("password", "postgres");
        }
        
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(host)
            .setPort(port)
            .setDatabase(database)
            .setUser(user)
            .setPassword(password);
        
        PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(dbConfig.getInteger("maxPoolSize", 10));
        
        return io.vertx.pgclient.PgPool.pool(vertx, connectOptions, poolOptions);
    }
}

