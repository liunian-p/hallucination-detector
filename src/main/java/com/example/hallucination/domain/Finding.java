package com.example.hallucination.domain;

public record Finding(Type type, Severity severity, Relation relation, String claim,
                      String evidence, String reason) {}
