package com.example.kolla.controllers;

import com.example.kolla.dto.TranscriptionCallbackRequest;
import com.example.kolla.responses.ApiResponse;
import com.example.kolla.responses.TranscriptionSegmentResponse;
import com.example.kolla.services.TranscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TranscriptionControllerHeaderTest {

    @Mock
    private TranscriptionService transcriptionService;

    private TranscriptionController controller;

    @BeforeEach
    void setUp() {
        controller = new TranscriptionController(transcriptionService);
        ReflectionTestUtils.setField(controller, "expectedCallbackApiKey", "expected-key");
    }

    @Test
    void receiveCallback_rejectsWrongInternalApiKey() {
        TranscriptionCallbackRequest request = new TranscriptionCallbackRequest();
        request.setJobId("job-1");
        request.setText("Xin chào");
        request.setSegmentStartTime("2026-06-05T09:00:00+07:00");

        ResponseEntity<ApiResponse<TranscriptionSegmentResponse>> response =
                controller.receiveCallback("wrong-key", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("X-Internal-Api-Key");
        verifyNoInteractions(transcriptionService);
    }
}
