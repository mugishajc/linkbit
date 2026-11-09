package io.github.mugishajc.shortener;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.TimeUnit;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        JsonObject config = AppConfig.fromEnv(System.getenv());
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new MainVerticle(), new DeploymentOptions().setConfig(config))
                .onSuccess(id -> System.out.println("URL shortener listening on port "
                        + config.getInteger("port") + ", public base URL " + config.getString("baseUrl")))
                .onFailure(err -> {
                    System.err.println("Failed to start: " + err.getMessage());
                    vertx.close();
                });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // shutting down anyway
            }
        }));
    }
}
