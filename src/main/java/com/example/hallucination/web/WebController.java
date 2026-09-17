package com.example.hallucination.web;

import com.example.hallucination.detector.Detector;
import com.example.hallucination.detector.Validation;
import com.example.hallucination.domain.Detection;
import com.example.hallucination.domain.Reply;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api")
public class WebController {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebController.class);
    private final Detector detector;

    public WebController(Detector detector) {
        this.detector = detector;
    }

    @PostMapping(value = "/detect", consumes = "application/json", produces = "application/json")
    public List<Detection> detect(@RequestBody List<Reply> replies) {
        LOGGER.info("收到检测请求: {} 条回复", replies == null ? 0 : replies.size());
        Validation.replies(replies);
        Validation.require(replies.size() <= 100, "At most 100 replies per batch");
        for (Reply reply : replies) {
            Validation.require(reply.id().length() <= 200 && reply.userQuestion().length() <= 20000
                    && reply.systemReply().length() <= 20000 && reply.knowledgeBase().length() <= 20000,
                    "Input field is too long");
        }
        List<Detection> detections = replies.stream().map(detector::detect).toList();
        LOGGER.info("检测完成: {} 条回复, {} 条疑似幻觉", detections.size(),
                detections.stream().filter(Detection::isHallucination).count());
        return detections;
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidInput(Exception exception) {
        return Map.of("error", "文件格式或字段无效，请检查 JSON 数组、字段类型及唯一且对应的 ID。");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> modelFailed(IllegalStateException exception) {
        LOGGER.error("模型检测失败", exception);
        return Map.of("error", "检测未完成，请检查服务端模型配置、额度和网络。失败不会被标记为正常。");
    }
}
