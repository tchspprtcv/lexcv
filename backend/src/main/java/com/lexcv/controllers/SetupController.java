package com.lexcv.controllers;

import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.dtos.SetupInitializeResponse;
import com.lexcv.dtos.SetupStatusResponse;
import com.lexcv.services.SetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/setup")
@RequiredArgsConstructor
public class SetupController {
    private final SetupService setupService;

    @GetMapping("/status")
    public ResponseEntity<SetupStatusResponse> getStatus() {
        return ResponseEntity.ok(
                SetupStatusResponse.builder()
                        .initialized(setupService.isInitialized())
                        .build()
        );
    }

    @PostMapping("/initialize")
    public ResponseEntity<?> initialize(@RequestBody SetupInitializeRequest request) {
        if (setupService.isInitialized()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "O sistema já foi inicializado."));
        }

        try {
            setupService.initializeSystem(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(SetupInitializeResponse.builder()
                            .initialized(true)
                            .message("Ambiente configurado com sucesso.")
                            .build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
        }
    }
}
