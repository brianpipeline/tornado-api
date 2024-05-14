package com.brian.pipeline.tornado.records.pubsub;

public record Message(
        MessageAttributes attributes,
        String data,
        String messageId,
        String publishTime
) {}