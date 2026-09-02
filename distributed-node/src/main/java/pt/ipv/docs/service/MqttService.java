package pt.ipv.docs.service;

import pt.ipv.docs.model.AbortMessage;
import pt.ipv.docs.model.CommitConfirmMessage;
import pt.ipv.docs.model.CommitMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pt.ipv.docs.model.AckMessage;
import pt.ipv.docs.model.PrepareMessage;
import pt.ipv.docs.model.HeartbeatMessage;

import java.nio.charset.StandardCharsets;

@Service
public class MqttService {

    private final String broker;
    private final String peerId;
    private final ObjectMapper objectMapper;
    private final ConsensusService consensusService;
    private final LeaderMonitor leaderMonitor;
    private MqttClient client;

    public MqttService(
            @Value("${mqtt.broker:tcp://127.0.0.1:1883}") String broker,
            @Value("${peer.id:leader}") String peerId,
            ObjectMapper objectMapper,
            ConsensusService consensusService,
            LeaderMonitor leaderMonitor
    ) {
        this.broker = broker;
        this.peerId = peerId;
        this.objectMapper = objectMapper;
        this.consensusService = consensusService;
        this.leaderMonitor = leaderMonitor;
    }

    @PostConstruct
    public void connect() throws Exception {
        client = new MqttClient(broker, peerId + "-" + System.nanoTime());
        client.connect();

        client.subscribe("cluster/test", (topic, message) -> {
            String text = new String(message.getPayload(), StandardCharsets.UTF_8);
            System.out.printf("[%s] MQTT recebido em %s: %s%n", peerId, topic, text);
        });

        client.subscribe("cluster/prepare", (topic, message) -> {
            PrepareMessage prepare = objectMapper.readValue(
                    message.getPayload(),
                    PrepareMessage.class
            );
            consensusService.handlePrepare(prepare);
        });

        client.subscribe("cluster/ack", (topic, message) -> {
            AckMessage ack = objectMapper.readValue(
                    message.getPayload(),
                    AckMessage.class
            );
            consensusService.handleAck(ack);
        });

        System.out.printf("[%s] Ligado ao MQTT em %s%n", peerId, broker);
        client.subscribe("cluster/commit", (topic, message) -> {
            CommitMessage commit = objectMapper.readValue(
                    message.getPayload(),
                    CommitMessage.class
            );
            consensusService.handleCommit(commit);
        });

        client.subscribe("cluster/commit-confirm", (topic, message) -> {
            CommitConfirmMessage confirmation = objectMapper.readValue(
                    message.getPayload(),
                    CommitConfirmMessage.class
            );
            consensusService.handleCommitConfirm(confirmation);
        });

        client.subscribe("cluster/abort", (topic, message) -> {
            AbortMessage abort = objectMapper.readValue(
                    message.getPayload(),
                    AbortMessage.class
            );
            consensusService.handleAbort(abort);
        });

        client.subscribe("cluster/heartbeat", (topic, message) -> {
            try {
                HeartbeatMessage heartbeat = objectMapper.readValue(
                        message.getPayload(),
                        HeartbeatMessage.class
                );

                if (!peerId.equals(heartbeat.leaderId())) {
                    leaderMonitor.messageReceived();

                    System.out.printf(
                            "[%s] HEARTBEAT recebido do líder %s | versão=%d%n",
                            peerId,
                            heartbeat.leaderId(),
                            heartbeat.activeVersion()
                    );
                }
            } catch (Exception exception) {
                System.err.printf(
                        "[%s] Erro ao processar heartbeat: %s%n",
                        peerId,
                        exception.getMessage()
                );
            }
        });
    }

    public void publishTest(String text) throws Exception {
        publish("cluster/test", text.getBytes(StandardCharsets.UTF_8));
    }

    public void publishPrepare(PrepareMessage message) throws Exception {
        publish("cluster/prepare", objectMapper.writeValueAsBytes(message));
    }

    public void publishAck(AckMessage message) throws Exception {
        publish("cluster/ack", objectMapper.writeValueAsBytes(message));
    }

    public void publishCommit(CommitMessage message) throws Exception {
        publish("cluster/commit", objectMapper.writeValueAsBytes(message));
    }

    public void publishCommitConfirm(CommitConfirmMessage message) throws Exception {
        publish("cluster/commit-confirm", objectMapper.writeValueAsBytes(message));
    }

    public void publishAbort(AbortMessage message) throws Exception {
        publish("cluster/abort", objectMapper.writeValueAsBytes(message));
    }

    public void publishHeartbeat(HeartbeatMessage heartbeat) throws Exception {
        MqttMessage message = new MqttMessage(
                objectMapper.writeValueAsBytes(heartbeat)
        );

        message.setQos(1);
        message.setRetained(false);

        client.publish("cluster/heartbeat", message);
    }

    private void publish(String topic, byte[] payload) throws Exception {
        MqttMessage message = new MqttMessage(payload);
        message.setQos(1);
        client.publish(topic, message);
    }
}