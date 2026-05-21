package com.example.exam.service;

import com.example.exam.dto.KnowledgeProfileDtos.FileProfileResponse;
import com.example.exam.dto.KnowledgeProfileDtos.ActivityResponse;
import com.example.exam.dto.KnowledgeProfileDtos.ChunkSearchResponse;
import com.example.exam.dto.KnowledgeProfileDtos.DiagnosisItemResponse;
import com.example.exam.dto.KnowledgeProfileDtos.DiagnosisResponse;
import com.example.exam.dto.KnowledgeProfileDtos.DistributionResponse;
import com.example.exam.dto.KnowledgeProfileDtos.OverviewResponse;
import com.example.exam.dto.KnowledgeProfileDtos.ReviewPressurePointResponse;
import com.example.exam.dto.KnowledgeProfileDtos.ReviewSuggestionResponse;
import com.example.exam.dto.KnowledgeProfileDtos.RiskBubbleResponse;
import com.example.exam.dto.KnowledgeProfileDtos.RiskChunkResponse;
import com.example.exam.dto.KnowledgeProfileDtos.RiskResponse;
import com.example.exam.dto.KnowledgeProfileDtos.StudyPlanPayload;
import com.example.exam.dto.KnowledgeProfileDtos.SubjectActivityResponse;
import com.example.exam.dto.KnowledgeProfileDtos.SubjectProfileResponse;
import com.example.exam.dto.KnowledgeProfileDtos.TrendPointResponse;
import com.example.exam.dto.KnowledgeProfileDtos.WeakChunkResponse;
import com.example.exam.model.MistakePracticeEvent;
import com.example.exam.model.KnowledgeChunk;
import com.example.exam.model.KnowledgeChunkEvent;
import com.example.exam.model.KnowledgeChunkEventType;
import com.example.exam.model.StudyFolder;
import com.example.exam.repository.KnowledgeChunkEventRepository;
import com.example.exam.repository.KnowledgeChunkRepository;
import com.example.exam.repository.MistakePracticeEventRepository;
import com.example.exam.repository.StudyFolderRepository;
import com.example.exam.repository.UserStudyProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeProfileService {
    private static final int RECENT_DAYS = 14;
    private static final int MIN_DIAGNOSIS_FEEDBACK_COUNT = 3;
    private static final String DIAGNOSIS_CONFIDENCE_THRESHOLD = "MEDIUM";
    private static final double WEAK_MASTERY_THRESHOLD = 0.5;
    private static final double BORDERLINE_WEAK_MASTERY_THRESHOLD = 0.65;

    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeChunkEventRepository eventRepository;
    private final MistakePracticeEventRepository mistakePracticeEventRepository;
    private final StudyFolderRepository folderRepository;
    private final UserStudyProfileRepository profileRepository;
    private final AiSettingsService aiSettingsService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public KnowledgeProfileService(KnowledgeChunkRepository chunkRepository,
                                   KnowledgeChunkEventRepository eventRepository,
                                   MistakePracticeEventRepository mistakePracticeEventRepository,
                                   StudyFolderRepository folderRepository,
                                   UserStudyProfileRepository profileRepository,
                                   AiSettingsService aiSettingsService) {
        this.chunkRepository = chunkRepository;
        this.eventRepository = eventRepository;
        this.mistakePracticeEventRepository = mistakePracticeEventRepository;
        this.folderRepository = folderRepository;
        this.profileRepository = profileRepository;
        this.aiSettingsService = aiSettingsService;
    }

    @Transactional(readOnly = true)
    public OverviewResponse overview(Long userId) {
        List<KnowledgeChunk> chunks = allChunks(userId);
        RecentMetrics recent = recentMetrics(chunks, recentEvents(userId, RECENT_DAYS));
        LocalDate examDate = profileRepository.findByOwnerId(userId).map(profile -> profile.getExamDate()).orElse(null);
        Long daysUntilExam = examDate == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), examDate);
        return new OverviewResponse(
                chunks.size(),
                practicedCount(chunks),
                coverageRate(chunks),
                masteryAverage(chunks),
                chunks.stream().mapToLong(KnowledgeChunk::getCiteCount).sum(),
                chunks.stream().mapToLong(KnowledgeChunk::getCorrectHitCount).sum(),
                chunks.stream().mapToLong(KnowledgeChunk::getWrongHitCount).sum(),
                weakCount(chunks),
                unpracticedCount(chunks),
                masteredCount(chunks),
                mediumCount(chunks),
                highRiskCount(chunks),
                averageConfidenceLevel(chunks),
                recent.practiceCount(),
                recent.correctCount(),
                recent.wrongCount(),
                reviewDueCount(chunks),
                latestAccess(chunks),
                examDate,
                daysUntilExam
        );
    }

    @Transactional(readOnly = true)
    public List<SubjectProfileResponse> subjects(Long userId) {
        List<StudyFolder> folders = folderRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        List<StudyFolder> subjectFolders = folders.stream()
                .filter(StudyFolder::isSubjectFolder)
                .filter(folder -> folder.getParent() == null)
                .sorted(Comparator.comparingInt(StudyFolder::getSubjectOrder).thenComparing(StudyFolder::getCreatedAt))
                .toList();
        List<KnowledgeChunkEvent> recentEvents = recentEvents(userId, RECENT_DAYS);
        List<SubjectProfileResponse> responses = new ArrayList<>();
        for (StudyFolder subject : subjectFolders) {
            Set<Long> folderIds = descendantIds(subject, folders);
            List<KnowledgeChunk> chunks = chunkRepository.findExistingByFolderIdInAndOwnerId(folderIds, userId);
            RecentMetrics recent = recentMetrics(chunks, recentEvents);
            responses.add(new SubjectProfileResponse(
                    subject.getId(),
                    subject.getName(),
                    chunks.size(),
                    practicedCount(chunks),
                    coverageRate(chunks),
                    masteryAverage(chunks),
                    chunks.stream().mapToLong(KnowledgeChunk::getCiteCount).sum(),
                    weakCount(chunks),
                    unpracticedCount(chunks),
                    masteredCount(chunks),
                    mediumCount(chunks),
                    highRiskCount(chunks),
                    averageConfidenceLevel(chunks),
                    recent.practiceCount(),
                    recent.correctCount(),
                    recent.wrongCount(),
                    reviewPressure(chunks),
                    latestAccess(chunks)
            ));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<FileProfileResponse> files(Long userId) {
        return files(userId, null);
    }

    @Transactional(readOnly = true)
    public List<FileProfileResponse> files(Long userId, Long folderId) {
        Set<Long> scopedFolderIds = scopedFolderIds(userId, folderId);
        List<KnowledgeChunkEvent> recentEvents = recentEvents(userId, RECENT_DAYS);
        Map<Long, List<KnowledgeChunk>> byFile = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : allChunks(userId)) {
            if (!scopedFolderIds.isEmpty() && !scopedFolderIds.contains(chunk.getFolder().getId())) {
                continue;
            }
            byFile.computeIfAbsent(chunk.getFile().getId(), ignored -> new ArrayList<>()).add(chunk);
        }
        return byFile.entrySet().stream()
                .map(entry -> {
                    List<KnowledgeChunk> chunks = entry.getValue();
                    KnowledgeChunk first = chunks.get(0);
                    RecentMetrics recent = recentMetrics(chunks, recentEvents);
                    return new FileProfileResponse(
                            first.getFile().getId(),
                            first.getFile().getOriginalName(),
                            chunks.size(),
                            practicedCount(chunks),
                            coverageRate(chunks),
                            masteryAverage(chunks),
                            chunks.stream().mapToLong(KnowledgeChunk::getCiteCount).sum(),
                            weakCount(chunks),
                            unpracticedCount(chunks),
                            masteredCount(chunks),
                            mediumCount(chunks),
                            highRiskCount(chunks),
                            averageConfidenceLevel(chunks),
                            recent.practiceCount(),
                            recent.correctCount(),
                            recent.wrongCount(),
                            latestAccess(chunks)
                    );
                })
                .sorted(Comparator.comparingLong(FileProfileResponse::citeCount).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WeakChunkResponse> weakChunks(Long userId) {
        return allChunks(userId).stream()
                .filter(this::isWeakChunkCandidate)
                .map(chunk -> new WeakChunkResponse(
                        chunk.getId(),
                        chunk.getFile().getId(),
                        chunk.getFile().getOriginalName(),
                        chunk.getPageNumber(),
                        excerpt(chunk.getContent()),
                        chunk.getMasteryRate(),
                        chunk.getCorrectHitCount(),
                        chunk.getWrongHitCount(),
                        chunk.getCiteCount(),
                        confidenceLevel(chunk),
                        chunk.getLastPracticedAt(),
                        reviewPriority(chunk)
                ))
                .sorted(Comparator.comparingDouble(WeakChunkResponse::reviewPriority).reversed())
                .limit(50)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChunkSearchResponse> searchChunks(Long userId, Long folderId, Long fileId, String query, int limit) {
        Set<Long> folderIds = scopedFolderIds(userId, folderId);
        if (folderIds.isEmpty()) {
            return List.of();
        }
        List<String> terms = searchTerms(query);
        int normalizedLimit = Math.max(1, Math.min(limit, 50));
        return chunkRepository.findExistingByFolderIdInAndOwnerId(folderIds, userId).stream()
                .filter(chunk -> fileId == null || chunk.getFile().getId().equals(fileId))
                .filter(chunk -> chunk.getContent() != null && !chunk.getContent().isBlank())
                .sorted(Comparator
                        .comparingInt((KnowledgeChunk chunk) -> chunkScore(chunk, terms)).reversed()
                        .thenComparing(KnowledgeChunk::getLastAccessedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(chunk -> chunk.getFile().getUploadedAt(), Comparator.reverseOrder()))
                .limit(normalizedLimit)
                .map(this::toChunkSearchResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TrendPointResponse> trends(Long userId, int days) {
        return dailyActivity(userId, normalizeDays(days));
    }

    @Transactional(readOnly = true)
    public ActivityResponse activity(Long userId, int days) {
        int normalizedDays = normalizeDays(days);
        List<TrendPointResponse> daily = dailyActivity(userId, normalizedDays);
        List<KnowledgeChunkEvent> events = recentEvents(userId, normalizedDays);
        List<StudyFolder> folders = folderRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        Map<Long, StudyFolder> folderById = folders.stream().collect(java.util.stream.Collectors.toMap(StudyFolder::getId, folder -> folder));
        Map<Long, TrendAccumulator> bySubject = new LinkedHashMap<>();
        Map<Long, String> subjectNames = new LinkedHashMap<>();
        for (KnowledgeChunkEvent event : events) {
            StudyFolder subject = subjectForFolder(folderById.get(event.getFolderId()), folderById);
            if (subject == null) {
                continue;
            }
            TrendAccumulator accumulator = bySubject.computeIfAbsent(subject.getId(), ignored -> new TrendAccumulator());
            subjectNames.put(subject.getId(), subject.getName());
            if (event.getEventType() == KnowledgeChunkEventType.CITED) {
                accumulator.citationCount++;
            } else if (isPracticeEvent(event)) {
                accumulator.practiceCount++;
                if (Boolean.TRUE.equals(event.getCorrect())) {
                    accumulator.correctCount++;
                } else if (Boolean.FALSE.equals(event.getCorrect())) {
                    accumulator.wrongCount++;
                }
            }
        }
        List<SubjectActivityResponse> subjects = bySubject.entrySet().stream()
                .map(entry -> {
                    TrendAccumulator item = entry.getValue();
                    double score = item.practiceCount * 2.0 + item.citationCount + item.correctCount * 0.5;
                    return new SubjectActivityResponse(entry.getKey(), subjectNames.get(entry.getKey()), item.practiceCount,
                            item.correctCount, item.wrongCount, item.citationCount, score);
                })
                .sorted(Comparator.comparingDouble(SubjectActivityResponse::activityScore).reversed())
                .toList();
        return new ActivityResponse(normalizedDays, daily, subjects);
    }

    @Transactional(readOnly = true)
    public RiskResponse risk(Long userId, int days) {
        return risk(userId, days, null);
    }

    @Transactional(readOnly = true)
    public RiskResponse risk(Long userId, int days, Long folderId) {
        int normalizedDays = normalizeDays(days);
        RiskContext context = riskContext(userId, folderId);
        List<RiskChunk> risks = riskChunks(context, Instant.now());
        return new RiskResponse(
                normalizedDays,
                risks.stream().limit(10).map(RiskChunk::toRiskResponse).toList(),
                pressureTrend(context, normalizedDays),
                risks.stream().limit(30).map(RiskChunk::toBubbleResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public DiagnosisResponse diagnosis(Long userId, int days, boolean ai) {
        int normalizedDays = normalizeDays(days);
        ActivityResponse activity = activity(userId, normalizedDays);
        RiskContext context = riskContext(userId);
        boolean hasReliableData = context.chunks().stream().anyMatch(this::hasMediumConfidence);
        if (!hasReliableData) {
            String insufficient = "学习数据不足，暂时无法给出恰当的学习诊断。请先完成几次知识点练习或错题复盘，系统会在达到中等置信度后再参与评估。";
            return new DiagnosisResponse(false, false, DIAGNOSIS_CONFIDENCE_THRESHOLD, insufficient, "", List.of(), List.of());
        }
        List<RiskChunk> risks = riskChunks(context, Instant.now()).stream()
                .filter(risk -> hasMediumConfidence(risk.chunk()))
                .toList();
        List<SubjectProfileResponse> subjects = subjects(userId).stream()
                .filter(subject -> confidenceRank(subject.averageConfidenceLevel()) >= confidenceRank(DIAGNOSIS_CONFIDENCE_THRESHOLD))
                .toList();
        List<FileProfileResponse> files = files(userId).stream()
                .filter(file -> confidenceRank(file.averageConfidenceLevel()) >= confidenceRank(DIAGNOSIS_CONFIDENCE_THRESHOLD))
                .toList();
        Set<Long> reliableSubjectIds = subjects.stream()
                .map(SubjectProfileResponse::subjectFolderId)
                .collect(java.util.stream.Collectors.toSet());
        SubjectProfileResponse weakestSubject = subjects.stream()
                .filter(subject -> subject.practicedChunkCount() > 0)
                .min(Comparator.comparingDouble(SubjectProfileResponse::masteryRate))
                .orElse(null);
        SubjectActivityResponse mostActive = activity.subjects().stream()
                .filter(subject -> reliableSubjectIds.contains(subject.subjectFolderId()))
                .findFirst()
                .orElse(null);
        FileProfileResponse staleFile = files.stream()
                .filter(file -> file.lastAccessedAt() != null)
                .min(Comparator.comparing(FileProfileResponse::lastAccessedAt))
                .orElse(null);
        RiskChunk topRisk = risks.stream().findFirst().orElse(null);
        List<DiagnosisItemResponse> items = new ArrayList<>();
        items.add(new DiagnosisItemResponse("最薄弱学科", weakestSubject == null ? "暂无" : weakestSubject.subjectName(),
                weakestSubject == null ? "还没有足够练习数据。" : "掌握率 " + percent(weakestSubject.masteryRate()) + "，薄弱点 " + weakestSubject.weakChunkCount() + " 个。", "WARN"));
        items.add(new DiagnosisItemResponse("最活跃学科", mostActive == null ? "暂无" : mostActive.subjectName(),
                mostActive == null ? "近 " + normalizedDays + " 天还没有学科活动。" : "近 " + normalizedDays + " 天活动分 " + Math.round(mostActive.activityScore()) + "。", "OK"));
        items.add(new DiagnosisItemResponse("最长未复习教材", staleFile == null ? "暂无" : staleFile.fileName(),
                staleFile == null ? "教材访问记录不足。" : "上次访问 " + staleFile.lastAccessedAt() + "，建议补一次回看。", "WARN"));
        items.add(new DiagnosisItemResponse("最高风险知识点", topRisk == null ? "暂无" : topRisk.title(),
                topRisk == null ? "暂未发现高风险知识点。" : "风险分 " + Math.round(topRisk.riskScore()) + "，" + topRisk.reason(), topRisk == null ? "OK" : "DANGER"));
        List<ReviewSuggestionResponse> suggestions = diverseRisks(risks, 5).stream()
                .map(risk -> toSuggestion(risk, context))
                .toList();
        String summary = ruleSummary(items, suggestions, normalizedDays);
        String aiSummary = "";
        boolean aiAvailable = false;
        if (ai) {
            AiResult result = aiSummary(userId, summary, items, suggestions);
            aiSummary = result.summary();
            aiAvailable = result.available();
        }
        return new DiagnosisResponse(aiAvailable, true, DIAGNOSIS_CONFIDENCE_THRESHOLD, summary, aiSummary, items, suggestions);
    }

    @Transactional(readOnly = true)
    public DistributionResponse distribution(Long userId) {
        List<KnowledgeChunk> chunks = allChunks(userId);
        return new DistributionResponse(
                chunks.stream().filter(chunk -> chunk.getFeedbackCount() == 0).count(),
                weakCount(chunks),
                mediumCount(chunks),
                goodCount(chunks),
                masteredCount(chunks)
        );
    }

    private List<TrendPointResponse> dailyActivity(Long userId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days - 1L);
        Map<LocalDate, TrendAccumulator> byDate = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            byDate.put(startDate.plusDays(i), new TrendAccumulator());
        }
        Map<Long, KnowledgeChunk> chunksById = allChunks(userId).stream()
                .collect(java.util.stream.Collectors.toMap(KnowledgeChunk::getId, chunk -> chunk, (left, right) -> left));
        for (KnowledgeChunkEvent event : recentEvents(userId, days)) {
            LocalDate date = LocalDate.ofInstant(event.getCreatedAt(), ZoneId.systemDefault());
            TrendAccumulator accumulator = byDate.get(date);
            if (accumulator == null) {
                continue;
            }
            if (event.getEventType() == KnowledgeChunkEventType.CITED) {
                accumulator.citationCount++;
            } else if (isPracticeEvent(event)) {
                accumulator.practiceCount++;
                if (Boolean.TRUE.equals(event.getCorrect())) {
                    accumulator.correctCount++;
                } else if (Boolean.FALSE.equals(event.getCorrect())) {
                    accumulator.wrongCount++;
                }
                KnowledgeChunk chunk = chunksById.get(event.getChunkId());
                if (chunk != null && chunk.getMasteryRate() >= 0.85) {
                    accumulator.newlyMasteredChunkIds.add(chunk.getId());
                }
            }
        }
        for (MistakePracticeEvent event : recentMistakeEvents(userId, days)) {
            LocalDate date = LocalDate.ofInstant(event.getCreatedAt(), ZoneId.systemDefault());
            TrendAccumulator accumulator = byDate.get(date);
            if (accumulator != null) {
                accumulator.mistakePracticeCount++;
            }
        }
        return byDate.entrySet().stream()
                .map(entry -> new TrendPointResponse(
                        entry.getKey(),
                        entry.getValue().practiceCount,
                        entry.getValue().correctCount,
                        entry.getValue().wrongCount,
                        entry.getValue().citationCount,
                        entry.getValue().mistakePracticeCount,
                        entry.getValue().newlyMasteredChunkIds.size()
                ))
                .toList();
    }

    private List<ReviewPressurePointResponse> pressureTrend(RiskContext context, int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        List<ReviewPressurePointResponse> points = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = start.plusDays(i);
            Instant at = date.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();
            List<RiskChunk> risks = riskChunks(context, at);
            double average = risks.stream().mapToDouble(RiskChunk::riskScore).average().orElse(0.0);
            long due = risks.stream().filter(risk -> risk.riskScore() >= 65).count();
            long high = risks.stream().filter(risk -> risk.riskScore() >= 80).count();
            points.add(new ReviewPressurePointResponse(date, average, due, high));
        }
        return points;
    }

    private RiskContext riskContext(Long userId) {
        return riskContext(userId, null);
    }

    private RiskContext riskContext(Long userId, Long folderId) {
        List<KnowledgeChunk> chunks = folderId == null
                ? allChunks(userId)
                : chunkRepository.findExistingByFolderIdInAndOwnerId(scopedFolderIds(userId, folderId), userId);
        List<StudyFolder> folders = folderRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        Map<Long, StudyFolder> folderById = folders.stream()
                .collect(java.util.stream.Collectors.toMap(StudyFolder::getId, folder -> folder, (left, right) -> left));
        LocalDate examDate = profileRepository.findByOwnerId(userId).map(profile -> profile.getExamDate()).orElse(null);
        long daysUntilExam = examDate == null ? 120 : ChronoUnit.DAYS.between(LocalDate.now(), examDate);
        int maxCite = chunks.stream().mapToInt(KnowledgeChunk::getCiteCount).max().orElse(0);
        Map<Long, List<KnowledgeChunkEvent>> eventsByChunk = eventRepository.findByOwnerIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        userId, LocalDate.now().minusDays(60).atStartOfDay(ZoneId.systemDefault()).toInstant())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(KnowledgeChunkEvent::getChunkId));
        return new RiskContext(chunks, folderById, Math.max(0, daysUntilExam), maxCite, eventsByChunk);
    }

    private List<RiskChunk> riskChunks(RiskContext context, Instant at) {
        return context.chunks().stream()
                .map(chunk -> riskChunk(context, chunk, at))
                .sorted(Comparator.comparingDouble(RiskChunk::riskScore).reversed())
                .toList();
    }

    private RiskChunk riskChunk(RiskContext context, KnowledgeChunk chunk, Instant at) {
        double masteryRate = chunk.getMasteryRate();
        int feedbackCount = chunk.getFeedbackCount();
        double masteryRisk = feedbackCount == 0 ? 0.55 : 1.0 - masteryRate;
        double wrongRisk = (chunk.getWrongHitCount() + 1.2) / (chunk.getCorrectHitCount() + chunk.getWrongHitCount() + 3.0);
        double attentionRisk = Math.log1p(chunk.getCiteCount()) / Math.log1p(context.maxCite() + 1.0);
        double examUrgency = clamp(1.0 - context.daysUntilExam() / 180.0, 0.15, 1.0);
        double baseInterval = context.daysUntilExam() <= 30 ? 5.0 : context.daysUntilExam() <= 90 ? 10.0 : 14.0;
        double targetInterval = baseInterval * (0.75 + masteryRate);
        Instant lastPractice = lastPracticeBefore(chunk, context.eventsByChunk().get(chunk.getId()), at);
        double daysSincePractice = lastPractice == null ? targetInterval + 2.0 : Math.max(0.0, Duration.between(lastPractice, at).toDays());
        double forgettingRisk = clamp(daysSincePractice / targetInterval, 0.0, 1.0);
        double confidencePenalty = feedbackCount < 3 ? 0.08 : 0.0;
        double riskScore = clamp(100.0 * (0.32 * masteryRisk
                + 0.22 * forgettingRisk
                + 0.18 * wrongRisk
                + 0.14 * attentionRisk
                + 0.14 * examUrgency
                + confidencePenalty), 0.0, 100.0);
        StudyFolder subject = subjectForFolder(chunk.getFolder(), context.folderById());
        return new RiskChunk(chunk, subject == null ? "" : subject.getName(), titleFor(chunk), excerpt(chunk.getContent()),
                riskScore, forgettingRisk, lastPractice, riskReason(chunk, riskScore, forgettingRisk));
    }

    private ReviewSuggestionResponse toSuggestion(RiskChunk risk, RiskContext context) {
        KnowledgeChunk chunk = risk.chunk();
        int minutes = risk.riskScore() >= 80 ? 45 : 30;
        LocalDate startDate = LocalDate.now();
        LocalTime startTime = LocalTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1);
        if (startTime.getHour() >= 22) {
            startDate = startDate.plusDays(1);
            startTime = LocalTime.of(9, 0);
        }
        LocalTime endTime = startTime.plusMinutes(minutes);
        String title = "复习：" + risk.title();
        String description = risk.reason() + " 资料：" + chunk.getFile().getOriginalName() + "，第 " + chunk.getPageNumber()
                + " 页。摘要：" + risk.excerpt();
        StudyPlanPayload payload = new StudyPlanPayload(
                title.length() > 120 ? title.substring(0, 120) : title,
                risk.subjectName().isBlank() ? null : risk.subjectName(),
                description.length() > 800 ? description.substring(0, 800) : description,
                "REVIEW",
                startDate,
                startTime.toString(),
                endTime.toString(),
                risk.riskScore() >= 80 ? "HIGH" : "MEDIUM",
                "TODO"
        );
        return new ReviewSuggestionResponse(chunk.getId(), chunk.getFile().getId(), risk.title(), risk.subjectName(),
                chunk.getFile().getOriginalName(), chunk.getPageNumber(), risk.excerpt(), risk.reason(), risk.riskScore(),
                minutes, payload);
    }

    private List<RiskChunk> diverseRisks(List<RiskChunk> risks, int limit) {
        Map<String, RiskChunk> byFilePage = new LinkedHashMap<>();
        for (RiskChunk risk : risks) {
            KnowledgeChunk chunk = risk.chunk();
            String key = chunk.getFile().getId() + ":" + chunk.getPageNumber();
            byFilePage.putIfAbsent(key, risk);
            if (byFilePage.size() >= limit) {
                break;
            }
        }
        if (byFilePage.size() >= limit) {
            return byFilePage.values().stream().limit(limit).toList();
        }
        List<RiskChunk> result = new ArrayList<>(byFilePage.values());
        Set<Long> used = result.stream()
                .map(item -> item.chunk().getId())
                .collect(java.util.stream.Collectors.toSet());
        for (RiskChunk risk : risks) {
            if (used.add(risk.chunk().getId())) {
                result.add(risk);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private String ruleSummary(List<DiagnosisItemResponse> items, List<ReviewSuggestionResponse> suggestions, int days) {
        String first = suggestions.isEmpty() ? "当前没有明显高风险知识点。" : "近 " + days + " 天建议优先处理 " + suggestions.size() + " 个高风险知识点。";
        String second = items.stream()
                .map(item -> item.label() + "：" + item.value())
                .collect(java.util.stream.Collectors.joining("；"));
        String third = suggestions.isEmpty() ? "可以先从未评估知识点开始补一次练习。" : "今日行动：" + suggestions.get(0).title() + "。";
        return first + "\n" + second + "\n" + third;
    }

    private AiResult aiSummary(Long userId, String summary, List<DiagnosisItemResponse> items, List<ReviewSuggestionResponse> suggestions) {
        var settings = aiSettingsService.get(userId);
        if (settings.chatApiKey() == null || settings.chatApiKey().isBlank()) {
            return new AiResult(false, "");
        }
        try {
            String prompt = "你是严谨的考研学习诊断老师。请基于下面的规则诊断，输出三段中文：当前状态、主要风险、今日行动。不要编造资料内容。\n\n"
                    + "规则总结：\n" + summary + "\n\n诊断项：\n"
                    + items.stream().map(item -> item.label() + ":" + item.value() + "," + item.detail()).collect(java.util.stream.Collectors.joining("\n"))
                    + "\n\n建议：\n"
                    + suggestions.stream().map(item -> item.title() + " 风险分:" + Math.round(item.riskScore()) + " 原因:" + item.reason()).collect(java.util.stream.Collectors.joining("\n"));
            String content = callModel(settings, prompt);
            return new AiResult(content != null && !content.isBlank(), content == null ? "" : content.trim());
        } catch (Exception ex) {
            return new AiResult(false, "");
        }
    }

    private String callModel(com.example.exam.dto.AiSettingsDtos.AiSettingsResponse settings, String prompt) throws Exception {
        String endpoint = normalizeChatEndpoint(settings.chatEndpoint());
        String body = mapper.writeValueAsString(Map.of(
                "model", settings.chatModel(),
                "temperature", 0.2,
                "max_tokens", 700,
                "messages", List.of(
                        Map.of("role", "system", "content", "你只输出学习诊断，不输出 JSON。"),
                        Map.of("role", "user", "content", prompt)
                )
        ));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.chatApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return "";
        }
        JsonNode root = mapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private String normalizeChatEndpoint(String endpoint) {
        String value = endpoint == null || endpoint.isBlank() ? AiSettingsService.DEFAULT_CHAT_ENDPOINT : endpoint.trim();
        if (value.endsWith("/chat/completions")) {
            return value;
        }
        return value.endsWith("/") ? value + "chat/completions" : value + "/chat/completions";
    }

    private List<KnowledgeChunk> allChunks(Long userId) {
        List<Long> folderIds = folderRepository.findByOwnerIdOrderByCreatedAtDesc(userId).stream()
                .map(StudyFolder::getId)
                .toList();
        if (folderIds.isEmpty()) {
            return List.of();
        }
        return chunkRepository.findExistingByFolderIdInAndOwnerId(folderIds, userId);
    }

    private int normalizeDays(int days) {
        return Math.max(1, Math.min(days, 60));
    }

    private List<MistakePracticeEvent> recentMistakeEvents(Long userId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(Math.max(1, days) - 1L);
        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        return mistakePracticeEventRepository.findByOwnerIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(userId, start);
    }

    private Set<Long> scopedFolderIds(Long userId, Long folderId) {
        List<StudyFolder> folders = folderRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        if (folderId == null) {
            return new HashSet<>(folders.stream().map(StudyFolder::getId).toList());
        }
        StudyFolder root = folders.stream()
                .filter(folder -> folder.getId().equals(folderId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Folder not found or access denied"));
        return descendantIds(root, folders);
    }

    private StudyFolder subjectForFolder(StudyFolder folder, Map<Long, StudyFolder> folderById) {
        StudyFolder current = folder;
        while (current != null) {
            if (current.isSubjectFolder() && current.getParent() == null) {
                return current;
            }
            current = current.getParent() == null ? null : folderById.get(current.getParent().getId());
        }
        return null;
    }

    private ChunkSearchResponse toChunkSearchResponse(KnowledgeChunk chunk) {
        return new ChunkSearchResponse(
                chunk.getId(),
                chunk.getFile().getId(),
                chunk.getFile().getOriginalName(),
                chunk.getPageNumber(),
                excerpt(chunk.getContent()),
                chunk.getMasteryRate(),
                chunk.getCiteCount(),
                chunk.getCorrectHitCount(),
                chunk.getWrongHitCount(),
                confidenceLevel(chunk),
                chunk.getLastPracticedAt()
        );
    }

    private List<String> searchTerms(String query) {
        String normalized = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT).trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        Set<String> terms = new HashSet<>();
        terms.add(normalized);
        for (String term : normalized.split("\\s+|,|\\.|;|:|，|。|；|：|、|\\?|？|!|！")) {
            if (!term.isBlank()) {
                terms.add(term);
            }
        }
        String compact = normalized.replaceAll("\\s+", "");
        for (int i = 0; i + 2 <= compact.length(); i++) {
            terms.add(compact.substring(i, i + 2));
        }
        return terms.stream().filter(term -> !term.isBlank()).toList();
    }

    private int chunkScore(KnowledgeChunk chunk, List<String> terms) {
        if (terms.isEmpty()) {
            return chunk.getCiteCount() + chunk.getFeedbackCount();
        }
        String content = chunk.getContent() == null ? "" : chunk.getContent().toLowerCase(java.util.Locale.ROOT);
        String fileName = chunk.getFile().getOriginalName() == null ? "" : chunk.getFile().getOriginalName().toLowerCase(java.util.Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (content.contains(term)) {
                score += Math.max(1, term.length());
            }
            if (fileName.contains(term)) {
                score += 3;
            }
        }
        return score;
    }

    private Set<Long> descendantIds(StudyFolder root, List<StudyFolder> folders) {
        Set<Long> ids = new HashSet<>();
        ids.add(root.getId());
        boolean changed;
        do {
            changed = false;
            for (StudyFolder folder : folders) {
                StudyFolder parent = folder.getParent();
                if (parent != null && ids.contains(parent.getId()) && ids.add(folder.getId())) {
                    changed = true;
                }
            }
        } while (changed);
        return ids;
    }

    private long practicedCount(List<KnowledgeChunk> chunks) {
        return chunks.stream().filter(chunk -> chunk.getFeedbackCount() > 0).count();
    }

    private Instant lastPracticeBefore(KnowledgeChunk chunk, List<KnowledgeChunkEvent> events, Instant at) {
        Instant last = chunk.getLastPracticedAt();
        if (last != null && last.isAfter(at)) {
            last = null;
        }
        if (events == null) {
            return last;
        }
        for (KnowledgeChunkEvent event : events) {
            if (!event.getCreatedAt().isAfter(at) && isPracticeEvent(event)) {
                last = event.getCreatedAt();
            }
        }
        return last;
    }

    private String titleFor(KnowledgeChunk chunk) {
        String content = chunk.getContent() == null ? "" : chunk.getContent()
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!content.isBlank()) {
            String[] parts = content.split("(?<=。)|(?<=；)|(?<=;)|(?<=：)|(?<=:)", 2);
            String title = parts[0].replaceAll("[。；;：:]$", "").trim();
            if (!title.isBlank()) {
                return title.length() > 32 ? title.substring(0, 32) + "..." : title;
            }
        }
        return chunk.getFile().getOriginalName() + " 第 " + chunk.getPageNumber() + " 页";
    }

    private String riskReason(KnowledgeChunk chunk, double riskScore, double forgettingRisk) {
        List<String> reasons = new ArrayList<>();
        if (chunk.getMasteryRate() < 0.55) {
            reasons.add("掌握率偏低");
        }
        if (forgettingRisk >= 0.75) {
            reasons.add("较久未复习");
        }
        if (chunk.getWrongHitCount() > chunk.getCorrectHitCount()) {
            reasons.add("错题反馈偏多");
        }
        if (chunk.getCiteCount() >= 3) {
            reasons.add("问答引用频繁");
        }
        if (reasons.isEmpty()) {
            reasons.add(riskScore >= 65 ? "临近复习窗口" : "建议巩固");
        }
        return String.join("、", reasons);
    }

    private String percent(double value) {
        return Math.round(value * 100) + "%";
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double coverageRate(List<KnowledgeChunk> chunks) {
        return chunks.isEmpty() ? 0.0 : practicedCount(chunks) / (double) chunks.size();
    }

    private double masteryAverage(List<KnowledgeChunk> chunks) {
        return chunks.stream()
                .filter(chunk -> chunk.getFeedbackCount() > 0)
                .mapToDouble(KnowledgeChunk::getMasteryRate)
                .average()
                .orElse(0.0);
    }

    private long weakCount(List<KnowledgeChunk> chunks) {
        return chunks.stream()
                .filter(this::isWeakChunkCandidate)
                .count();
    }

    private boolean isWeakChunkCandidate(KnowledgeChunk chunk) {
        if (chunk.getFeedbackCount() < MIN_DIAGNOSIS_FEEDBACK_COUNT) {
            return false;
        }
        if (chunk.getMasteryRate() < WEAK_MASTERY_THRESHOLD) {
            return true;
        }
        return chunk.getMasteryRate() < BORDERLINE_WEAK_MASTERY_THRESHOLD
                && chunk.getWrongHitCount() > chunk.getCorrectHitCount();
    }

    private long unpracticedCount(List<KnowledgeChunk> chunks) {
        return chunks.stream().filter(chunk -> chunk.getFeedbackCount() == 0).count();
    }

    private long mediumCount(List<KnowledgeChunk> chunks) {
        return chunks.stream()
                .filter(chunk -> chunk.getFeedbackCount() > 0)
                .filter(chunk -> chunk.getMasteryRate() >= 0.4 && chunk.getMasteryRate() < 0.7)
                .count();
    }

    private long goodCount(List<KnowledgeChunk> chunks) {
        return chunks.stream()
                .filter(chunk -> chunk.getFeedbackCount() > 0)
                .filter(chunk -> chunk.getMasteryRate() >= 0.7 && chunk.getMasteryRate() < 0.85)
                .count();
    }

    private long masteredCount(List<KnowledgeChunk> chunks) {
        return chunks.stream()
                .filter(chunk -> chunk.getFeedbackCount() > 0)
                .filter(chunk -> chunk.getMasteryRate() >= 0.85)
                .count();
    }

    private long highRiskCount(List<KnowledgeChunk> chunks) {
        return chunks.stream()
                .filter(chunk -> chunk.getFeedbackCount() > 0)
                .filter(chunk -> reviewPriority(chunk) >= 0.75)
                .count();
    }

    private long reviewDueCount(List<KnowledgeChunk> chunks) {
        return chunks.stream()
                .filter(chunk -> chunk.getFeedbackCount() > 0)
                .filter(chunk -> reviewPriority(chunk) >= 0.6)
                .count();
    }

    private double reviewPressure(List<KnowledgeChunk> chunks) {
        return chunks.stream()
                .filter(chunk -> chunk.getFeedbackCount() > 0)
                .mapToDouble(this::reviewPriority)
                .average()
                .orElse(0.0);
    }

    private String confidenceLevel(KnowledgeChunk chunk) {
        int feedbackCount = chunk.getFeedbackCount();
        if (feedbackCount == 0) {
            return "NONE";
        }
        if (feedbackCount <= 2) {
            return "LOW";
        }
        if (feedbackCount <= 5) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private boolean hasMediumConfidence(KnowledgeChunk chunk) {
        return chunk.getFeedbackCount() >= MIN_DIAGNOSIS_FEEDBACK_COUNT;
    }

    private int confidenceRank(String level) {
        return switch (level == null ? "NONE" : level) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            default -> 0;
        };
    }

    private String averageConfidenceLevel(List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) {
            return "NONE";
        }
        double average = chunks.stream()
                .mapToInt(chunk -> switch (confidenceLevel(chunk)) {
                    case "LOW" -> 1;
                    case "MEDIUM" -> 2;
                    case "HIGH" -> 3;
                    default -> 0;
                })
                .average()
                .orElse(0.0);
        if (average < 0.5) {
            return "NONE";
        }
        if (average < 1.5) {
            return "LOW";
        }
        if (average < 2.5) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private List<KnowledgeChunkEvent> recentEvents(Long userId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(Math.max(1, days) - 1L);
        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        return eventRepository.findByOwnerIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(userId, start);
    }

    private RecentMetrics recentMetrics(List<KnowledgeChunk> chunks, List<KnowledgeChunkEvent> events) {
        Set<Long> chunkIds = chunks.stream().map(KnowledgeChunk::getId).collect(java.util.stream.Collectors.toSet());
        long practiceCount = 0;
        long correctCount = 0;
        long wrongCount = 0;
        for (KnowledgeChunkEvent event : events) {
            if (!chunkIds.contains(event.getChunkId()) || !isPracticeEvent(event)) {
                continue;
            }
            practiceCount++;
            if (Boolean.TRUE.equals(event.getCorrect())) {
                correctCount++;
            } else if (Boolean.FALSE.equals(event.getCorrect())) {
                wrongCount++;
            }
        }
        return new RecentMetrics(practiceCount, correctCount, wrongCount);
    }

    private boolean isPracticeEvent(KnowledgeChunkEvent event) {
        return event.getEventType() == KnowledgeChunkEventType.FEEDBACK_CLEAR
                || event.getEventType() == KnowledgeChunkEventType.FEEDBACK_FORGOT
                || event.getEventType() == KnowledgeChunkEventType.PRACTICE_CORRECT
                || event.getEventType() == KnowledgeChunkEventType.PRACTICE_WRONG;
    }

    private double reviewPriority(KnowledgeChunk chunk) {
        int citeBase = Math.max(1, chunk.getCiteCount());
        double attentionScore = Math.log1p(chunk.getCiteCount()) / Math.log1p(citeBase);
        double daysSincePractice = chunk.getLastPracticedAt() == null
                ? 14.0
                : Math.max(0.0, Duration.between(chunk.getLastPracticedAt(), Instant.now()).toDays());
        double forgetRisk = Math.min(1.0, daysSincePractice / 14.0);
        return (1.0 - chunk.getMasteryRate()) * 0.6 + forgetRisk * 0.25 + attentionScore * 0.15;
    }

    private Instant latestAccess(List<KnowledgeChunk> chunks) {
        return chunks.stream()
                .map(KnowledgeChunk::getLastAccessedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private String excerpt(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        return normalized.length() > 180 ? normalized.substring(0, 180) + "..." : normalized;
    }

    private record RecentMetrics(long practiceCount, long correctCount, long wrongCount) {
    }

    private record RiskContext(
            List<KnowledgeChunk> chunks,
            Map<Long, StudyFolder> folderById,
            long daysUntilExam,
            int maxCite,
            Map<Long, List<KnowledgeChunkEvent>> eventsByChunk
    ) {
    }

    private record RiskChunk(
            KnowledgeChunk chunk,
            String subjectName,
            String title,
            String excerpt,
            double riskScore,
            double forgettingRisk,
            Instant lastPracticeAt,
            String reason
    ) {
        private RiskChunkResponse toRiskResponse() {
            return new RiskChunkResponse(
                    chunk.getId(),
                    chunk.getFile().getId(),
                    chunk.getFile().getOriginalName(),
                    chunk.getPageNumber(),
                    title,
                    excerpt,
                    chunk.getMasteryRate(),
                    chunk.getCorrectHitCount(),
                    chunk.getWrongHitCount(),
                    chunk.getCiteCount(),
                    confidenceLevelStatic(chunk),
                    lastPracticeAt,
                    riskScore,
                    forgettingRisk
            );
        }

        private RiskBubbleResponse toBubbleResponse() {
            return new RiskBubbleResponse(chunk.getId(), title, chunk.getMasteryRate(), chunk.getCiteCount(), chunk.getWrongHitCount(), riskScore);
        }

        private static String confidenceLevelStatic(KnowledgeChunk chunk) {
            int feedbackCount = chunk.getFeedbackCount();
            if (feedbackCount == 0) return "NONE";
            if (feedbackCount <= 2) return "LOW";
            if (feedbackCount <= 5) return "MEDIUM";
            return "HIGH";
        }
    }

    private record AiResult(boolean available, String summary) {
    }

    private static class TrendAccumulator {
        private long practiceCount;
        private long correctCount;
        private long wrongCount;
        private long citationCount;
        private long mistakePracticeCount;
        private final Set<Long> newlyMasteredChunkIds = new HashSet<>();
    }
}
