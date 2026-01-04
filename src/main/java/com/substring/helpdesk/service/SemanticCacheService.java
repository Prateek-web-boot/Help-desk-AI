package com.substring.helpdesk.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SemanticCacheService {

    @Autowired
    private VectorStore vectorStore;


    public void setCachedAnswer(String userEmail, String question, String answer, String convoId) {

        Document doc = new Document(question,
                Map.of("userEmail", userEmail,
                        "convoId", convoId,
                        "cachedAnswer", answer
                ));
        vectorStore.add(List.of(doc));
    }




}
