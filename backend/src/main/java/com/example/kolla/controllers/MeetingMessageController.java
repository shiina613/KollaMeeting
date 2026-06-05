package com.example.kolla.controllers;

import com.example.kolla.dto.CreateMeetingMessageRequest;
import com.example.kolla.models.User;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.MeetingMessageResponse;
import com.example.kolla.services.MeetingMessageService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/meetings/{meetingId}/messages")
@RequiredArgsConstructor
@Tag(name = "Meeting Messages", description = "Persistent meeting discussion messages")
@SecurityRequirement(name = "bearerAuth")
public class MeetingMessageController {

    private final MeetingMessageService meetingMessageService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MeetingMessageResponse>>> listMessages(
            @PathVariable Long meetingId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                meetingMessageService.listMessages(meetingId, currentUser)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MeetingMessageResponse>> createMessage(
            @PathVariable Long meetingId,
            @Valid @RequestBody CreateMeetingMessageRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Meeting message created successfully",
                        meetingMessageService.createMessage(meetingId, request, currentUser)));
    }
}
