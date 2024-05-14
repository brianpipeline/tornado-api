package com.brian.pipeline.tornado.records.cloudbuild;

import com.google.gson.annotations.SerializedName;

public record Substitutions(
        @SerializedName("TRIGGER_NAME")
        String triggerName,
        @SerializedName("_GIT_HEAD_SHA")
        String gitHeadSha,
        @SerializedName("_GIT_REF")
        String gitRef,
        @SerializedName("_GIT_REPOSITORY_NAME")
        String gitRepositoryName
) {
}
