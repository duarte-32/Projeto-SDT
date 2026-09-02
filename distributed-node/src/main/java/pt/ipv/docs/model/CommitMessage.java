package pt.ipv.docs.model;

public record CommitMessage(
        String operationId,
        long version,
        String vectorHash
) {
}