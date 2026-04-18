package com.substring.helpdesk.service;

import com.substring.helpdesk.entity.ChatMode;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SemanticCacheService {


    @Qualifier("userQueryCacheVectorStore")
    private final VectorStore semanticCacheVectorStore;

    public SemanticCacheService(
            @Qualifier("userQueryCacheVectorStore") VectorStore semanticCacheVectorStore
    ) {
        this.semanticCacheVectorStore = semanticCacheVectorStore;
    }


    public void setCachedAnswer(String userEmail, String question, String answer, String convoId, ChatMode mode) {

        Document doc = new Document(question,
                Map.of("userEmail", userEmail,
                        "convoId", convoId,
                        "mode", mode.name(),
                        "cachedAnswer", answer
                ));
        semanticCacheVectorStore.add(List.of(doc));
    }

    public String getCachedAnswer(String email, String query, String convoId, ChatMode mode) {

        String filter = "userEmail == '" + email + "' && mode == '" + mode.name() + "'";
        if (mode != ChatMode.RAG && convoId != null && !convoId.isBlank()) {
            filter = filter + " && convoId == '" + convoId + "'";
        }

        List<Document> results = semanticCacheVectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .similarityThreshold(0.95)
                        .filterExpression(filter)
                        .topK(3)
                        .build()
        );

        if (!results.isEmpty()) {
            Map<String, Object> metadata = results.get(0).getMetadata();
            if (metadata != null && metadata.containsKey("cachedAnswer")) {
                return metadata.get("cachedAnswer").toString();
            }
        }
        return null;
    }

}
