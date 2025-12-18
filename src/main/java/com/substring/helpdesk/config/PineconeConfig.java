package com.substring.helpdesk.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pinecone.PineconeVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PineconeConfig {

    @Value("${spring.ai.vectorstore.pinecone.api-key}")
    private String apiKey;

    @Value("${spring.ai.vectorstore.pinecone.index-name}")
    private String indexName;

    @Value("${spring.ai.vectorstore.pinecone.host}")
    private String host;


    @Bean
    public VectorStore pineconeVectorStore(EmbeddingModel embeddingModel) {

        return PineconeVectorStore.builder(embeddingModel)
                .apiKey(apiKey)
                .indexName(indexName)
                .build();
    }
}
