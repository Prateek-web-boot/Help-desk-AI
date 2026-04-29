package com.substring.helpdesk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentIngestPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestPipeline.class);

    @Value("${helpdesk.rag.chunk-size:350}")
    private int chunkSize = 350;

    @Value("${helpdesk.rag.chunk-overlap-words:40}")
    private int overlapWords = 40;

    @Value("${helpdesk.rag.include-source-prefix:true}")
    private boolean includeSourcePrefix = true;

    @Qualifier("companyDocsVectorStore")
    private final VectorStore companyDocsVectorStore;

    public DocumentIngestPipeline(@Qualifier("companyDocsVectorStore") VectorStore companyDocsVectorStore) {
        this.companyDocsVectorStore = companyDocsVectorStore;
    }

    @Transactional
    public void ingest(String content, String project) {
        ingest(List.of(new Document(content)), project);
    }

    @Transactional
    public void ingest(List<Document> sourceDocuments, String project) {
        if (sourceDocuments == null || sourceDocuments.isEmpty()) {
            log.warn("Skipping ingestion for project={} because no source documents were provided", project);
            return;
        }

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .build();

        List<Document> finalChunks = new ArrayList<>();

        for (Document sourceDocument : sourceDocuments) {
            List<Document> baseChunks = splitter.apply(List.of(sourceDocument));
            List<Document> overlappedChunks = applyOverlap(baseChunks, overlapWords);

            for (int i = 0; i < overlappedChunks.size(); i++) {
                Document chunk = overlappedChunks.get(i);
                Map<String, Object> metadata = new HashMap<>();
                if (sourceDocument.getMetadata() != null) {
                    metadata.putAll(sourceDocument.getMetadata());
                }
                if (chunk.getMetadata() != null) {
                    metadata.putAll(chunk.getMetadata());
                }

                metadata.put("project", project);
                metadata.put("chunk_index", i);
                metadata.put("global_chunk_index", finalChunks.size());
                metadata.put("chunk_size", chunkSize);
                metadata.put("chunk_overlap_words", overlapWords);

                String sourceReference = buildSourceReference(metadata);
                if (sourceReference != null) {
                    metadata.put("source_reference", sourceReference);
                }

                String chunkText = normalizeWhitespace(chunk.getText());
                if (includeSourcePrefix && sourceReference != null) {
                    chunkText = "Source: " + sourceReference + "\n\n" + chunkText;
                }

                finalChunks.add(new Document(chunkText, metadata));
            }
        }

        if (finalChunks.isEmpty()) {
            log.warn("No non-empty chunks were generated for project={}", project);
            return;
        }

        log.info("Ingesting {} chunks for project={}", finalChunks.size(), project);
        companyDocsVectorStore.add(finalChunks);
    }

    private List<Document> applyOverlap(List<Document> chunks, int overlapWords) {
        List<Document> result = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String current = chunks.get(i).getText();

            if (i > 0) {
                String prev = chunks.get(i - 1).getText();
                String[] words = prev.split("\\s+");
                int start = Math.max(0, words.length - overlapWords);

                String overlap = String.join(" ",
                        java.util.Arrays.copyOfRange(words, start, words.length));

                current = overlap + " " + current;
            }

            result.add(new Document(current, chunks.get(i).getMetadata()));
        }

        return result;
    }

    private String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String buildSourceReference(Map<String, Object> metadata) {
        Object sourceReference = metadata.get("source_reference");
        if (sourceReference != null && !sourceReference.toString().isBlank()) {
            return sourceReference.toString();
        }

        Object sourceFile = metadata.get("source_file");
        Object pageNumber = metadata.get("page_number");
        Object pageCount = metadata.get("page_count");

        if (sourceFile == null || pageNumber == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder(sourceFile.toString())
                .append(" | page ")
                .append(pageNumber);

        if (pageCount != null) {
            builder.append('/').append(pageCount);
        }

        return builder.toString();
    }
}
