package com.substring.helpdesk.controller;

import com.substring.helpdesk.service.DocumentIngestPipeline;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/docs")
public class DocumentController {

    private final DocumentIngestPipeline ingestionService;

    public DocumentController(DocumentIngestPipeline ingestionService) {
        this.ingestionService = ingestionService;
    }


    @PostMapping("/upload")
    public String uploadDoc(@RequestBody String content,
                            @RequestParam String project) {

        ingestionService.ingest(content, project);

        return "Document ingested successfully!";
    }

}
