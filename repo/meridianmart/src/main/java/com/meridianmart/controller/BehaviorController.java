package com.meridianmart.controller;

import com.meridianmart.dto.ApiResponse;
import com.meridianmart.dto.BehaviorEventRequest;
import com.meridianmart.model.User;
import com.meridianmart.service.BehaviorEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/behavior")
@RequiredArgsConstructor
public class BehaviorController {

    private final BehaviorEventService behaviorEventService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> recordBehavior(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody BehaviorEventRequest request) {
        behaviorEventService.recordEvent(user, request);
        return ResponseEntity.ok(ApiResponse.success("Event recorded", null));
    }
}
