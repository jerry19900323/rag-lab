package com.example.raglab.application;

import java.util.List;

public record ChatResponse(String answer, List<ChunkSearchHit> sources) {
    public ChatResponse { sources = List.copyOf(sources); }
}
