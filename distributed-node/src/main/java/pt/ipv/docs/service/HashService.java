package pt.ipv.docs.service;

import org.springframework.stereotype.Service;
import pt.ipv.docs.model.DocumentEntry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
public class HashService {

    public String cidVectorHash(List<DocumentEntry> documents) {
        try {
            String canonical = documents.stream()
                    .map(DocumentEntry::cid)
                    .reduce("", (left, right) ->
                            left.isEmpty() ? right : left + "\n" + right
                    );

            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder();
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível calcular SHA-256", exception);
        }
    }
}