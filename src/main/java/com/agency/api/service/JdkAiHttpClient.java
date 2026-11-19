package com.agency.api.service;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class JdkAiHttpClient implements AiHttpClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public Response post(URI uri, Duration timeout, Map<String, String> headers, String body) throws Exception {
        var builder = HttpRequest.newBuilder(uri).timeout(timeout).POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }
}
