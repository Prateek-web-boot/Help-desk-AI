package com.substring.helpdesk.config;

import com.substring.helpdesk.service.DocumentIngestPipeline;
import com.substring.helpdesk.service.PdfLoaderService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataLoaderConfig {

    @Bean
    public CommandLineRunner loadData(
            PdfLoaderService pdfLoaderService,
            DocumentIngestPipeline pipeline) {

        return args -> {

            String content = pdfLoaderService.loadPdfText("helpdesk_large_rag_doc.pdf");

            pipeline.ingest(content, "helpdesk-project");

            System.out.println("✅ PDF ingested successfully!");
        };
    }
}