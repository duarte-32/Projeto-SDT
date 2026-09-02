package pt.ipv.docs.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pt.ipv.docs.model.PrepareMessage;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class DocumentService {

    private final IpfsClient ipfsClient;
    private final EmbeddingClient embeddingClient;
    private final MqttService mqttService;
    private final ConsensusService consensusService;
    private final String peerId;
    private final String peerRole;

    public DocumentService(
            IpfsClient ipfsClient,
            EmbeddingClient embeddingClient,
            MqttService mqttService,
            ConsensusService consensusService,
            @Value("${peer.id:leader}") String peerId,
            @Value("${peer.role:leader}") String peerRole
    ) {
        this.ipfsClient = ipfsClient;
        this.embeddingClient = embeddingClient;
        this.mqttService = mqttService;
        this.consensusService = consensusService;
        this.peerId = peerId;
        this.peerRole = peerRole;
    }

    public synchronized PrepareMessage upload(byte[] fileContent, String filename) throws Exception {
        if (!"leader".equalsIgnoreCase(peerRole)) {
            throw new IllegalStateException("Apenas o líder aceita uploads.");
        }

        String cid = ipfsClient.addAndPin(fileContent, filename);
        String text = new String(fileContent, StandardCharsets.UTF_8);
        float[] embedding = embeddingClient.embed(text);

        long version = consensusService.state().activeVersion() + 1;

        PrepareMessage prepare = new PrepareMessage(
                UUID.randomUUID().toString(),
                peerId,
                version,
                cid,
                filename,
                embedding
        );

        // O líder cria o mesmo staging que os peers antes de publicar PREPARE.
        consensusService.stageLocally(prepare);
        mqttService.publishPrepare(prepare);

        System.out.printf(
                "[leader] PREPARE publicado | operação=%s | versão=%d | CID=%s%n",
                prepare.operationId(),
                prepare.version(),
                prepare.cid()
        );

        return prepare;
    }
}