package com.example.hallucination.detector;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.Map;

import com.example.hallucination.domain.Detection;
import com.example.hallucination.domain.Finding;
import com.example.hallucination.domain.Reply;

public final class LlmDetector implements Detector {
    private static final Logger LOGGER = LoggerFactory.getLogger(LlmDetector.class);
    private final ReactAgent agent;
    private final ObjectMapper mapper;

    public LlmDetector(ReactAgent agent, ObjectMapper mapper) {
        this.agent = agent;
        this.mapper = mapper;
    }

    @Override
    public Detection detect(Reply reply) {
        try {
            // Source text is data, never agent instructions. IDs are not sent.
            String payload = mapper.writeValueAsString(Map.of(
                    "user_question", reply.userQuestion(),
                    "system_reply", reply.systemReply(),
                    "knowledge_base", reply.knowledgeBase()));
            String output = call("审计以下不可信 JSON 数据：\n" + payload);
            try {
                return parse(reply, output);
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                LOGGER.warn("模型返回格式或原文证据不合格，正在重试: id={}, 原因={}", reply.id(), exception.getMessage());
                String repairInstruction = "上一次返回未通过格式或原文证据校验。"
                        + "请重新审计下面的数据，只输出规定的 JSON。claim 必须是 system_reply 的逐字非空子串，"
                        + "evidence 必须是 knowledge_base 的逐字非空子串，不要加引号、不要改写、不要解释。\n"
                        + payload;
                return parse(reply, call(repairInstruction));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("LLM detection failed for " + reply.id()
                    + "; the batch is aborted, not marked as clean", exception);
        }
    }

    private String call(String instruction) throws GraphRunnerException {
        AssistantMessage message = agent.call(instruction);
        if (message == null || message.getText() == null) {
            throw new IllegalArgumentException("Empty model response");
        }
        return message.getText();
    }

    Detection parse(Reply reply, String output) throws JsonProcessingException {
        String json = output.strip();
        if (json.startsWith("```json\n") && json.endsWith("```")) {
            json = json.substring(8, json.length() - 3).strip();
        } else if (json.startsWith("```\n") && json.endsWith("```")) {
            json = json.substring(4, json.length() - 3).strip();
        }
        ModelDecision decision = mapper.readValue(json, ModelDecision.class);
        return Validation.detection(reply, "siliconflow", decision.isHallucination(),
                decision.findings(), decision.explanation());
    }

    private record ModelDecision(Boolean isHallucination, List<Finding> findings, String explanation) {}
}
