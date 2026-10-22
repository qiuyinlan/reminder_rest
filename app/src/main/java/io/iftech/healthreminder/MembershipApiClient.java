package io.iftech.healthreminder;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class MembershipApiClient {
    interface Transport {
        Response execute(String method, String url, String json, String token) throws IOException;
    }

    static final class Response {
        final int status;
        final String body;
        Response(int status, String body) { this.status = status; this.body = body == null ? "" : body; }
    }

    private final String baseUrl;
    private final Transport transport;

    MembershipApiClient(String baseUrl) { this(baseUrl, MembershipApiClient::httpRequest); }

    MembershipApiClient(String baseUrl, Transport transport) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.transport = transport;
    }

    Response request(String method, String path, JSONObject body, String token) throws IOException {
        if (baseUrl.isEmpty()) throw new IOException("会员服务地址尚未配置");
        return transport.execute(method, baseUrl + path, body == null ? null : body.toString(), token);
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return "";
        if (!trimmed.startsWith("https://")) return "";
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    private static Response httpRequest(String method, String url, String json, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        if (json != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        }
        if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (json != null) {
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Response(status, stream == null ? "" : readAll(stream));
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
