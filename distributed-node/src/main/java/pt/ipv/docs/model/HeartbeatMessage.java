package pt.ipv.docs.model;

public record HeartbeatMessage(
        String leaderId,
        long activeVersion,
        long timestampEpochMs
) {
}