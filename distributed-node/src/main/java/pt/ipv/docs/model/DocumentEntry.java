package pt.ipv.docs.model;

public record DocumentEntry(
        String cid,
        String filename,
        float[] embedding
) {
}
