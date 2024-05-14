package com.brian.pipeline.tornado.records.cloudbuild;

import java.util.Map;

public record Builds(Map<String, Map<String, Map<String, CloudBuildStatus>>> builds,
                     int version) {
}
