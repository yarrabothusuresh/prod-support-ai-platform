package com.example.prodsupport.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class TestKnowledgeVectorStoreConfig {

    @Bean
    @Primary
    public EmbeddingModel testEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public float[] embed(String text) {
                float[] vector = new float[768];
                int h = text != null ? text.hashCode() : 0;
                for (int i = 0; i < 768; i++) {
                    vector[i] = (float) Math.sin(h + i);
                }
                return vector;
            }

            @Override
            public float[] embed(Document document) {
                return embed(document.getContent());
            }

            @Override
            public List<float[]> embed(List<String> texts) {
                List<float[]> list = new ArrayList<>();
                for (String t : texts) {
                    list.add(embed(t));
                }
                return list;
            }

            @Override
            public EmbeddingResponse embedForResponse(List<String> texts) {
                List<Embedding> embeddings = new ArrayList<>();
                for (int i = 0; i < texts.size(); i++) {
                    embeddings.add(new Embedding(embed(texts.get(i)), i));
                }
                return new EmbeddingResponse(embeddings);
            }

            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                return embedForResponse(request.getInstructions());
            }

            @Override
            public int dimensions() {
                return 768;
            }
        };
    }

    @Bean
    @Primary
    public VectorStore testVectorStore(EmbeddingModel embeddingModel) {
        return new SimpleVectorStore(embeddingModel);
    }
}
