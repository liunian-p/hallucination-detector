package com.example.hallucination.detector;

import com.example.hallucination.domain.Detection;
import com.example.hallucination.domain.Reply;

public interface Detector {
    Detection detect(Reply reply);
}
