package com.example.kolla.responses;

import com.example.kolla.enums.MinutesStatus;
import com.example.kolla.models.Meeting;
import com.example.kolla.models.Minutes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinutesResponseDocxTest {

    @Test
    void from_exposesDocxPathsAndAvailabilityFlags() {
        Meeting meeting = new Meeting();
        meeting.setId(123L);

        Minutes minutes = Minutes.builder()
                .id(77L)
                .meeting(meeting)
                .status(MinutesStatus.SECRETARY_CONFIRMED)
                .draftPdfPath("minutes/123/draft_77.pdf")
                .draftDocxPath("minutes/123/draft_77.docx")
                .secretaryPdfPath("minutes/123/secretary_77.pdf")
                .secretaryDocxPath("minutes/123/secretary_77.docx")
                .build();

        MinutesResponse response = MinutesResponse.from(minutes);

        assertThat(response.getDraftDocxPath()).isEqualTo("minutes/123/draft_77.docx");
        assertThat(response.getSecretaryDocxPath()).isEqualTo("minutes/123/secretary_77.docx");
        assertThat(response.isDraftDocxAvailable()).isTrue();
        assertThat(response.isSecretaryDocxAvailable()).isTrue();
    }
}
