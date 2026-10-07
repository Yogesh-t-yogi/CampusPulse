package com.campuspulse.service.embedding;

import java.util.List;

public interface EmbeddingService {

    List<Double> generateEmbedding(String text);
}