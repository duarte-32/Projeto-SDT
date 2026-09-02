package pt.ipv.docs.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ipv.docs.model.ClusterState;
import pt.ipv.docs.service.ConsensusService;

import java.util.Map;

@RestController
@RequestMapping("/cluster")
public class ClusterController {

    private final ConsensusService consensusService;

    public ClusterController(ConsensusService consensusService) {
        this.consensusService = consensusService;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        ClusterState state = consensusService.state();

        return Map.of(
                "activeVersion", state.activeVersion(),
                "activeDocuments", state.activeDocuments(),
                "stagingOperationId", String.valueOf(state.stagingOperationId()),
                "stagingVersion", state.stagingVersion(),
                "stagingDocuments", state.stagingDocuments()
        );
    }
}