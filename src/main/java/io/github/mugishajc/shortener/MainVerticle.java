package io.github.mugishajc.shortener;

import io.github.mugishajc.shortener.service.LinkService;
import io.github.mugishajc.shortener.service.UrlValidator;
import io.github.mugishajc.shortener.store.InMemoryLinkStore;
import io.github.mugishajc.shortener.web.ApiException;
import io.github.mugishajc.shortener.web.LinkRoutes;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.net.URI;
import java.time.Clock;

public class MainVerticle extends AbstractVerticle {

    private int actualPort;

    @Override
    public void start(Promise<Void> startPromise) {
        int port = config().getInteger("port", 8080);
        String baseUrl = config().getString("baseUrl", "http://localhost:" + port);

        String ownHost = URI.create(baseUrl).getHost();
        LinkService service = new LinkService(new InMemoryLinkStore(), Clock.systemUTC(), new UrlValidator(ownHost));

        Router router = Router.router(vertx);
        router.get("/health").handler(ctx -> ctx.json(new JsonObject().put("status", "UP")));
        new LinkRoutes(service, baseUrl).mount(router);
        router.route().failureHandler(MainVerticle::handleFailure);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(server -> {
                    actualPort = server.actualPort();
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    private static void handleFailure(RoutingContext ctx) {
        int status;
        String message;
        if (ctx.failure() instanceof ApiException e) {
            status = e.status();
            message = e.getMessage();
        } else if (ctx.failure() == null) {
            // ctx.fail(statusCode) without an exception, e.g. 413 from the body handler
            status = ctx.statusCode();
            message = "Request failed";
        } else {
            status = 500;
            message = "Internal server error";
        }
        if (ctx.response().ended()) {
            return;
        }
        ctx.response()
                .setStatusCode(status)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("status", status).put("error", message).encode());
    }

    /** The port the server is bound to, useful when started with port 0. */
    public int actualPort() {
        return actualPort;
    }
}
