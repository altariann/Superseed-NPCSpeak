package com.l299l.npcspeak.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.l299l.npcspeak.config.PluginConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class OllamaBackend implements AiBackend {

    private final String url;
    private final String model;
    private final int timeoutSeconds;
    private final HttpClient httpClient;

    public OllamaBackend(PluginConfig config) {
        this.url = config.getOllamaUrl();
        this.model = config.getOllamaModel();
        this.timeoutSeconds = config.getOllamaTimeoutSeconds();
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public CompletableFuture<String> complete(List<AiMessage> messages) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(messages)))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException(
                                "Ollama returned HTTP " + response.statusCode() + ": " + response.body());
                    }
                    return parseContent(response.body());
                });
    }

    @Override
    public CompletableFuture<String> streamComplete(List<AiMessage> messages, Consumer<String> onToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(messages, true)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Ollama returned HTTP " + response.statusCode());
                    }
                    StringBuilder full = new StringBuilder();
                    response.body().forEach(line -> {
                        if (line.isBlank()) return;
                        try {
                            JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                            if (obj.has("message")) {
                                String token = obj.getAsJsonObject("message").get("content").getAsString();
                                if (!token.isEmpty()) {
                                    onToken.accept(token);
                                    full.append(token);
                                }
                            }
                        } catch (Exception ignored) {}
                    });
                    return full.toString().trim();
                });
    }

    private String buildBody(List<AiMessage> messages) {
        return buildBody(messages, false);
    }

    private String buildBody(List<AiMessage> messages, boolean stream) {
        return encodeRequest(model, messages, stream);
    }

    /** Shared wire format for the backend and opt-in local pilot; never adds inference options. */
    public static String encodeRequest(String model, List<AiMessage> messages, boolean stream) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", stream);
        JsonArray arr = new JsonArray();
        for (AiMessage msg : messages) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            m.addProperty("content", msg.content());
            arr.add(m);
        }
        body.add("messages", arr);
        return body.toString();
    }

    private String parseContent(String body) {
        return JsonParser.parseString(body)
                .getAsJsonObject()
                .getAsJsonObject("message")
                .get("content")
                .getAsString()
                .trim();
    }

    @Override
    public String getName() { return "Ollama"; }
}
