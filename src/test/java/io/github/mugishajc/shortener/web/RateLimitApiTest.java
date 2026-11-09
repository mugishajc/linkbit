package io.github.mugishajc.shortener.web;

import io.github.mugishajc.shortener.MainVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(VertxExtension.class)
class RateLimitApiTest {

    private WebClient client;

    @BeforeEach
    void deploy(Vertx vertx, VertxTestContext testContext) {
        MainVerticle verticle = new MainVerticle();
        JsonObject config = new JsonObject()
                .put("port", 0)
                .put("baseUrl", "http://sho.rt")
                .put("rateLimitCapacity", 2)
                .put("rateLimitPerMinute", 1)
                .put("trustForwardedFor", true);
        vertx.deployVerticle(verticle, new DeploymentOptions().setConfig(config))
                .onComplete(testContext.succeeding(id -> {
                    client = WebClient.create(vertx, new WebClientOptions()
                            .setDefaultHost("localhost")
                            .setDefaultPort(verticle.actualPort())
                            .setFollowRedirects(false));
                    testContext.completeNow();
                }));
    }

    @Test
    void blocksClientAfterBurst(VertxTestContext ctx) {
        create("10.0.0.1")
                .compose(first -> {
                    ctx.verify(() -> {
                        assertEquals(201, first.statusCode());
                        assertEquals("2", first.getHeader("X-RateLimit-Limit"));
                        assertEquals("1", first.getHeader("X-RateLimit-Remaining"));
                    });
                    return create("10.0.0.1");
                })
                .compose(second -> create("10.0.0.1"))
                .onComplete(ctx.succeeding(third -> ctx.verify(() -> {
                    assertEquals(429, third.statusCode());
                    assertNotNull(third.getHeader("Retry-After"));
                    assertEquals(429, third.bodyAsJsonObject().getInteger("status"));
                    ctx.completeNow();
                })));
    }

    @Test
    void limitsEachClientSeparately(VertxTestContext ctx) {
        create("10.0.0.2")
                .compose(r -> create("10.0.0.2"))
                .compose(r -> create("10.0.0.2"))
                .compose(blocked -> {
                    ctx.verify(() -> assertEquals(429, blocked.statusCode()));
                    return create("10.0.0.3");
                })
                .onComplete(ctx.succeeding(other -> ctx.verify(() -> {
                    assertEquals(201, other.statusCode());
                    ctx.completeNow();
                })));
    }

    @Test
    void redirectsAreNotLimited(VertxTestContext ctx) {
        Future<HttpResponse<Buffer>> chain = client.get("/abcd").send();
        for (int i = 0; i < 5; i++) {
            chain = chain.compose(r -> client.get("/abcd").send());
        }
        chain.onComplete(ctx.succeeding(res -> ctx.verify(() -> {
            assertEquals(404, res.statusCode());
            ctx.completeNow();
        })));
    }

    private Future<HttpResponse<Buffer>> create(String ip) {
        return client.post("/api/links")
                .putHeader("X-Forwarded-For", ip + ", 172.16.0.1")
                .sendJsonObject(new JsonObject().put("url", "https://example.com"));
    }
}
