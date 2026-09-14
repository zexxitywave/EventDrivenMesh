package com.hacisimsek.product.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@Slf4j
public class EmbeddingService {

    private final RestClient restClient;
    private final String model;

    public EmbeddingService(
            @Value("${app.ollama.base-url}") String baseUrl,
            @Value("${app.ollama.model}") String model) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.model = model;
    }

    /** Calls Ollama /api/embed and returns the 768-dim float vector. */
    public float[] embed(String text) {
        EmbedResponse resp = restClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbedRequest(model, text))
                .retrieve()
                .body(EmbedResponse.class);

        if (resp == null || resp.embeddings() == null || resp.embeddings().isEmpty()) {
            throw new IllegalStateException("Ollama returned no embeddings for input");
        }
        List<Float> dims = resp.embeddings().get(0);
        float[] vec = new float[dims.size()];
        for (int i = 0; i < dims.size(); i++) {
            vec[i] = dims.get(i);
        }
        return vec;
    }

    /** Formats a float vector as the Postgres vector literal: "[0.1,0.2,...]" */
    public static String vectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private record EmbedRequest(String model, String input) {}
    private record EmbedResponse(List<List<Float>> embeddings) {}
}
