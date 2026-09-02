package pt.ipv.docs.model;

public record CommitConfirmMessage(
        String operationId,
        String peerId,
        long version,
        String status,
        String reason
) {
}