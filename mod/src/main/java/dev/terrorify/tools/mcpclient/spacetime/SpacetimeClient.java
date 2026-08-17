package dev.terrorify.tools.mcpclient.spacetime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal SpacetimeDB HTTP client — mirrors the exact wire format Stomzy's
 * own {@code SpacetimeBootstrap}/{@code MaincloudClient} already use
 * (POST /v1/database/{db}/sql, text/plain raw SQL; POST
 * /v1/database/{db}/call/{reducer}, JSON array body), so this stays wire-
 * compatible with whatever SpacetimeDB deployment the connected server uses.
 * No SDK dependency — plain {@code java.net.http}, same as the Java backend.
 *
 * <p>All calls are async and fire-and-forget-safe: a slow/unreachable
 * SpacetimeDB host degrades to "no debug report", never blocks or freezes
 * the client thread.
 */
public final class SpacetimeClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("mcpclient");

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    /** Fire-and-forget reducer call. Never throws; logs and swallows failures. */
    public void callReducerAsync(String db, String reducerName, JsonArray args) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(SpacetimeConfig.URI + "/v1/database/" + db + "/call/" + reducerName))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(args.toString()))
            .build();

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete((resp, err) -> {
                if (err != null) {
                    LOGGER.debug("mcpclient: reducer {} call failed: {}", reducerName, err.toString());
                } else if (resp.statusCode() >= 300) {
                    LOGGER.debug("mcpclient: reducer {} returned {}: {}", reducerName, resp.statusCode(), resp.body());
                }
            });
    }

    /** Async SQL query; resolves to the row array (empty on any failure — never throws). */
    public CompletableFuture<JsonArray> sqlRowsAsync(String db, String sql) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(SpacetimeConfig.URI + "/v1/database/" + db + "/sql"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "text/plain")
            .POST(HttpRequest.BodyPublishers.ofString(sql))
            .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> {
                if (resp.statusCode() >= 300) {
                    LOGGER.debug("mcpclient: sql query returned {}: {}", resp.statusCode(), resp.body());
                    return new JsonArray();
                }
                return rowsOf(resp.body());
            })
            .exceptionally(err -> {
                LOGGER.debug("mcpclient: sql query failed: {}", err.toString());
                return new JsonArray();
            });
    }

    /**
     * SpacetimeDB's SQL HTTP response is {@code [{"schema":...,"rows":[...]}]}
     * (one statement result per query) — flatten to the first statement's rows,
     * matching Stomzy's own {@code rowsOf} helper.
     */
    private static JsonArray rowsOf(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonArray() || parsed.getAsJsonArray().isEmpty()) {
                return new JsonArray();
            }
            JsonObject statement = parsed.getAsJsonArray().get(0).getAsJsonObject();
            return statement.has("rows") ? statement.getAsJsonArray("rows") : new JsonArray();
        } catch (Exception e) {
            return new JsonArray();
        }
    }
}
