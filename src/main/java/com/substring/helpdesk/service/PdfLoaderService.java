package com.substring.helpdesk.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PdfLoaderService {

    public String loadPdfText(MultipartFile file) {
        return loadPdfDocuments(file).stream()
                .map(Document::getText)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    public List<Document> loadPdfDocuments(MultipartFile file) {
        try {
            try (PDDocument document = Loader.loadPDF(file.getInputStream().readAllBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                int totalPages = document.getNumberOfPages();
                List<Document> pages = new ArrayList<>();

                for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
                    stripper.setStartPage(pageNumber);
                    stripper.setEndPage(pageNumber);

                    String text = normalizeWhitespace(stripper.getText(document));
                    if (text.isBlank()) {
                        continue;
                    }

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("source_file", safeFilename(file));
                    metadata.put("page_number", pageNumber);
                    metadata.put("page_count", totalPages);
                    metadata.put("source_type", "pdf");

                    pages.add(new Document(text, metadata));
                }

                return pages;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read PDF", e);
        }
    }

    private String safeFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return "uploaded-document.pdf";
        }
        return originalFilename.replace("\\", "/").substring(originalFilename.replace("\\", "/").lastIndexOf('/') + 1);
    }

    private String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
