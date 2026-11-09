# Vert.x URL Shortener

A small URL shortener built on Eclipse Vert.x 4 and Vert.x Web. It creates short codes, redirects visitors, counts clicks and limits how fast each client IP can create links. Everything runs on the event loop with no blocking calls.

## Features

- Short codes are the base62 form of a sequence number (`0-9a-zA-Z`), so they never collide and stay short. The sequence starts at 62^3, so codes are at least four characters.
- Optional custom aliases such as `/spring-sale`, with a reserved word list and a 409 when the alias is taken.
- `302` redirects with `Cache-Control: no-store`, so every visit reaches the server and is counted.
- Click count and last click time per link.
- Target URLs must be absolute `http` or `https` URLs with a host, at most 2048 characters, and must not point back at the shortener.
- Per-IP token bucket rate limiting on link creation, with `X-RateLimit-*` and `Retry-After` headers.
- Storage sits behind a `LinkStore` interface that returns Vert.x futures. The included implementation is in memory; a database version can be added without touching the routes.

## Stack

- Java 21, Maven
- Vert.x 4 (`vertx-core`, `vertx-web`)
- JUnit 5 with `vertx-junit5` and `vertx-web-client` for HTTP tests
- `maven-shade-plugin` for a single runnable jar

## Running it

Requires JDK 21+ and Maven 3.9+.

```bash
# run from source
mvn compile exec:java

# or build the fat jar and run it
mvn package
java -jar target/vertx-url-shortener-1.0.0-SNAPSHOT-fat.jar
```

The server listens on port 8080 by default.

| Variable | Default | Meaning |
|----------|---------|---------|
| `PORT` | `8080` | HTTP port |
| `BASE_URL` | `http://localhost:<PORT>` | Public base used to build `shortUrl` values |
| `RATE_LIMIT_CAPACITY` | `20` | Burst size per client IP |
| `RATE_LIMIT_PER_MINUTE` | `20` | Refill rate per client IP |
| `TRUST_FORWARDED_FOR` | `false` | Take the client IP from `X-Forwarded-For`. Only enable behind a proxy that sets it |

## API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/links` | Create a short link. Body: `{"url": "...", "alias": "optional"}` |
| GET | `/api/links/{code}` | Link details and click stats (does not count as a click) |
| DELETE | `/api/links/{code}` | Delete a link |
| GET | `/{code}` | Redirect to the target URL and count the click |
| GET | `/health` | Liveness check |

Errors are JSON: `{"status": 400, "error": "Only http and https URLs can be shortened"}`.

### Examples

```bash
curl -s -X POST localhost:8080/api/links \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://vertx.io/docs/"}'
```

```json
{
  "code": "1000",
  "shortUrl": "http://localhost:8080/1000",
  "targetUrl": "https://vertx.io/docs/",
  "createdAt": "2026-11-09T05:41:34.670Z",
  "clicks": 0,
  "lastClickedAt": null
}
```

```bash
# custom alias
curl -s -X POST localhost:8080/api/links \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://example.com/pricing", "alias": "pricing"}'

# follow it
curl -i localhost:8080/pricing
# HTTP/1.1 302 Found
# Location: https://example.com/pricing

# check the stats
curl -s localhost:8080/api/links/pricing
```

Once a client uses up its burst, link creation returns:

```
HTTP/1.1 429 Too Many Requests
Retry-After: 3
X-RateLimit-Limit: 20
X-RateLimit-Remaining: 0
```

## How it fits together

```
Main             reads env vars, deploys MainVerticle
MainVerticle     builds the router, wires store, service and rate limiter, maps failures to JSON
web/LinkRoutes   HTTP handlers for the API and the redirect route
service/         LinkService (code generation, alias rules, URL checks)
store/           LinkStore interface and the in-memory implementation
ratelimit/       token bucket limiter and the Vert.x handler that applies it
code/Base62      encoding of sequence numbers into short codes
```

A few decisions worth calling out:

- Generated codes and custom aliases share one namespace. If a generated code happens to match an alias someone already took, the service moves on to the next sequence number.
- Clicks are recorded with `ConcurrentHashMap.computeIfPresent`, so concurrent redirects are not lost even with several verticle instances.
- Idle rate limit buckets are evicted every minute. A bucket is only dropped once it would be full again, so eviction never resets anyone's limit early.

## Tests

```bash
mvn verify
```

Unit tests cover the base62 codec, URL and alias rules, the store and the token bucket (with a fake clock). HTTP tests deploy the verticle on a random port and call it with `WebClient`, covering creation, redirects, click counting, error responses and the 429 path.

## License

MIT, see [LICENSE](LICENSE).
