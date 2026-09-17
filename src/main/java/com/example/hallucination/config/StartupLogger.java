package com.example.hallucination.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.env.Environment;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupLogger.class);
    private final Environment environment;

    public StartupLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logReady() {
        LOGGER.info("客服回复幻觉检测已启动: http://127.0.0.1:8080, detector.mode={}",
                environment.getRequiredProperty("detector.mode"));
    }
}
