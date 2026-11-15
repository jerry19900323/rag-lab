package com.example.raglab.application;

import com.example.raglab.infrastructure.elasticsearch.ElasticsearchChunkSearch;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

/** Selects related history and document evidence before generating an answer. */
@Service
public class ChatService {
    private final ChunkSearchService search;
    private final ModelSelectionResolver models;
    private final ChatCompletionClient completion;
    private final ConversationHistoryService history;
    private final DocumentMetadataQuery documents;
    private final ChatTraceService traces;
    private final ElasticsearchChunkSearch profileSearch;

    public ChatService(ChunkSearchService search, ModelSelectionResolver models,
            ChatCompletionClient completion, ConversationHistoryService history,
            DocumentMetadataQuery documents, ChatTraceService traces,
            ElasticsearchChunkSearch profileSearch) {
        this.search = search;
        this.models = models;
        this.completion = completion;
        this.history = history;
        this.documents = documents;
        this.traces = traces;
        this.profileSearch = profileSearch;
    }

    public ChatResponse ask(ChatRequest request) { return ask(request, null); }

    public ChatResponse ask(ChatRequest request, Long userId) {
        TraceRun run = new TraceRun(userId, request, ignored -> {});
        try {
            run.emit(Map.of("step", "started", "message", "Preparing question"));
            PreparedAnswer prepared = prepare(request, userId, run::emit);
            String answer = completion.complete(prepared.model(), prepared.question(), prepared.context(),
                    request.temperature(), request.maxTokens());
            run.emit(Map.of("step", "answer_generated", "message", "Answer generated",
                    "answerLength", answer.length()));
            if (userId != null) history.append(userId, request.conversationId(), request.question(), answer);
            run.save("COMPLETED", null);
            return new ChatResponse(answer, prepared.sources());
        } catch (RuntimeException exception) {
            run.saveFailure(exception);
            throw exception;
        }
    }

    public void stream(ChatRequest request, Consumer<List<ChunkSearchHit>> onSources,
            Consumer<String> onToken) {
        stream(request, null, onSources, onToken);
    }

    public void stream(ChatRequest request, Long userId, Consumer<List<ChunkSearchHit>> onSources,
            Consumer<String> onToken) {
        stream(request, userId, onSources, onToken, ignored -> {});
    }

    public void stream(ChatRequest request, Long userId, Consumer<List<ChunkSearchHit>> onSources,
            Consumer<String> onToken, Consumer<Map<String, Object>> onProgress) {
        TraceRun run = new TraceRun(userId, request, onProgress);
        try {
            run.emit(Map.of("step", "started", "message", "Preparing question"));
            PreparedAnswer prepared = prepare(request, userId, run::emit);
            onSources.accept(prepared.sources());
            run.emit(Map.of("step", "generating", "message", "Generating answer",
                    "sourceCount", prepared.sources().size()));
            StringBuilder answer = new StringBuilder();
            completion.stream(prepared.model(), prepared.question(), prepared.context(),
                    request.temperature(), request.maxTokens(), token -> {
                        answer.append(token);
                        onToken.accept(token);
                    });
            run.emit(Map.of("step", "answer_generated", "message", "Answer generated",
                    "answerLength", answer.length()));
            if (userId != null) history.append(userId, request.conversationId(), request.question(), answer.toString());
            run.save("COMPLETED", null);
        } catch (RuntimeException exception) {
            run.saveFailure(exception);
            throw exception;
        }
    }

