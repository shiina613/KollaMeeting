package com.example.kolla.services;

import com.example.kolla.services.impl.DocxMinutesRenderer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DocxMinutesRendererTest {

    @Test
    void renderLines_createsValidDocxPackageWithEscapedVietnameseText() throws Exception {
        byte[] docx = DocxMinutesRenderer.renderLines(List.of(
                "BIÊN BẢN CUỘC HỌP - BẢN NHÁP",
                "",
                "Cuộc họp: Họp nghiệm thu",
                "[Nguyễn Văn A]",
                "Nội dung <quan trọng> & đúng dấu tiếng Việt"
        ));

        Map<String, String> entries = unzipTextEntries(docx);

        assertThat(entries).containsKeys(
                "[Content_Types].xml",
                "_rels/.rels",
                "word/document.xml",
                "docProps/core.xml",
                "docProps/app.xml"
        );
        assertThat(entries.get("word/document.xml"))
                .contains("Họp nghiệm thu")
                .contains("Nguyễn Văn A")
                .contains("Nội dung &lt;quan trọng&gt; &amp; đúng dấu tiếng Việt");
    }

    private Map<String, String> unzipTextEntries(byte[] docx) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()
                        && (entry.getName().endsWith(".xml") || entry.getName().endsWith(".rels"))) {
                    entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return entries;
    }
}
