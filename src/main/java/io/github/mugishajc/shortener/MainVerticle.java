package io.github.mugishajc.shortener;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class MainVerticle extends AbstractVerticle {

    private int actualPort;

    @Override
    public void start(Promise<Void> startPromise) {
        int port = config().getInteger("port", 8080);

        Router router = Router.router(vertx);
        router.get("/health").handler(ctx -> ctx.json(new JsonObject().put("status", "UP")));

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(server -> {
                    actualPort = server.actualPort();
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    /** The port the server is bound to, useful when started with port 0. */
    public int actualPort() {
        return actualPort;
    }
}
