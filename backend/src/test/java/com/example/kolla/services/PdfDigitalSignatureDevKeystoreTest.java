package com.example.kolla.services;

import com.example.kolla.config.DigitalSignatureProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses the repo dev keystore at {@code secrets/signing.p12} when present.
 */
class PdfDigitalSignatureDevKeystoreTest {

    private static boolean devKeystoreExists() {
        return Files.isRegularFile(Paths.get("..", "secrets", "signing.p12"));
    }

    @Test
    @EnabledIf("com.example.kolla.services.PdfDigitalSignatureDevKeystoreTest#devKeystoreExists")
    void signWithDevKeystore_producesVerifiablePdf() throws Exception {
        Path keystore = Paths.get("..", "secrets", "signing.p12").toAbsolutePath().normalize();
        Path outPdf = Paths.get("target", "dev-signed-minutes.pdf").toAbsolutePath();
        Files.createDirectories(outPdf.getParent());

        DigitalSignatureProperties props = new DigitalSignatureProperties();
        props.setEnabled(true);
        props.setKeystorePath(keystore.toString());
        props.setKeystorePassword("kolla-signing-dev");
        props.setKeystoreType("PKCS12");
        props.setKeyAlias("kolla-signing");

        PdfDigitalSignatureService service = new PdfDigitalSignatureService(props);
        service.loadKeystoreIfEnabled();

        byte[] draft = PdfDigitalSignatureServiceTest.minimalPdfBytes();
        byte[] signed = service.signPdf(draft, "Nguyen Quang Tung");
        Files.write(outPdf, signed);

        try (PDDocument doc = Loader.loadPDF(signed)) {
            assertThat(doc.getSignatureDictionaries()).hasSize(1);
            PDSignature sig = doc.getSignatureDictionaries().get(0);
            assertThat(sig.getContents()).isNotNull();
        }

        assertThat(Files.size(outPdf)).isGreaterThan(1000);
        System.out.println("Wrote signed PDF: " + outPdf);
    }
}
