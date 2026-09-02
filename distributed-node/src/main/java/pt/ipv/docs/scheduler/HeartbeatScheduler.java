package pt.ipv.docs.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.ipv.docs.model.HeartbeatMessage;
import pt.ipv.docs.service.ConsensusService;
import pt.ipv.docs.service.MqttService;

@Component
public class HeartbeatScheduler {

    private final MqttService mqttService;
    private final ConsensusService consensusService;
    private final String peerId;
    private final String peerRole;

    public HeartbeatScheduler(
            MqttService mqttService,
            ConsensusService consensusService,
            @Value("${peer.id:leader}") String peerId,
            @Value("${peer.role:peer}") String peerRole
    ) {
        this.mqttService = mqttService;
        this.consensusService = consensusService;
        this.peerId = peerId;
        this.peerRole = peerRole;
    }

    @Scheduled(fixedRateString = "${cluster.heartbeat-interval-ms:5000}")
    public void sendHeartbeat() {
        if (!"leader".equalsIgnoreCase(peerRole)) {
            return;
        }

        try {
            mqttService.publishHeartbeat(new HeartbeatMessage(
                    peerId,
                    consensusService.state().activeVersion(),
                    System.currentTimeMillis()
            ));
        } catch (Exception exception) {
            System.err.println("[leader] Falha ao enviar heartbeat: " + exception.getMessage());
        }
    }
}