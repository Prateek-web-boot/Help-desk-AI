package com.substring.helpdesk.service;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentIngestPipeline {

    @Qualifier("companyDocsVectorStore")
    private final VectorStore companyDocsVectorStore;


    public DocumentIngestPipeline( @Qualifier("companyDocsVectorStore") VectorStore companyDocsVectorStore) {
        this.companyDocsVectorStore = companyDocsVectorStore;
    }

    @Transactional
    public void ingest(String content, String project) {

        System.out.println("🚀 Starting ingestion...");

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(350)
                .build();

        // Step 1: Split
        List<Document> baseChunks = splitter.apply(List.of(new Document(content)));

        System.out.println("📄 Total chunks created: " + baseChunks.size());

        // Step 2: Apply overlap
        List<Document> finalChunks = applyOverlap(baseChunks, 50);

        // Step 3: Add metadata
        finalChunks.forEach(doc -> doc.getMetadata().put("project", project));

        System.out.println("📦 Ready to insert chunks: " + finalChunks.size());

        // Step 4: Insert
        companyDocsVectorStore.add(finalChunks);

        System.out.println("✅ Insert completed!");
    }

    private List<Document> applyOverlap(List<Document> chunks, int overlapSize) {

        List<Document> result = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {

            Document currentDoc = chunks.get(i);
            String currentText = currentDoc.getText();

            if (i > 0) {
                String prevText = chunks.get(i - 1).getText();

                String overlapText = prevText.substring(
                        Math.max(0, prevText.length() - overlapSize)
                );

                currentText = overlapText + " " + currentText;
            }

            // ✅ IMPORTANT: preserve metadata
            Document newDoc = new Document(currentText, currentDoc.getMetadata());

            result.add(newDoc);
        }

        return result;
    }

}
