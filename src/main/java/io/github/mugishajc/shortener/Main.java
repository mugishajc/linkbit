package io.github.mugishajc.shortener;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        JsonObject config = new JsonObject().put("port", Integer.parseInt(System.getenv().getOrDefault("PORT", "8080")));

        vertx.deployVerticle(new MainVerticle(), new DeploymentOptions().setConfig(config))
                .onSuccess(id -> System.out.println("URL shortener listening on port " + config.getInteger("port")))
                .onFailure(err -> {
                    System.err.println("Failed to start: " + err.getMessage());
                    vertx.close();
                });
    }
}
