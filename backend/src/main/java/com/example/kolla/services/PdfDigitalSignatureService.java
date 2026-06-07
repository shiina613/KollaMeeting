package com.example.kolla.services;

import com.example.kolla.config.DigitalSignatureProperties;
import com.example.kolla.exceptions.BadRequestException;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.signatures.BouncyCastleDigest;
import com.itextpdf.signatures.DigestAlgorithms;
import com.itextpdf.signatures.IExternalDigest;
import com.itextpdf.signatures.IExternalSignature;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.PrivateKeySignature;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Signs meeting-minutes PDFs with a PAdES/CAdES detached signature (ETSI.CAdES.detached).
 *
 * <p>Requires a CA-issued or org-issued certificate in PKCS#12 or JKS keystore.
 * This is true digital signing (private key + certificate chain), not the legacy SHA-256 text stamp.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfDigitalSignatureService {

    private static final DateTimeFormatter WATERMARK_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final float WATERMARK_MARGIN_RIGHT = 48f;
    private static final float WATERMARK_MARGIN_TOP = 42f;
    private static final float WATERMARK_NAME_FONT_SIZE = 11f;
    private static final float WATERMARK_TIME_FONT_SIZE = 10f;
    private static final float WATERMARK_LINE_GAP = 15f;
    private static final float WATERMARK_OPACITY = 0.18f;

    private final DigitalSignatureProperties properties;

    private volatile SigningMaterial signingMaterial;

    @PostConstruct
    void loadKeystoreIfEnabled() {
        if (!properties.isEnabled()) {
            log.info("PDF digital signature is disabled (digital-signature.enabled=false)");
            return;
        }
        try {
            signingMaterial = loadSigningMaterial();
            log.info("PDF digital signature ready: subject={}, alias={}",
                    signingMaterial.signerSubject(), signingMaterial.keyAlias());
        } catch (CertificateException e) {
            throw new IllegalStateException(
                    "Invalid certificate in digital-signature keystore at " + properties.getKeystorePath(), e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load digital-signature keystore at " + properties.getKeystorePath(), e);
        }
    }

    public boolean isEnabled() {
        return properties.isEnabled() && signingMaterial != null;
    }

    /**
     * Signs {@code pdfBytes} and returns a new PDF with an embedded PAdES/CAdES signature.
     *
     * @param pdfBytes      draft PDF bytes
     * @param signerDisplay name shown in the signature panel (typically Host full name)
     */
    public byte[] signPdf(byte[] pdfBytes, String signerDisplay) throws IOException {
        if (!isEnabled()) {
            throw new BadRequestException(
                    "Chữ ký số chưa được cấu hình. Đặt digital-signature.enabled=true và cung cấp "
                            + "keystore PKCS#12/JKS (xem docs/digital-signature.md).");
        }

        SigningMaterial material = signingMaterial;

        ensureBouncyCastleProvider();
        String signerName = signerDisplay != null && !signerDisplay.isBlank()
                ? signerDisplay
                : material.signerSubject();
        byte[] watermarkedPdf = addSignatureWatermark(pdfBytes, signerName);

        try (ByteArrayInputStream pdfIn = new ByteArrayInputStream(watermarkedPdf);
             ByteArrayOutputStream signedOut = new ByteArrayOutputStream();
             PdfReader reader = new PdfReader(pdfIn)) {

            PdfSigner signer = new PdfSigner(
                    reader,
                    signedOut,
                    new StampingProperties().useAppendMode());
            signer.setFieldName("KollaMeetingSignature");
            signer.setSignatureEvent(signatureDictionary ->
                    signatureDictionary.put(PdfName.Name, new PdfString(signerName)));
            signer.getSignatureAppearance()
                    .setReason(properties.getReason())
                    .setLocation(properties.getLocation())
                    .setSignatureCreator("KollaMeeting")
                    .setLayer2Text("");

            try {
                IExternalDigest digest = new BouncyCastleDigest();
                IExternalSignature signature = new PrivateKeySignature(
                        material.privateKey(),
                        DigestAlgorithms.SHA256,
                        BouncyCastleProvider.PROVIDER_NAME);

                signer.signDetached(
                        digest,
                        signature,
                        material.certificateChain().toArray(Certificate[]::new),
                        null,
                        null,
                        null,
                        0,
                        PdfSigner.CryptoStandard.CADES);
            } catch (GeneralSecurityException e) {
                throw new IOException("PAdES PDF signing failed", e);
            }
            return signedOut.toByteArray();
        }
    }

    private static byte[] addSignatureWatermark(byte[] pdfBytes, String signerName) throws IOException {
        try (ByteArrayInputStream pdfIn = new ByteArrayInputStream(pdfBytes);
             ByteArrayOutputStream watermarkedOut = new ByteArrayOutputStream();
             PdfReader reader = new PdfReader(pdfIn);
             PdfWriter writer = new PdfWriter(watermarkedOut);
             PdfDocument document = new PdfDocument(reader, writer)) {

            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            Rectangle pageSize = document.getFirstPage().getPageSize();
            float rightX = pageSize.getRight() - WATERMARK_MARGIN_RIGHT;
            float firstLineY = pageSize.getBottom() + WATERMARK_MARGIN_TOP + WATERMARK_LINE_GAP;

            PdfCanvas canvas = new PdfCanvas(
                    document.getFirstPage().newContentStreamBefore(),
                    document.getFirstPage().getResources(),
                    document);
            canvas.saveState();
            canvas.setExtGState(new PdfExtGState().setFillOpacity(WATERMARK_OPACITY));
            showRightAlignedText(canvas, font, "Sign By: " + toAsciiName(signerName),
                    WATERMARK_NAME_FONT_SIZE, rightX, firstLineY);
            showRightAlignedText(canvas, font, LocalDateTime.now().format(WATERMARK_TIME_FORMAT),
                    WATERMARK_TIME_FONT_SIZE, rightX, firstLineY - WATERMARK_LINE_GAP);
            canvas.restoreState();

            document.close();
            return watermarkedOut.toByteArray();
        }
    }

    private static void showRightAlignedText(
            PdfCanvas canvas,
            PdfFont font,
            String text,
            float fontSize,
            float rightX,
            float y) {

        float x = rightX - font.getWidth(text, fontSize);
        canvas.beginText()
                .setFontAndSize(font, fontSize)
                .moveText(x, y)
                .showText(text)
                .endText();
    }

    private static String toAsciiName(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace("Đ", "D")
                .replace("đ", "d")
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{ASCII}]", "")
                .trim();
    }

    /** Subject DN of the signing certificate (for audit logs / API). */
    public String signerCertificateSubject() {
        return signingMaterial != null ? signingMaterial.signerSubject() : null;
    }

    private static void ensureBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private SigningMaterial loadSigningMaterial()
            throws KeyStoreException, IOException, NoSuchAlgorithmException,
            UnrecoverableKeyException, CertificateException {

        String path = properties.getKeystorePath();
        if (path == null || path.isBlank()) {
            throw new BadRequestException("digital-signature.keystore-path is required when enabled");
        }
        Path keystoreFile = Path.of(path);
        if (!Files.isRegularFile(keystoreFile)) {
            throw new BadRequestException("Keystore not found: " + keystoreFile);
        }

        char[] password = properties.getKeystorePassword() != null
                ? properties.getKeystorePassword().toCharArray()
                : new char[0];

        KeyStore keyStore = KeyStore.getInstance(properties.getKeystoreType());
        try (InputStream in = Files.newInputStream(keystoreFile)) {
            keyStore.load(in, password);
        }

        String alias = resolveKeyAlias(keyStore, properties.getKeyAlias());
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password);
        if (privateKey == null) {
            throw new BadRequestException("No private key for alias: " + alias);
        }

        Certificate[] chain = keyStore.getCertificateChain(alias);
        if (chain == null || chain.length == 0) {
            throw new BadRequestException("No certificate chain for alias: " + alias);
        }

        X509Certificate signingCert = (X509Certificate) chain[0];
        List<X509Certificate> certList = new ArrayList<>();
        for (Certificate cert : chain) {
            certList.add((X509Certificate) cert);
        }

        return new SigningMaterial(alias, privateKey, signingCert, certList);
    }

    private static String resolveKeyAlias(KeyStore keyStore, String configuredAlias)
            throws KeyStoreException {
        if (configuredAlias != null && !configuredAlias.isBlank()) {
            return configuredAlias;
        }
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                return alias;
            }
        }
        throw new BadRequestException("No key entry found in keystore");
    }

    private record SigningMaterial(
            String keyAlias,
            PrivateKey privateKey,
            X509Certificate signingCertificate,
            List<X509Certificate> certificateChain) {

        String signerSubject() {
            return signingCertificate.getSubjectX500Principal().getName();
        }
    }
}
