package com.example.kolla.services;

import com.example.kolla.config.DigitalSignatureProperties;
import com.example.kolla.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfDigitalSignatureDisabledTest {

    @Test
    void signPdf_whenDisabled_throwsBadRequest() {
        DigitalSignatureProperties props = new DigitalSignatureProperties();
        props.setEnabled(false);

        PdfDigitalSignatureService service = new PdfDigitalSignatureService(props);

        assertThatThrownBy(() -> service.signPdf(new byte[] {1, 2, 3}, "Host"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Chữ ký số chưa được cấu hình");
    }
}
