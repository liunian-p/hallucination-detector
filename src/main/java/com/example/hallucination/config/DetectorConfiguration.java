package com.example.hallucination.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.example.hallucination.detector.Detector;
import com.example.hallucination.detector.LlmDetector;
import com.example.hallucination.detector.RuleDetector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class DetectorConfiguration {
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "detector.mode", havingValue = "mock")
    Detector mockDetector(ObjectMapper mapper) throws Exception {
        try (var stream = new ClassPathResource("mock-rules.json").getInputStream()) {
            return new RuleDetector(mapper.readValue(stream, new TypeReference<List<RuleDetector.Rule>>() {}));
        }
    }

    @Bean
    @ConditionalOnProperty(name = "detector.mode", havingValue = "siliconflow")
    Detector llmDetector(ChatModel model, ObjectMapper mapper) throws Exception {
        String prompt = new ClassPathResource("judge-prompt.txt").getContentAsString(StandardCharsets.UTF_8);
        ReactAgent agent = ReactAgent.builder()
                .name("hallucination_judge")
                .model(model)
                .systemPrompt(prompt)
                .build();
        return new LlmDetector(agent, mapper);
    }

}
