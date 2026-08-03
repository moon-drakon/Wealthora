package com.wealthora.server.api;

import com.wealthora.server.service.SpeechService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/speech")
public final class SpeechController {

    private final SpeechService speechService;

    public SpeechController(SpeechService speechService) {
        this.speechService = speechService;
    }

    @GetMapping("/status")
    SpeechStatusResponse status() {
        return speechService.status();
    }

    @PostMapping("/recognize")
    SpeechRecognitionResponse recognize(
            @Valid @RequestBody SpeechRecognitionRequest request) {
        return speechService.recognize(request);
    }
}
