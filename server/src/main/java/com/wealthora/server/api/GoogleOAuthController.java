package com.wealthora.server.api;

import com.wealthora.server.service.GoogleOAuthCallbackResult;
import com.wealthora.server.service.GoogleOAuthService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/google")
public final class GoogleOAuthController {

    private final GoogleOAuthService googleOAuthService;

    public GoogleOAuthController(GoogleOAuthService googleOAuthService) {
        this.googleOAuthService = googleOAuthService;
    }

    @GetMapping("/status")
    GoogleOAuthStatusResponse status() {
        return googleOAuthService.status();
    }

    @PostMapping("/start")
    GoogleOAuthStartResponse start(
            @Valid @RequestBody GoogleOAuthStartRequest request) {
        return googleOAuthService.start(request.deviceLabel());
    }

    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> callback(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error) {
        GoogleOAuthCallbackResult result = googleOAuthService.callback(
                state, code, error);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(page(result));
    }

    @PostMapping("/poll")
    GoogleOAuthPollResponse poll(
            @Valid @RequestBody GoogleOAuthPollRequest request) {
        return googleOAuthService.poll(
                request.flowIdentifier(), request.pollSecret());
    }

    private static String page(GoogleOAuthCallbackResult result) {
        return "<!doctype html><html lang=\"en\"><head>"
                + "<meta charset=\"utf-8\"><meta name=\"viewport\" "
                + "content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escape(result.title()) + "</title>"
                + "<style>body{font-family:system-ui,sans-serif;max-width:42rem;"
                + "margin:12vh auto;padding:2rem;color:#17212b}h1{font-size:1.6rem}"
                + "p{line-height:1.5}</style></head><body><h1>"
                + escape(result.title()) + "</h1><p>"
                + escape(result.message()) + "</p></body></html>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
