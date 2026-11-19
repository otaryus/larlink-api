package com.agency.api.service;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public interface AiHttpClient {
    record Response(int statusCode, String body) {}
    Response post(URI uri, Duration timeout, Map<String, String> headers, String body) throws Exception;
}
