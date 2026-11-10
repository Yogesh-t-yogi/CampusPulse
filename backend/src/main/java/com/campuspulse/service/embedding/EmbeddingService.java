package com.campuspulse.service.embedding;

import java.util.List;
import java.util.stream.Collectors;

public interface EmbeddingService {

    String MODEL = "sentence-transformers/all-MiniLM-L6-v2";

    List<Double> generateEmbedding(String text);

    /** One vector per text, same order. */
    List<List<Double>> generateEmbeddings(List<String> texts);

    /** pgvector literal, e.g. "[0.1,0.2,...]". */
    static String toVectorLiteral(List<Double> embedding) {
        return embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }
}
