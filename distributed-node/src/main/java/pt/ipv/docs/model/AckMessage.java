package pt.ipv.docs.model;

public record AckMessage(
        String operationId,
        String peerId,
        long version,
        String vectorHash,
        String status,
        String reason
) {
}