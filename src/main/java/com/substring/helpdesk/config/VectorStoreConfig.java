package com.substring.helpdesk.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {
//
//    @Value("${spring.ai.vectorstore.pinecone.api-key}")
//    private String apiKey;
//
//    @Value("${spring.ai.vectorstore.pinecone.index-name}")
//    private String indexName;


//    @Primary
//    @Bean
//    public VectorStore pineconeVectorStore(EmbeddingModel embeddingModel) {
//
//        return PineconeVectorStore.builder(embeddingModel)
//                .apiKey(apiKey)
//                .indexName(indexName)
//                .build();
//    }


    @Primary
    @Bean
    public VectorStore pgVectorStore(EmbeddingModel embeddingModel, JdbcTemplate jdbcTemplate) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("semantic_cache")
                .initializeSchema(true)
                .build();
    }
}
