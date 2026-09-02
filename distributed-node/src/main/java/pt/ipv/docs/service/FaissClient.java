package pt.ipv.docs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pt.ipv.docs.model.DocumentEntry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class FaissClient {

    private final String embeddingUrl;
    private final String peerId;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FaissClient(
            @Value("${embedding.url:http://127.0.0.1:8001}") String embeddingUrl,
            @Value("${peer.id:leader}") String peerId,
            ObjectMapper objectMapper
    ) {
        this.embeddingUrl = embeddingUrl;
        this.peerId = peerId;
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public void add(DocumentEntry document) {
        try {
            FaissAddRequest payload = new FaissAddRequest(
                    peerId,
                    document.cid(),
                    document.filename(),
                    document.embedding()
            );

            String jsonBody = objectMapper.writeValueAsString(payload);

            System.out.printf(
                    "[%s] FAISS_ADD_HTTP_1_1 | CID=%s | dimensão=%d%n",
                    peerId,
                    document.cid(),
                    document.embedding().length
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(embeddingUrl + "/faiss/add"))
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

            System.out.printf(
                    "[%s] FAISS_ADD resposta | HTTP %d | %s%n",
                    peerId,
                    response.statusCode(),
                    response.body()
            );

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "FAISS devolveu HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Falha ao adicionar documento ao índice FAISS",
                    exception
            );
        }
    }

    private record FaissAddRequest(
            String peer_id,
            String cid,
            String filename,
            float[] embedding
    ) {
    }
}