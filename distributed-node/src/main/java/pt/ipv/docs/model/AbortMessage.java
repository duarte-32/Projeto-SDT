package pt.ipv.docs.model;

public record AbortMessage(
        String operationId,
        long version,
        String reason
) {
}