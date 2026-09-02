package pt.ipv.docs.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.ipv.docs.service.LeaderMonitor;

@Component
public class LeaderFailureDetector {

    private final LeaderMonitor leaderMonitor;
    private final String peerId;
    private final String peerRole;
    private final long timeoutMs;

    public LeaderFailureDetector(
            LeaderMonitor leaderMonitor,
            @Value("${peer.id:peer}") String peerId,
            @Value("${peer.role:peer}") String peerRole,
            @Value("${cluster.leader-timeout-ms:15000}") long timeoutMs
    ) {
        this.leaderMonitor = leaderMonitor;
        this.peerId = peerId;
        this.peerRole = peerRole;
        this.timeoutMs = timeoutMs;
    }

    @Scheduled(fixedRate = 1000)
    public void checkLeader() {
        if ("leader".equalsIgnoreCase(peerRole)) {
            return;
        }

        long lastMessage = leaderMonitor.lastLeaderMessage();

        // Ainda não foi recebido nenhum heartbeat.
        if (lastMessage == 0) {
            return;
        }

        long elapsed = System.currentTimeMillis() - lastMessage;

        if (elapsed > timeoutMs && leaderMonitor.detectFailureOnce()) {
            System.err.printf(
                    "[%s] FALHA DO LÍDER DETETADA após %d ms%n",
                    peerId,
                    elapsed
            );
        }
    }
}