    private PreparedAnswer prepare(ChatRequest request, Long userId,
            Consumer<Map<String, Object>> onProgress) {
        if (request == null) throw new IllegalArgumentException("Chat request is required");
        ModelPurpose preferred = request.mode() == ChatMode.DOCUMENT ? ModelPurpose.ANSWER : ModelPurpose.CHAT;
        ModelPurpose fallback = preferred == ModelPurpose.ANSWER ? ModelPurpose.CHAT : ModelPurpose.ANSWER;
        ResolvedModel model = models.resolve(preferred)
                .or(() -> models.resolve(fallback))
                .orElseThrow(() -> new ModelConfigurationUnavailableException(
                        "No enabled CHAT or ANSWER model is configured"));

        List<ConversationHistoryService.Turn> turns = userId == null ? List.of()
                : history.recent(userId, request.conversationId());
        long totalTurns = userId == null ? turns.size() : history.totalTurns(userId, request.conversationId());
        onProgress.accept(Map.of("step", "history_loaded", "message", "Loaded conversation history",
                "turnCount", turns.size(), "totalTurns", totalTurns));
        ResolvedModel routingModel = models.resolve(ModelPurpose.ROUTING).orElse(model);
        onProgress.accept(Map.of("step", "routing", "message", "Choosing evidence sources"));
        ChatRoutingDecision decision = completion.route(routingModel, request.question(), turns);
        ChatRoute route = decision.route();
        String routingReason = decision.reason();
        if (request.mode() == ChatMode.CHAT && route.usesDocuments()) {
            route = route.usesHistory() ? ChatRoute.HISTORY : ChatRoute.GENERAL;
            routingReason = "Document search disabled by explicit CHAT mode; " + routingReason;
        }
        onProgress.accept(Map.of("step", "route_selected", "message", "Selected evidence sources",
                "route", route.name(), "routingModelPurpose", routingModel.usageType().name(),
                "reason", routingReason, "searchQuery",
                route.usesDocuments() ? decision.searchQuery() : ""));

        List<ConversationHistoryService.Turn> selectedTurns = List.of();
        List<ChatCompletionClient.HistoryJudgment> historyJudgments = List.of();
        String retrievalQuestion = route.usesDocuments() ? decision.searchQuery() : request.question();
        if (route == ChatRoute.HISTORY) {
            historyJudgments = turns.stream()
                    .map(turn -> new ChatCompletionClient.HistoryJudgment(turn, true,
                            "Question asks about this conversation"))
                    .toList();
        } else if (route == ChatRoute.BOTH && !turns.isEmpty()) {
            historyJudgments = completion.assessHistory(model, request.question(), turns);
        }
        selectedTurns = historyJudgments.stream().filter(ChatCompletionClient.HistoryJudgment::accepted)
                .map(ChatCompletionClient.HistoryJudgment::turn).toList();
        if (route.usesHistory()) {
            onProgress.accept(Map.of("step", "history_selected", "message", "Selected relevant history",
                    "turnCount", selectedTurns.size(), "decisions", historyJudgments.stream()
                            .map(judgment -> Map.of("question", limit(judgment.turn().question(), 300),
                                    "answer", limit(judgment.turn().answer(), 500),
                                    "accepted", judgment.accepted(), "reason", judgment.reason()))
                            .toList()));
        }

        List<ChunkSearchHit> sources = List.of();
        if (route.usesDocuments()) {
            String documentId = request.documentId();
            if (documentId == null && decision.documentHint() != null) {
                List<DocumentSummary> matches = documents.findByExactFileName(decision.documentHint(), 2);
                if (matches.size() == 1) documentId = matches.get(0).documentId();
                onProgress.accept(Map.of("step", "document_filter", "message", "Checked document titles",
                        "hint", decision.documentHint(), "matchCount", matches.size(),
                        "applied", documentId != null));
            }
            String retrievalQuery = retrievalQuestion.substring(0, Math.min(500, retrievalQuestion.length()));
            List<String> selectedDocumentIds = List.of();
            boolean legacyFallback = false;
            if (documentId == null) {
                List<DocumentProfileHit> profiles = profileSearch.searchDocumentProfiles(retrievalQuery, 5);
                onProgress.accept(Map.of("step", "document_profiles", "message", "Found candidate documents",
                        "candidateCount", profiles.size(), "candidates", profiles.stream()
                                .map(profile -> Map.of("documentId", profile.documentId(),
                                        "fileName", profile.fileName(), "summary",
                                        limit(profile.summary(), 500), "score", profile.score())).toList()));
                if (profiles.isEmpty()) {
                    legacyFallback = true;
                    onProgress.accept(Map.of("step", "document_selection", "message",
                            "No indexed document summaries; searching chunks for legacy documents",
                            "selectedCount", 0, "fallback", true));
                } else {
                    List<ChatCompletionClient.DocumentJudgment> judgments =
                            completion.assessDocuments(routingModel, retrievalQuestion, profiles);
                    selectedDocumentIds = judgments.stream()
                            .filter(ChatCompletionClient.DocumentJudgment::accepted)
                            .map(judgment -> judgment.profile().documentId()).limit(3).toList();
                    legacyFallback = selectedDocumentIds.isEmpty();
                    onProgress.accept(Map.of("step", "document_selection", "message",
                            "Selected documents from summaries", "selectedCount", selectedDocumentIds.size(),
                            "fallback", legacyFallback, "decisions", judgments.stream()
                                    .map(judgment -> Map.of("documentId", judgment.profile().documentId(),
                                            "fileName", judgment.profile().fileName(), "accepted",
                                            judgment.accepted(), "reason", judgment.reason())).toList()));
                }
            }
            onProgress.accept(Map.of("step", "searching", "message", "Searching documents",
                    "query", retrievalQuery, "documentScope", documentId != null
                            ? List.of(documentId) : selectedDocumentIds));
            List<ChunkSearchHit> candidates;
            ChunkSearchService.AnswerSearchSession answerSearch = search.answerSearchSession(retrievalQuery);
            if (documentId != null || legacyFallback) {
                candidates = answerSearch.search(new ChunkSearchRequest(
                        retrievalQuery, documentId, 0, request.topK()));
            } else if (selectedDocumentIds.isEmpty()) {
                candidates = List.of();
            } else {
                List<List<ChunkSearchHit>> perDocument = new ArrayList<>();
                for (String id : selectedDocumentIds) {
                    perDocument.add(answerSearch.search(new ChunkSearchRequest(
                            retrievalQuery, id, 0, request.topK())));
                }
                List<ChunkSearchHit> merged = new ArrayList<>();
                for (int position = 0; position < request.topK() && merged.size() < request.topK() * 2;
                        position++) {
                    for (List<ChunkSearchHit> hits : perDocument) {
                        if (position < hits.size() && merged.size() < request.topK() * 2)
                            merged.add(hits.get(position));
                    }
                }
                candidates = List.copyOf(merged);
            }
            onProgress.accept(Map.of("step", "retrieved", "message", "Found document candidates",
                    "candidateCount", candidates.size(), "candidates", candidates.stream()
                            .map(hit -> Map.of("documentId", hit.documentId(), "fileName", hit.fileName(),
                                    "chunkIndex", hit.chunkIndex(), "score", hit.score(),
                                    "contentPreview", limit(hit.content(), 500)))
                            .toList()));
            List<ChatCompletionClient.SourceJudgment> firstJudgments = candidates.isEmpty() ? List.of()
                    : completion.assessSources(model, retrievalQuestion, candidates);
            List<ChatCompletionClient.SourceJudgment> judgments = new ArrayList<>(firstJudgments);
            if (documentId == null && !legacyFallback && !selectedDocumentIds.isEmpty()
                    && firstJudgments.stream().noneMatch(ChatCompletionClient.SourceJudgment::accepted)) {
                onProgress.accept(Map.of("step", "document_fallback", "message",
                        "No selected document passage answered the question; checking remaining chunks",
                        "reason", "Selected document summaries did not yield accepted evidence"));
                List<ChunkSearchHit> fallbackCandidates = answerSearch.search(
                        new ChunkSearchRequest(retrievalQuery, null, 0, request.topK()));
                onProgress.accept(Map.of("step", "retrieved", "message", "Found fallback candidates",
                        "candidateCount", fallbackCandidates.size(), "candidates", fallbackCandidates.stream()
                                .map(hit -> Map.of("documentId", hit.documentId(), "fileName", hit.fileName(),
                                        "chunkIndex", hit.chunkIndex(), "score", hit.score(),
                                        "contentPreview", limit(hit.content(), 500))).toList()));
                if (!fallbackCandidates.isEmpty())
                    judgments.addAll(completion.assessSources(model, retrievalQuestion, fallbackCandidates));
            }
            sources = judgments.stream().filter(ChatCompletionClient.SourceJudgment::accepted)
                    .map(ChatCompletionClient.SourceJudgment::hit).limit(request.topK()).toList();
            onProgress.accept(Map.of("step", "evidence_selected", "message", "Selected document evidence",
                    "sourceCount", sources.size(), "decisions", judgments.stream()
                            .map(judgment -> Map.of("documentId", judgment.hit().documentId(),
                                    "fileName", judgment.hit().fileName(), "chunkIndex",
                                    judgment.hit().chunkIndex(), "accepted", judgment.accepted(),
                                    "reason", judgment.reason())).toList()));
        }
        List<ChunkSearchHit> selectedSources = sources;
        String documentContext = IntStream.range(0, selectedSources.size())
                .mapToObj(index -> "[" + (index + 1) + "] " + selectedSources.get(index).fileName() + ": "
                        + selectedSources.get(index).content().substring(0,
                                Math.min(1500, selectedSources.get(index).content().length())))
                .reduce((left, right) -> left + "\n\n" + right).orElse("");
        StringBuilder context = new StringBuilder();
        if (route == ChatRoute.HISTORY || !selectedTurns.isEmpty()) {
            context.append("Total completed turns in this conversation: ").append(totalTurns)
                    .append(". Use this exact number for count questions. ")
                    .append("Only the detailed turns below are available for content questions.\n");
            context.append("Conversation history (").append(selectedTurns.size())
                    .append(" retained turns, oldest to newest):\n");
            if (selectedTurns.isEmpty()) context.append("(none)\n");
            for (int index = 0; index < selectedTurns.size(); index++) {
                var turn = selectedTurns.get(index);
                context.append(index + 1).append(". User: ").append(limit(turn.question(), 500))
                        .append("\n   Assistant: ").append(limit(turn.answer(), 1000)).append('\n');
            }
        }
        if (!documentContext.isEmpty()) {
            if (!context.isEmpty()) context.append('\n');
            context.append("Document passages:\n").append(documentContext);
        }
        return new PreparedAnswer(model, request.question(), context.toString(), sources);
    }

