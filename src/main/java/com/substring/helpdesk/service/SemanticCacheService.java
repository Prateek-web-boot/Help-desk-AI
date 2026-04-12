package com.substring.helpdesk.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SemanticCacheService {

    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private VectorStore semanticCacheVectorStore;


    public void setCachedAnswer(String userEmail, String question, String answer, String convoId) {

        Document doc = new Document(question,
                Map.of("userEmail", userEmail,
                        "convoId", convoId,
                        "cachedAnswer", answer
                ));
        vectorStore.add(List.of(doc));
    }



    public String getCachedAnswer(String email, String query) {

        List<Document> results = semanticCacheVectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .similarityThreshold(0.9)
                        .filterExpression("userEmail== '" + email + "'")
                        .topK(1)
                        .build()
        );

        if(!results.isEmpty()){
            return results.get(0).getText();
        }
        return null;
    }

}
