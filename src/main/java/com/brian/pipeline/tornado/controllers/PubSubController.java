package com.brian.pipeline.tornado.controllers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PubSubController {

    @PostMapping("/pubsub")
    public ResponseEntity<?> receiveMessage(@RequestBody String message) {
        // Process the received message
        System.out.println("Received message payload: " + message);
        // Acknowledge the mess®age (if required)
        return ResponseEntity.ok().build();
    }
}

