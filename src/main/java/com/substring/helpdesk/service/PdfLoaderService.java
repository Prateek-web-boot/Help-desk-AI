package com.substring.helpdesk.service;

import org.apache.pdfbox.Loader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class PdfLoaderService {

    private final ResourceLoader resourceLoader;

    public PdfLoaderService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String loadPdfText(String path) {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + path);

            PDDocument document = Loader.loadPDF(resource.getInputStream().readAllBytes());
            PDFTextStripper stripper = new PDFTextStripper();

            String text = stripper.getText(document);
            document.close();

            return text;

        } catch (Exception e) {
            throw new RuntimeException("Failed to read PDF", e);
        }
    }
}
