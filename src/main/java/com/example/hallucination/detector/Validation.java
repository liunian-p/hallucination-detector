package com.example.hallucination.detector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.example.hallucination.domain.Detection;
import com.example.hallucination.domain.Finding;
import com.example.hallucination.domain.Reply;
import com.example.hallucination.domain.Severity;

public final class Validation {
    private Validation() {}

    public static void replies(List<Reply> replies) {
        uniqueIds(replies, Reply::id);
        for (Reply reply : replies) {
            require(text(reply.userQuestion()) && text(reply.systemReply()) && text(reply.knowledgeBase()),
                    "Input fields must be nonblank: " + reply.id());
        }
    }

    public static <T> Set<String> uniqueIds(List<T> items, Function<T, String> getId) {
        require(items != null && !items.isEmpty(), "Input must be a nonempty array");
        Set<String> ids = new HashSet<>();
        for (T item : items) {
            require(item != null, "Null item");
            String id = getId.apply(item);
            require(text(id) && ids.add(id), "Missing or duplicate ID: " + id);
        }
        return ids;
    }

    public static Detection detection(Reply reply, String mode, Boolean isHallucination,
                                      List<Finding> findings, String explanation) {
        require(isHallucination != null && findings != null && text(explanation), "Incomplete decision");
        require(isHallucination == !findings.isEmpty(),
                "Decision flag must agree with findings");
        Severity severity = Severity.NONE;
        for (Finding finding : findings) {
            require(finding != null && finding.type() != null && finding.relation() != null
                    && finding.severity() != null && finding.severity() != Severity.NONE
                    && text(finding.claim()) && text(finding.evidence()) && text(finding.reason()),
                    "Incomplete finding");
            require(reply.systemReply().contains(finding.claim()), "Claim is not an exact reply quote");
            require(reply.knowledgeBase().contains(finding.evidence()), "Evidence is not an exact KB quote");
            if (finding.severity().ordinal() > severity.ordinal()) {
                severity = finding.severity();
            }
        }
        return new Detection(reply.id(), mode, isHallucination, severity,
                List.copyOf(findings), explanation);
    }

    public static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
