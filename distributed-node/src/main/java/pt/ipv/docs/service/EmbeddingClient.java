package pt.ipv.docs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class EmbeddingClient {

    private final String embeddingUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EmbeddingClient(
            @Value("${embedding.url:http://127.0.0.1:8001}") String embeddingUrl,
            ObjectMapper objectMapper
    ) {
        this.embeddingUrl = embeddingUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public float[] embed(String text) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(
                new EmbedRequest(text)
        );

        System.out.println("[leader] EMBEDDING_HTTP_1_1");
        System.out.println("[leader] JSON enviado: " + jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(embeddingUrl + "/embed"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        jsonBody,
                        StandardCharsets.UTF_8
                ))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        System.out.println(
                "[leader] Resposta embeddings: HTTP "
                        + response.statusCode()
                        + " | "
                        + response.body()
        );

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Embedding service devolveu HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );
        }

        JsonNode responseJson = objectMapper.readTree(response.body());
        JsonNode values = responseJson.get("embedding");

        if (values == null) {
            throw new IllegalStateException(
                    "Resposta sem campo embedding: " + response.body()
            );
        }

        float[] vector = new float[values.size()];

        for (int i = 0; i < values.size(); i++) {
            vector[i] = (float) values.get(i).asDouble();
        }

        System.out.println(
                "[leader] Embedding recebido: dimensão " + vector.length
        );

        return vector;
    }

    private record EmbedRequest(String text) {
    }
}