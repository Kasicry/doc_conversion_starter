package com.docconversion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfToDocxConverterTest {

    private final PdfToDocxConverter converter = new PdfToDocxConverter();

    @Test
    void convertsArbitraryPdfIntoVisualBackgroundAndEditableTextBoxes(@TempDir Path tempDirectory) throws Exception {
        Path pdf = tempDirectory.resolve("arbitrary-document.pdf");
        Files.copy(Path.of("../cv_doc/배달의민족 회원탈퇴 요청서.pdf"), pdf);

        byte[] result = converter.convert(pdf);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result))) {
            String documentXml = document.getDocument().xmlText();
            assertThat(document.getAllPictures()).hasSize(1);
            assertThat(documentXml).contains("page-background-1");
            assertThat(documentXml).contains("editable-text-1-");
            assertThat(documentXml).contains("배달의민족 회원탈퇴 요청서");
        }
    }
}
