package com.example.kolla.services.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal OOXML renderer for meeting minutes DOCX files.
 */
public final class DocxMinutesRenderer {

    private DocxMinutesRenderer() {
    }

    public static byte[] renderLines(List<String> lines) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {

            write(zip, "[Content_Types].xml", contentTypesXml());
            write(zip, "_rels/.rels", rootRelationshipsXml());
            write(zip, "docProps/core.xml", corePropertiesXml());
            write(zip, "docProps/app.xml", appPropertiesXml());
            write(zip, "word/document.xml", documentXml(lines));

            zip.finish();
            return out.toByteArray();
        }
    }

    private static void write(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
                  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
                </Types>
                """;
    }

    private static String rootRelationshipsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
                  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
                </Relationships>
                """;
    }

    private static String corePropertiesXml() {
        String now = Instant.now().toString();
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                  xmlns:dc="http://purl.org/dc/elements/1.1/"
                  xmlns:dcterms="http://purl.org/dc/terms/"
                  xmlns:dcmitype="http://purl.org/dc/dcmitype/"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <dc:title>Meeting minutes</dc:title>
                  <dc:creator>KollaMeeting</dc:creator>
                  <cp:lastModifiedBy>KollaMeeting</cp:lastModifiedBy>
                  <dcterms:created xsi:type="dcterms:W3CDTF">%s</dcterms:created>
                  <dcterms:modified xsi:type="dcterms:W3CDTF">%s</dcterms:modified>
                </cp:coreProperties>
                """.formatted(now, now);
    }

    private static String appPropertiesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
                  xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
                  <Application>KollaMeeting</Application>
                </Properties>
                """;
    }

    private static String documentXml(List<String> lines) {
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            body.append(paragraphXml(line));
        }

        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                %s
                    <w:sectPr>
                      <w:pgSz w:w="11906" w:h="16838"/>
                      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/>
                    </w:sectPr>
                  </w:body>
                </w:document>
                """.formatted(body);
    }

    private static String paragraphXml(String line) {
        if (line == null || line.isBlank()) {
            return "    <w:p><w:pPr><w:spacing w:after=\"120\"/></w:pPr></w:p>\n";
        }

        boolean bold = line.startsWith("BIÊN BẢN")
                || (line.startsWith("[") && line.contains("]"));
        String runProperties = bold ? "<w:rPr><w:b/></w:rPr>" : "";
        return """
                    <w:p>
                      <w:pPr><w:spacing w:after="120"/></w:pPr>
                      <w:r>%s<w:t xml:space="preserve">%s</w:t></w:r>
                    </w:p>
                """.formatted(runProperties, escapeXml(line));
    }

    private static String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
