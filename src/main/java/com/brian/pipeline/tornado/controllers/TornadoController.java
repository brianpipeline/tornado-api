package com.brian.pipeline.tornado.controllers;

import com.brian.pipeline.tornado.records.cloudbuild.Builds;
import com.brian.pipeline.tornado.records.cloudbuild.CloudBuildJson;
import com.brian.pipeline.tornado.records.cloudbuild.CloudBuildStatus;
import com.brian.pipeline.tornado.records.pubsub.PubSubMessage;
import com.google.gson.Gson;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class TornadoController {
    public static final String PIPELINE_TO_CHECK = "opinionated-pipeline";
    private final Gson gson;
    private final Map<String, Map<String, Map<String, CloudBuildStatus>>> repoNameToBranchToBuildIdToStatus;
    private int version;
    private final List<CountDownLatch> work;
    private final ScheduledExecutorService executor;

    private static final Pattern WAIT_REGEX = Pattern.compile("\\bwait=(\\d+)");




    public TornadoController() {
        this.gson = new Gson();
        this.version = 0;
        this.repoNameToBranchToBuildIdToStatus = new ConcurrentHashMap<>();
        this.work = new CopyOnWriteArrayList<>();
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }

    @PostMapping("/pubsub")
    public synchronized ResponseEntity<?> receiveCloudBuildMessage(@RequestBody PubSubMessage pubSubMessage) {
        String status = pubSubMessage.message().attributes().status();
        String buildId = pubSubMessage.message().attributes().buildId();
        String startTime = pubSubMessage.message().publishTime();
        byte[] decodedBytes = Base64.decodeBase64(pubSubMessage.message().data());
        String cloudBuildJsonString = new String(decodedBytes, StandardCharsets.UTF_8);
        CloudBuildJson cloudBuildJson = gson.fromJson(cloudBuildJsonString, CloudBuildJson.class);

        if (!cloudBuildJson.substitutions().triggerName().equals(PIPELINE_TO_CHECK)) {
            return ResponseEntity.badRequest().build();
        }
        String branchName = cloudBuildJson.substitutions().gitRef().split("/", 3)[2];

        CloudBuildStatus cloudBuildStatus = new CloudBuildStatus(status, branchName, startTime, buildId, cloudBuildJson.substitutions().gitRepositoryName());
        if (!repoNameToBranchToBuildIdToStatus.containsKey(cloudBuildStatus.repoName())) {
            repoNameToBranchToBuildIdToStatus.put(cloudBuildStatus.repoName(), new ConcurrentHashMap<>());
            repoNameToBranchToBuildIdToStatus.get(cloudBuildStatus.repoName()).put(branchName, new ConcurrentHashMap<>());
            repoNameToBranchToBuildIdToStatus.get(cloudBuildStatus.repoName()).get(branchName).put(cloudBuildStatus.buildId(), cloudBuildStatus);
        } else {
            repoNameToBranchToBuildIdToStatus.get(cloudBuildStatus.repoName()).get(branchName).put(cloudBuildStatus.buildId(), cloudBuildStatus);
        }
        updated();

        return ResponseEntity.ok().build();
    }

    @GetMapping("/pollAllBuilds")
    public ResponseEntity<?> pollAllBuilds(@RequestHeader(value = "If-None-Match", required = false) String tag, @RequestHeader(value = "Prefer", required = false) String wait) throws InterruptedException {
        Integer waitTime = null;
        if (wait != null) {
            Matcher matcher = WAIT_REGEX.matcher(wait);
            if (matcher.matches()) {
                waitTime = Integer.parseInt(matcher.group(1));
            }
        }
        if (tag == null) {
            return getBuildsMap();
        } else {
            int clientVersion = Integer.parseInt(tag.replace("\"", ""));
            int currentVersion;
            synchronized (this) {
                currentVersion = version;
            }
            if (clientVersion != currentVersion) {
                return getBuildsMap();
            }
        }

        if (waitTime == null) {
            return new ResponseEntity<>(null, null, HttpStatus.NOT_MODIFIED);
        }

        return waitForChange(waitTime);
    }

    private void updated() {
        synchronized (this) {
            version++;
        }
        work.forEach(CountDownLatch::countDown);
        work.clear();
    }

    private ResponseEntity<Builds> getBuildsMap() {
        MultiValueMap<String, String> headers = new HttpHeaders();
        synchronized (this) {
            headers.add("Content-Type", "application/json");
            headers.add("Cache-Control", "no-store");
            return new ResponseEntity<>(new Builds(repoNameToBranchToBuildIdToStatus, version), headers, HttpStatus.OK);
        }

    }

    private ResponseEntity<Builds> waitForChange(Integer waitTime) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        work.add(latch);
        AtomicBoolean timedOut = new AtomicBoolean(false);
        executor.schedule(() -> {
            work.remove(latch);
            timedOut.set(true);
            latch.countDown();
        }, waitTime, TimeUnit.SECONDS);
        latch.await();
        if (timedOut.get()) {
            return new ResponseEntity<>(null, null, HttpStatus.NOT_MODIFIED);
        }
        return getBuildsMap();
    }
}

