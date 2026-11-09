package io.github.mugishajc.shortener.web;

import io.github.mugishajc.shortener.MainVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class LinkApiTest {

    private static final String BASE_URL = "http://sho.rt";

    private WebClient client;

    @BeforeEach
    void deploy(Vertx vertx, VertxTestContext testContext) {
        MainVerticle verticle = new MainVerticle();
        JsonObject config = new JsonObject().put("port", 0).put("baseUrl", BASE_URL);
        vertx.deployVerticle(verticle, new DeploymentOptions().setConfig(config))
                .onComplete(testContext.succeeding(id -> {
                    client = WebClient.create(vertx, new WebClientOptions()
                            .setDefaultHost("localhost")
                            .setDefaultPort(verticle.actualPort())
                            .setFollowRedirects(false));
                    testContext.completeNow();
                }));
    }

    @AfterEach
    void closeClient() {
        client.close();
    }

    @Test
    void healthEndpointIsUp(VertxTestContext ctx) {
        client.get("/health").send().onComplete(ctx.succeeding(res -> ctx.verify(() -> {
            assertEquals(200, res.statusCode());
            assertEquals("UP", res.bodyAsJsonObject().getString("status"));
            ctx.completeNow();
        })));
    }

    @Test
    void createsGeneratedCode(VertxTestContext ctx) {
        create(new JsonObject().put("url", "https://example.com/docs/getting-started"))
                .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                    assertEquals(201, res.statusCode());
                    JsonObject body = res.bodyAsJsonObject();
                    String code = body.getString("code");
                    assertEquals(4, code.length(), "first generated code starts at four characters");
                    assertEquals(BASE_URL + "/" + code, body.getString("shortUrl"));
                    assertEquals(BASE_URL + "/api/links/" + code, res.getHeader("Location"));
                    assertEquals(0L, body.getLong("clicks"));
                    ctx.completeNow();
                })));
    }

    @Test
    void generatedCodesAreUnique(VertxTestContext ctx) {
        JsonObject req = new JsonObject().put("url", "https://example.com");
        create(req).compose(first -> create(req).map(second -> new String[]{
                        first.bodyAsJsonObject().getString("code"),
                        second.bodyAsJsonObject().getString("code")}))
                .onComplete(ctx.succeeding(codes -> ctx.verify(() -> {
                    assertTrue(!codes[0].equals(codes[1]));
                    ctx.completeNow();
                })));
    }

    @Test
    void redirectsAndCountsClicks(VertxTestContext ctx) {
        create(new JsonObject().put("url", "https://example.com/pricing").put("alias", "pricing"))
                .compose(created -> client.get("/pricing").send())
                .compose(redirect -> {
                    ctx.verify(() -> {
                        assertEquals(302, redirect.statusCode());
                        assertEquals("https://example.com/pricing", redirect.getHeader("Location"));
                        assertEquals("no-store", redirect.getHeader("Cache-Control"));
                    });
                    return client.get("/pricing").send();
                })
                .compose(second -> client.get("/api/links/pricing").send())
                .onComplete(ctx.succeeding(details -> ctx.verify(() -> {
                    JsonObject body = details.bodyAsJsonObject();
                    assertEquals(2L, body.getLong("clicks"));
                    assertNotNull(body.getString("lastClickedAt"));
                    ctx.completeNow();
                })));
    }

    @Test
    void lookingUpDetailsDoesNotCountAsClick(VertxTestContext ctx) {
        create(new JsonObject().put("url", "https://example.com").put("alias", "quiet"))
                .compose(created -> client.get("/api/links/quiet").send())
                .compose(first -> client.get("/api/links/quiet").send())
                .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                    assertEquals(0L, res.bodyAsJsonObject().getLong("clicks"));
                    ctx.completeNow();
                })));
    }

    @Test
    void unknownCodesReturn404(VertxTestContext ctx) {
        client.get("/zzzz").send()
                .compose(redirect -> {
                    ctx.verify(() -> assertEquals(404, redirect.statusCode()));
                    return client.get("/api/links/zzzz").send();
                })
                .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                    assertEquals(404, res.statusCode());
                    assertEquals("No link with code 'zzzz'", res.bodyAsJsonObject().getString("error"));
                    ctx.completeNow();
                })));
    }

    @Test
    void rejectsInvalidUrls(VertxTestContext ctx) {
        create(new JsonObject().put("url", "javascript:alert(1)"))
                .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                    assertEquals(400, res.statusCode());
                    assertEquals("Only http and https URLs can be shortened", res.bodyAsJsonObject().getString("error"));
                    ctx.completeNow();
                })));
    }

    @Test
    void rejectsLinksToItself(VertxTestContext ctx) {
        create(new JsonObject().put("url", "http://sho.rt/abcd"))
                .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                    assertEquals(400, res.statusCode());
                    ctx.completeNow();
                })));
    }

    @Test
    void rejectsMalformedBodies(VertxTestContext ctx) {
        client.post("/api/links").putHeader("Content-Type", "application/json")
                .sendBuffer(Buffer.buffer("{not json"))
                .compose(res -> {
                    ctx.verify(() -> assertEquals(400, res.statusCode()));
                    return create(new JsonObject().put("url", 42));
                })
                .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                    assertEquals(400, res.statusCode());
                    assertEquals("Field 'url' must be a string", res.bodyAsJsonObject().getString("error"));
                    ctx.completeNow();
                })));
    }

    @Test
    void duplicateAliasIsAConflict(VertxTestContext ctx) {
        JsonObject req = new JsonObject().put("url", "https://example.com").put("alias", "launch");
        create(req)
                .compose(first -> create(req))
                .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                    assertEquals(409, res.statusCode());
                    ctx.completeNow();
                })));
    }

    @Test
    void reservedAliasIsRejected(VertxTestContext ctx) {
        create(new JsonObject().put("url", "https://example.com").put("alias", "api"))
                .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                    assertEquals(400, res.statusCode());
                    ctx.completeNow();
                })));
    }

    @Test
    void deletesLinks(VertxTestContext ctx) {
        create(new JsonObject().put("url", "https://example.com").put("alias", "temp"))
                .compose(created -> client.delete("/api/links/temp").send())
                .compose(deleted -> {
                    ctx.verify(() -> assertEquals(204, deleted.statusCode()));
                    return client.get("/temp").send();
                })
                .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                    assertEquals(404, res.statusCode());
                    ctx.completeNow();
                })));
    }

    private io.vertx.core.Future<HttpResponse<Buffer>> create(JsonObject body) {
        return client.post("/api/links").sendJsonObject(body);
    }
}
