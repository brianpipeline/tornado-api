package com.brian.pipeline.tornado.records.pubsub;

public record MessageAttributes(
        String buildId,
        String status
) {}