package pt.ipv.docs.model;

import pt.ipv.docs.service.HashService;

import java.util.ArrayList;
import java.util.List;

public class ClusterState {

    private long activeVersion = 0;
    private final List<DocumentEntry> activeDocuments = new ArrayList<>();

    private String stagingOperationId;
    private long stagingVersion = -1;
    private final List<DocumentEntry> stagingDocuments = new ArrayList<>();

    public synchronized List<DocumentEntry> commit(
            String operationId,
            long version,
            String expectedHash,
            HashService hashService
    ) {
        if (stagingOperationId == null || !stagingOperationId.equals(operationId)) {
            throw new IllegalStateException("Operação staging não encontrada");
        }

        if (stagingVersion != version) {
            throw new IllegalStateException("Versão do COMMIT não coincide com staging");
        }

        String localHash = hashService.cidVectorHash(stagingDocuments);
        if (!localHash.equals(expectedHash)) {
            throw new IllegalStateException("Hash local não coincide com a hash do COMMIT");
        }

        activeDocuments.clear();
        activeDocuments.addAll(stagingDocuments);
        activeVersion = stagingVersion;

        List<DocumentEntry> committedDocuments = List.copyOf(activeDocuments);
        clearStaging(operationId);
        return committedDocuments;
    }

    public synchronized PrepareResult prepare(PrepareMessage message) {
        if (message.version() <= activeVersion) {
            return PrepareResult.rejected("Versão antiga ou mensagem duplicada");
        }

        if (message.version() != activeVersion + 1) {
            return PrepareResult.conflict(
                    "Versão esperada: " + (activeVersion + 1)
                            + "; versão recebida: " + message.version()
            );
        }

        if (stagingOperationId != null && !stagingOperationId.equals(message.operationId())) {
            return PrepareResult.conflict(
                    "Já existe uma operação staging: " + stagingOperationId
            );
        }

        // Receção duplicada da mesma operação: não duplica CID nem embedding.
        if (stagingOperationId != null) {
            return PrepareResult.accepted(List.copyOf(stagingDocuments));
        }

        stagingOperationId = message.operationId();
        stagingVersion = message.version();
        stagingDocuments.clear();
        stagingDocuments.addAll(activeDocuments);
        stagingDocuments.add(new DocumentEntry(
                message.cid(),
                message.filename(),
                message.embedding()
        ));

        return PrepareResult.accepted(List.copyOf(stagingDocuments));
    }

    public synchronized long activeVersion() {
        return activeVersion;
    }

    public synchronized List<DocumentEntry> activeDocuments() {
        return List.copyOf(activeDocuments);
    }

    public synchronized String stagingOperationId() {
        return stagingOperationId;
    }

    public synchronized long stagingVersion() {
        return stagingVersion;
    }

    public synchronized List<DocumentEntry> stagingDocuments() {
        return List.copyOf(stagingDocuments);
    }

    public synchronized void clearStaging(String operationId) {
        if (stagingOperationId != null && stagingOperationId.equals(operationId)) {
            stagingOperationId = null;
            stagingVersion = -1;
            stagingDocuments.clear();
        }
    }

    public record PrepareResult(
            boolean accepted,
            String status,
            String reason,
            List<DocumentEntry> documents
    ) {
        public static PrepareResult accepted(List<DocumentEntry> documents) {
            return new PrepareResult(true, "OK", null, documents);
        }

        public static PrepareResult conflict(String reason) {
            return new PrepareResult(false, "CONFLICT", reason, List.of());
        }

        public static PrepareResult rejected(String reason) {
            return new PrepareResult(false, "REJECTED", reason, List.of());
        }
    }
}
