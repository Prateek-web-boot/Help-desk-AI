package com.substring.helpdesk.controller;

import com.substring.helpdesk.service.DocumentIngestPipeline;
import com.substring.helpdesk.service.ProjectCatalogService;
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
    private final ProjectCatalogService projectCatalogService;


    public DocumentController(PdfLoaderService pdfLoaderService,
                              DocumentIngestPipeline documentIngestPipeline,
                              ProjectCatalogService projectCatalogService){
        this.pdfLoaderService = pdfLoaderService;
        this.documentIngestPipeline = documentIngestPipeline;
        this.projectCatalogService = projectCatalogService;
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

    @GetMapping("/projects")
    public ResponseEntity<List<String>> getProjects() {
        return ResponseEntity.ok(projectCatalogService.getAvailableProjects());
    }
}
