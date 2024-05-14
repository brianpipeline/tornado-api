package com.brian.pipeline.tornado.records.cloudbuild;

public record CloudBuildStatus(
        String status,
        String branch,
        String startTime,
        String buildId,
        String repoName
) {
}
