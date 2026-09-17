package com.example.hallucination.domain;

import java.util.List;

public record Detection(String id, String mode, Boolean isHallucination, Severity severity,
                        List<Finding> findings, String explanation) {}
