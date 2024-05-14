package com.brian.pipeline.tornado.records.pubsub;

public record PubSubMessage(
        Message message,
        String subscription
) {}
