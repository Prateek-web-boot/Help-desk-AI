package com.substring.helpdesk.controller;

import com.substring.helpdesk.service.DocumentIngestPipeline;
import com.substring.helpdesk.service.PdfLoaderService;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/docs")
public class DocumentController {

    private final PdfLoaderService pdfLoaderService;
    private final DocumentIngestPipeline documentIngestPipeline;


    public DocumentController(PdfLoaderService pdfLoaderService, DocumentIngestPipeline documentIngestPipeline){
        this.pdfLoaderService = pdfLoaderService;
        this.documentIngestPipeline = documentIngestPipeline;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadDoc(@RequestParam("file") MultipartFile file,
                                           @RequestParam("project") String project) {

        if(file.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        List<Document> pages = pdfLoaderService.loadPdfDocuments(file);
        documentIngestPipeline.ingest(pages, project);

        return ResponseEntity.ok("Document Uploaded & Ingested successfully!");
    }
}
