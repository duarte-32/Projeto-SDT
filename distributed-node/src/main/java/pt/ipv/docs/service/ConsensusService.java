package pt.ipv.docs.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import pt.ipv.docs.model.AbortMessage;
import pt.ipv.docs.model.AckMessage;
import pt.ipv.docs.model.ClusterState;
import pt.ipv.docs.model.CommitConfirmMessage;
import pt.ipv.docs.model.CommitMessage;
import pt.ipv.docs.model.DocumentEntry;
import pt.ipv.docs.model.PrepareMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConsensusService {

    private final ClusterState state = new ClusterState();
    private final HashService hashService;
    private final MqttService mqttService;
    private final String peerId;
    private final String peerRole;
    private final FaissClient faissClient;
    private final int quorumRemote;

    // Apenas o líder usa esta estrutura para mostrar/guardar ACKs.
    // operationId -> (peerId -> ACK)
    private final Map<String, Map<String, AckMessage>> acknowledgements =
            new ConcurrentHashMap<>();

    public ConsensusService(
            HashService hashService,
            @Lazy MqttService mqttService,
            FaissClient faissClient,
            @Value("${peer.id:leader}") String peerId,
            @Value("${peer.role:leader}") String peerRole,
            @Value("${cluster.quorum-remote:2}") int quorumRemote
    ) {
        this.hashService = hashService;
        this.mqttService = mqttService;
        this.faissClient = faissClient;
        this.peerId = peerId;
        this.peerRole = peerRole;
        this.quorumRemote = quorumRemote;
    }

    public ClusterState state() {
        return state;
    }

    public void stageLocally(PrepareMessage message) {
        ClusterState.PrepareResult result = state.prepare(message);
        if (!result.accepted()) {
            throw new IllegalStateException(
                    "Líder não conseguiu criar staging: " + result.reason()
            );
        }
    }

    public void handlePrepare(PrepareMessage message) {
        // O líder criou staging antes de publicar; não deve voltar a processar a própria mensagem.
        if ("leader".equalsIgnoreCase(peerRole) && peerId.equals(message.leaderId())) {
            return;
        }

        ClusterState.PrepareResult result = state.prepare(message);

        try {
            if (!result.accepted()) {
                mqttService.publishAck(new AckMessage(
                        message.operationId(),
                        peerId,
                        message.version(),
                        null,
                        result.status(),
                        result.reason()
                ));

                System.out.printf(
                        "[%s] PREPARE %s: %s%n",
                        peerId,
                        result.status(),
                        result.reason()
                );
                return;
            }

            String vectorHash = hashService.cidVectorHash(result.documents());

            mqttService.publishAck(new AckMessage(
                    message.operationId(),
                    peerId,
                    message.version(),
                    vectorHash,
                    "OK",
                    null
            ));

            System.out.printf(
                    "[%s] PREPARE aceite | operação=%s | versão=%d | hash=%s%n",
                    peerId,
                    message.operationId(),
                    message.version(),
                    vectorHash
            );
        } catch (Exception exception) {
            System.err.printf(
                    "[%s] Falha ao processar PREPARE: %s%n",
                    peerId,
                    exception.getMessage()
            );
        }
    }

    public synchronized void handleAck(AckMessage ack) {
        if (!"leader".equalsIgnoreCase(peerRole)) {
            return;
        }

        acknowledgements
                .computeIfAbsent(ack.operationId(), ignored -> new ConcurrentHashMap<>())
                .put(ack.peerId(), ack);

        System.out.printf(
                "[leader] ACK recebido | peer=%s | operação=%s | estado=%s | hash=%s%n",
                ack.peerId(),
                ack.operationId(),
                ack.status(),
                ack.vectorHash()
        );

        if (!"OK".equals(ack.status())) {
            return;
        }

        if (!ack.operationId().equals(state.stagingOperationId())) {
            return;
        }

        String expectedHash = hashService.cidVectorHash(state.stagingDocuments());

        long validAcks = acknowledgements.get(ack.operationId()).values().stream()
                .filter(item -> "OK".equals(item.status()))
                .filter(item -> expectedHash.equals(item.vectorHash()))
                .count();

        if (validAcks < quorumRemote) {
            return;
        }

        try {
            CommitMessage commit = new CommitMessage(
                    ack.operationId(),
                    state.stagingVersion(),
                    expectedHash
            );

            // Aplica antes localmente. O líder ignora o PREPARE próprio,
            // mas recebe o COMMIT MQTT como os outros nós.
            applyCommit(commit);
            mqttService.publishCommit(commit);

            System.out.printf(
                    "[leader] QUORUM atingido (%d ACKs); COMMIT publicado para versão %d%n",
                    validAcks,
                    commit.version()
            );
        } catch (Exception exception) {
            System.err.println("[leader] Erro no COMMIT: " + exception.getMessage());
        }
    }

    public Map<String, Map<String, AckMessage>> acknowledgements() {
        return acknowledgements;
    }

    public void handleCommit(CommitMessage commit) {
        // O líder já aplicou localmente antes de publicar.
        if ("leader".equalsIgnoreCase(peerRole)) {
            return;
        }

        try {
            applyCommit(commit);

            mqttService.publishCommitConfirm(new CommitConfirmMessage(
                    commit.operationId(),
                    peerId,
                    commit.version(),
                    "OK",
                    null
            ));

            System.out.printf(
                    "[%s] COMMIT aplicado | versão=%d%n",
                    peerId,
                    commit.version()
            );
        } catch (Exception exception) {
            try {
                mqttService.publishCommitConfirm(new CommitConfirmMessage(
                        commit.operationId(),
                        peerId,
                        commit.version(),
                        "ERROR",
                        exception.getMessage()
                ));
            } catch (Exception ignored) {
            }

            System.err.printf(
                    "[%s] COMMIT rejeitado: %s%n",
                    peerId,
                    exception.getMessage()
            );
        }
    }

    private synchronized void applyCommit(CommitMessage commit) {
        List<DocumentEntry> before = state.activeDocuments();

        List<DocumentEntry> after = state.commit(
                commit.operationId(),
                commit.version(),
                commit.vectorHash(),
                hashService
        );

        after.stream()
                .filter(document -> before.stream().noneMatch(
                        old -> old.cid().equals(document.cid())
                ))
                .forEach(faissClient::add);
    }

    public void handleCommitConfirm(CommitConfirmMessage confirmation) {
        if (!"leader".equalsIgnoreCase(peerRole)) {
            return;
        }

        System.out.printf(
                "[leader] COMMIT_CONFIRM | peer=%s | versão=%d | estado=%s | razão=%s%n",
                confirmation.peerId(),
                confirmation.version(),
                confirmation.status(),
                confirmation.reason()
        );
    }

    public void handleAbort(AbortMessage abort) {
        state.clearStaging(abort.operationId());
        System.out.printf(
                "[%s] ABORT recebido | operação=%s | razão=%s%n",
                peerId,
                abort.operationId(),
                abort.reason()
        );
    }
}