package pt.ipv.docs.model;

public record PrepareMessage(
        String operationId,
        String leaderId,
        long version,
        String cid,
        String filename,
        float[] embedding
) {
}