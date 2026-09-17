package com.example.hallucination.detector;

import java.util.ArrayList;
import java.util.List;

import com.example.hallucination.domain.Detection;
import com.example.hallucination.domain.Finding;
import com.example.hallucination.domain.Reply;
import com.example.hallucination.domain.Relation;
import com.example.hallucination.domain.Severity;
import com.example.hallucination.domain.Type;

public final class RuleDetector implements Detector {
    private final List<Rule> rules;

    public RuleDetector(List<Rule> rules) {
        Validation.uniqueIds(rules, Rule::id);
        for (Rule rule : rules) {
            Validation.require(rule.kbAny() != null && !rule.kbAny().isEmpty()
                    && rule.replyAny() != null && !rule.replyAny().isEmpty()
                    && rule.kbAny().stream().allMatch(Validation::text)
                    && rule.replyAny().stream().allMatch(Validation::text)
                    && rule.type() != null && rule.severity() != null
                    && rule.severity() != Severity.NONE && rule.relation() != null
                    && Validation.text(rule.reason()), "Invalid rule: " + rule.id());
        }
        this.rules = List.copyOf(rules);
    }

    @Override
    public Detection detect(Reply reply) {
        List<Finding> findings = new ArrayList<>();
        for (Rule rule : rules) {
            boolean kbMatches = rule.kbAny().stream().anyMatch(reply.knowledgeBase()::contains);
            boolean replyMatches = rule.replyAny().stream().anyMatch(reply.systemReply()::contains);
            if (kbMatches && replyMatches) {
                findings.add(new Finding(rule.type(), rule.severity(), rule.relation(),
                        reply.systemReply(), reply.knowledgeBase(), rule.id() + ": " + rule.reason()));
            }
        }
        return Validation.detection(reply, "mock-rules", !findings.isEmpty(), findings,
                findings.isEmpty() ? "No baseline rule matched; this is not proof of factual accuracy."
                        : "Matched content rules; each finding includes original reply and KB evidence.");
    }

    public record Rule(String id, List<String> kbAny, List<String> replyAny, Type type,
                       Severity severity, Relation relation, String reason) {}
}
