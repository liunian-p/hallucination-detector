package com.example.hallucination.detector;

import com.example.hallucination.config.DetectorConfiguration;
import com.example.hallucination.domain.Finding;
import com.example.hallucination.domain.Relation;
import com.example.hallucination.domain.Reply;
import com.example.hallucination.domain.Severity;
import com.example.hallucination.domain.Type;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DetectorTest {
    private ObjectMapper mapper;
    private RuleDetector detector;
    private List<Reply> replies;

    @BeforeEach
    void setup() throws Exception {
        mapper = new DetectorConfiguration().objectMapper();
        try (var stream = new ClassPathResource("mock-rules.json").getInputStream()) {
            detector = new RuleDetector(mapper.readValue(stream, new TypeReference<List<RuleDetector.Rule>>() {}));
        }
        replies = mapper.readValue(Path.of("data/task4_replies.json").toFile(), new TypeReference<>() {});
    }

    @Test
    void detectsKnownHallucinationAndKeepsEvidence() {
        var result = detector.detect(replies.get(0));
        assertThat(result.isHallucination()).isTrue();
        assertThat(result.findings()).isNotEmpty();
        for (Finding finding : result.findings()) {
            assertThat(replies.get(0).systemReply()).contains(finding.claim());
            assertThat(replies.get(0).knowledgeBase()).contains(finding.evidence());
        }
    }

    @Test
    void leavesCorrectReplyCleanAndFlagsOmission() {
        assertThat(detector.detect(replies.get(11)).isHallucination()).isFalse();
        assertThat(detector.detect(replies.get(19)).findings()).extracting(Finding::type).contains(Type.OMISSION);
    }

    @Test
    void usesContentRatherThanReplyId() {
        Reply reply = replies.get(0);
        Reply renamed = new Reply("new-id", reply.userQuestion(), reply.systemReply(), reply.knowledgeBase());
        assertThat(detector.detect(renamed).findings()).isEqualTo(detector.detect(reply).findings());
    }

    @Test
    void rejectsIncompleteAndInvalidFindings() {
        Reply reply = replies.get(0);
        Finding invalid = new Finding(Type.POLICY, Severity.HIGH, Relation.CONTRADICTED,
                "not in reply", reply.knowledgeBase(), "test");
        assertThatThrownBy(() -> Validation.detection(reply, "test", true, List.of(invalid), "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedModelOutput() {
        var llm = new LlmDetector(null, mapper);
        assertThatThrownBy(() -> llm.parse(replies.get(0), "not json")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> llm.parse(replies.get(0), "{\"is_hallucination\":true,\"findings\":[],\"explanation\":\"bad\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
