package io.github.mugishajc.shortener.web;

import io.github.mugishajc.shortener.service.LinkService;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class LinkRoutes {

    private static final long MAX_BODY_BYTES = 16 * 1024;

    private final LinkService service;
    private final String baseUrl;

    public LinkRoutes(LinkService service, String baseUrl) {
        this.service = service;
        this.baseUrl = baseUrl;
    }

    public void mount(Router router) {
        router.route("/api/*").handler(BodyHandler.create().setBodyLimit(MAX_BODY_BYTES));
        router.post("/api/links").handler(this::create);
        router.get("/api/links/:code").handler(this::details);
        router.delete("/api/links/:code").handler(this::delete);
        // registered last so it never shadows /api or /health
        router.get("/:code").handler(this::redirect);
    }

    private void create(RoutingContext ctx) {
        JsonObject body = jsonBody(ctx);
        String url = body.getString("url");
        if (url == null || url.isBlank()) {
            throw ApiException.badRequest("Field 'url' is required");
        }
        service.shorten(url.trim())
                .onSuccess(link -> ctx.response()
                        .setStatusCode(201)
                        .putHeader("Location", baseUrl + "/api/links/" + link.code())
                        .putHeader("Content-Type", "application/json")
                        .end(link.toJson(baseUrl).encode()))
                .onFailure(ctx::fail);
    }

    private void details(RoutingContext ctx) {
        String code = ctx.pathParam("code");
        service.find(code)
                .onSuccess(found -> found.ifPresentOrElse(
                        link -> ctx.json(link.toJson(baseUrl)),
                        () -> ctx.fail(ApiException.notFound("No link with code '" + code + "'"))))
                .onFailure(ctx::fail);
    }

    private void delete(RoutingContext ctx) {
        String code = ctx.pathParam("code");
        service.delete(code)
                .onSuccess(deleted -> {
                    if (deleted) {
                        ctx.response().setStatusCode(204).end();
                    } else {
                        ctx.fail(ApiException.notFound("No link with code '" + code + "'"));
                    }
                })
                .onFailure(ctx::fail);
    }

    private void redirect(RoutingContext ctx) {
        String code = ctx.pathParam("code");
        service.resolve(code)
                .onSuccess(found -> found.ifPresentOrElse(
                        link -> ctx.response()
                                .setStatusCode(302)
                                .putHeader("Location", link.targetUrl())
                                // every visit must reach us or the click count is wrong
                                .putHeader("Cache-Control", "no-store")
                                .end(),
                        () -> ctx.fail(ApiException.notFound("No link with code '" + code + "'"))))
                .onFailure(ctx::fail);
    }

    private static JsonObject jsonBody(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                throw ApiException.badRequest("Request body must be a JSON object");
            }
            return body;
        } catch (DecodeException | ClassCastException e) {
            throw ApiException.badRequest("Request body must be a JSON object");
        }
    }
}