    private String limit(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private record PreparedAnswer(ResolvedModel model, String question, String context,
            List<ChunkSearchHit> sources) {}

    private final class TraceRun {
        private final Long userId;
        private final ChatRequest request;
        private final Consumer<Map<String, Object>> downstream;
        private final String requestId = UUID.randomUUID().toString();
        private final String startedAt = Instant.now().toString();
        private final long startNanos = System.nanoTime();
        private long previousNanos = startNanos;
        private final List<Map<String, Object>> steps = new ArrayList<>();

        private TraceRun(Long userId, ChatRequest request, Consumer<Map<String, Object>> downstream) {
            this.userId = userId;
            this.request = request;
            this.downstream = downstream;
        }

        private void emit(Map<String, Object> event) {
            long now = System.nanoTime();
            Map<String, Object> step = new LinkedHashMap<>(event);
            step.put("requestId", requestId);
            step.put("elapsedMs", (now - startNanos) / 1_000_000);
            step.put("durationMs", (now - previousNanos) / 1_000_000);
            previousNanos = now;
            Map<String, Object> snapshot = Map.copyOf(step);
            steps.add(snapshot);
            downstream.accept(snapshot);
        }

        private void save(String status, String error) {
            if (userId == null || request == null) return;
            traces.save(userId, request.conversationId(), new ChatTraceService.Trace(requestId,
                    request.question(), status, startedAt, Instant.now().toString(), steps, error));
        }

        private void saveFailure(RuntimeException failure) {
            try {
                save("FAILED", failure.getMessage());
            } catch (RuntimeException saveError) {
                failure.addSuppressed(saveError);
            }
        }
    }
}
