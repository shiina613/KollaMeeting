package com.example.kolla.services;

import com.example.kolla.config.DigitalSignatureProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDigitalSignatureServiceTest {

    private static PdfDigitalSignatureService service;

    @BeforeAll
    static void createKeystoreAndService() throws Exception {
        Path tempDir = Files.createTempDirectory("kolla-sign-test");
        Path keystorePath = tempDir.resolve("test-signing.p12");
        String password = "test-pass";
        ProcessBuilder pb = new ProcessBuilder(
                "keytool", "-genkeypair",
                "-alias", "test",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-sigalg", "SHA256withRSA",
                "-validity", "365",
                "-storetype", "PKCS12",
                "-keystore", keystorePath.toString(),
                "-storepass", password,
                "-keypass", password,
                "-dname", "CN=Test Signer, O=KollaMeeting, C=VN"
        );
        Process p = pb.start();
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("keytool failed with exit " + code);
        }

        DigitalSignatureProperties props = new DigitalSignatureProperties();
        props.setEnabled(true);
        props.setKeystorePath(keystorePath.toString());
        props.setKeystorePassword(password);
        props.setKeystoreType("PKCS12");
        props.setKeyAlias("test");

        service = new PdfDigitalSignatureService(props);
        service.loadKeystoreIfEnabled();
    }

    static byte[] minimalPdfBytes() throws IOException {
        return minimalPdfBytesInternal();
    }

    @Test
    void signPdf_embedsSignatureDictionary() throws IOException {
        byte[] draft = minimalPdfBytesInternal();
        byte[] signed = service.signPdf(draft, "Nguyen Van A");

        assertThat(signed.length).isGreaterThan(draft.length);

        try (PDDocument doc = Loader.loadPDF(signed)) {
            List<PDSignature> signatures = doc.getSignatureDictionaries();
            assertThat(signatures).isNotEmpty();
            PDSignature sig = signatures.get(0);
            assertThat(sig.getFilter()).isEqualTo("Adobe.PPKLite");
            assertThat(sig.getSubFilter()).isEqualTo("ETSI.CAdES.detached");
            assertThat(sig.getName()).contains("Nguyen Van A");
            assertThat(sig.getContents()).isNotNull();
            assertThat(sig.getContents().length).isGreaterThan(100);
        }
    }

    private static byte[] minimalPdfBytesInternal() throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Draft minutes");
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
