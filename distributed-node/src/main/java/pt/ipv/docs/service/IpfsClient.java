package pt.ipv.docs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class IpfsClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public IpfsClient(
            @Value("${ipfs.rpc:http://127.0.0.1:5001}") String ipfsRpc,
            ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.builder().baseUrl(ipfsRpc).build();
        this.objectMapper = objectMapper;
    }

    public String addAndPin(byte[] content, String filename) throws Exception {
        ByteArrayResource file = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file);

        String json = restClient.post()
                .uri("/api/v0/add?pin=true")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode response = objectMapper.readTree(json);
        return response.get("Hash").asText();
    }
}
