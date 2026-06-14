package com.example.exam.service.chunking;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于文本结构和字符二元组连续性的切片参考实现。
 *
 */
public final class LexicalContinuityChunker {
    private static final int MIN_CHUNK_SIZE = 500;
    private static final int TARGET_CHUNK_SIZE = 800;
    private static final int PREFERRED_MAX_CHUNK_SIZE = 1000;
    private static final int MAX_CHUNK_SIZE = 1100;
    private static final int CONTEXT_WINDOW_SIZE = 400;

    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(#{1,6}\\s+.+|第[一二三四五六七八九十百千万0-9]+[章节篇部分].*"
                    + "|[一二三四五六七八九十]+[、.．].+|\\(?[一二三四五六七八九十]+\\).+"
                    + "|\\d+(?:\\.\\d+){0,3}[、.．\\s]+.+|考点\\s*[:：]?.+|知识点\\s*[:：]?.+)$"
    );
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^。！？；.!?;]+[。！？；.!?;]?");
    private static final Pattern SOFT_BREAK_PATTERN = Pattern.compile("[^，,、：:]+[，,、：:]?");

    private LexicalContinuityChunker() {
    }

    /**
     * 将文本切分为结构和词汇连续性较强的片段。
     *
     * @param text 已抽取并清洗的资料文本
     * @return 切片内容及其在原文本中的近似起始偏移
     */
    public static List<Chunk> split(String text) {
        List<TextUnit> units = splitIntoTextUnits(text);
        if (units.isEmpty()) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        int chunkStartIndex = 0;

        while (chunkStartIndex < units.size()) {
            int hardEndExclusive = findHardEnd(units, chunkStartIndex);

            // 剩余内容没有超过最大长度时，直接作为最后一个片段。
            if (hardEndExclusive >= units.size()) {
                addChunk(chunks, units.subList(chunkStartIndex, units.size()));
                break;
            }

            BoundaryCandidate best = findBestBoundary(
                    units,
                    chunkStartIndex,
                    hardEndExclusive
            );

            // 没有合适候选点时，在最大长度前最后一个完整文本单元处切分。
            int boundaryIndex = best == null
                    ? Math.max(chunkStartIndex + 1, hardEndExclusive)
                    : best.unitIndex();

            addChunk(chunks, units.subList(chunkStartIndex, boundaryIndex));
            chunkStartIndex = boundaryIndex;
        }

        return mergeShortTail(chunks);
    }

    /**
     * 找到不超过最大切片长度的最远文本单元位置。
     */
    private static int findHardEnd(List<TextUnit> units, int startIndex) {
        int endExclusive = startIndex;
        int length = 0;
        while (endExclusive < units.size()) {
            int nextLength = units.get(endExclusive).text().length();
            if (length > 0 && length + nextLength > MAX_CHUNK_SIZE) {
                break;
            }
            length += nextLength;
            endExclusive++;
        }
        return endExclusive;
    }

    /**
     * 在当前最大长度范围内选择综合得分最高的边界。
     */
    private static BoundaryCandidate findBestBoundary(
            List<TextUnit> units,
            int chunkStartIndex,
            int searchEndExclusive
    ) {
        BoundaryCandidate best = null;
        int chunkLength = 0;
        boolean containsBody = false;

        for (int i = chunkStartIndex; i < searchEndExclusive; i++) {
            TextUnit currentUnit = units.get(i);
            chunkLength += currentUnit.text().length();
            containsBody = containsBody || !currentUnit.heading();

            int boundaryIndex = i + 1;
            if (boundaryIndex >= units.size()) {
                break;
            }

            TextUnit nextUnit = units.get(boundaryIndex);
            boolean headingBoundary = nextUnit.heading() && containsBody;
            //是否包含正文标题
            if (chunkLength < MIN_CHUNK_SIZE && !headingBoundary) {
                continue;
            }

            double score = boundaryScore(
                    units,
                    chunkStartIndex,
                    boundaryIndex,
                    chunkLength
            );
            BoundaryCandidate candidate = new BoundaryCandidate(boundaryIndex, score);
            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }

            // 超过优选上限后保留当前最佳点，不再鼓励继续增长。
            if (chunkLength >= PREFERRED_MAX_CHUNK_SIZE) {
                break;
            }
        }
        return best;
    }

    /**
     * 综合词汇不连续度、结构边界、长度和前文依赖关系计算边界分。
     */
    private static double boundaryScore(
            List<TextUnit> units,
            int chunkStartIndex,
            int boundaryIndex,
            int chunkLength
    ) {
        TextUnit nextUnit = units.get(boundaryIndex);
        String left = leftContext(units, chunkStartIndex, boundaryIndex);
        String right = rightContext(units, boundaryIndex);

        double similarity = lexicalSimilarity(left, right);
        double discontinuityScore = (1.0 - similarity) * 35.0;

        double structureScore;
        if (nextUnit.heading()) {
            structureScore = 25.0;
        } else if (nextUnit.paragraphStart()) {
            structureScore = 15.0;
        } else {
            structureScore = 5.0;
        }

        double distance = Math.abs(chunkLength - TARGET_CHUNK_SIZE);
        double lengthScore = Math.max(
                0.0,
                20.0 * (1.0 - distance / TARGET_CHUNK_SIZE)
        );
        double dependencyPenalty = startsWithContextDependency(nextUnit.text())
                ? 20.0
                : 0.0;

        return discontinuityScore
                + structureScore
                + lengthScore
                - dependencyPenalty;
    }

    /**
     * 计算两个局部文本窗口的字符二元组 Jaccard 相似度。
     */
    static double lexicalSimilarity(String leftText, String rightText) {
        Set<String> leftBigrams = characterBigrams(leftText);
        Set<String> rightBigrams = characterBigrams(rightText);

        if (leftBigrams.isEmpty() && rightBigrams.isEmpty()) {
            return 1.0;
        }
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(leftBigrams);
        intersection.retainAll(rightBigrams);

        Set<String> union = new HashSet<>(leftBigrams);
        union.addAll(rightBigrams);
        return intersection.size() / (double) union.size();
    }

    /**
     * 生成字符二元组，不依赖中文分词器或外部模型。
     */
    static Set<String> characterBigrams(String text) {
        String normalized = text == null
                ? ""
                : text.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{IsHan}a-z0-9]", "");

        Set<String> bigrams = new HashSet<>();
        for (int i = 0; i + 1 < normalized.length(); i++) {
            bigrams.add(normalized.substring(i, i + 2));
        }
        return bigrams;
    }

    /**
     * 读取候选边界左侧的局部窗口，避免整段文本稀释边界附近主题。
     */
    private static String leftContext(
            List<TextUnit> units,
            int startIndex,
            int boundaryIndex
    ) {
        StringBuilder result = new StringBuilder();
        for (int i = boundaryIndex - 1; i >= startIndex; i--) {
            result.insert(0, units.get(i).text());
            if (result.length() >= CONTEXT_WINDOW_SIZE) {
                return result.substring(result.length() - CONTEXT_WINDOW_SIZE);
            }
        }
        return result.toString();
    }

    /**
     * 读取候选边界右侧的局部窗口。
     */
    private static String rightContext(List<TextUnit> units, int boundaryIndex) {
        StringBuilder result = new StringBuilder();
        for (int i = boundaryIndex; i < units.size(); i++) {
            result.append(units.get(i).text());
            if (result.length() >= CONTEXT_WINDOW_SIZE) {
                return result.substring(0, CONTEXT_WINDOW_SIZE);
            }
        }
        return result.toString();
    }

    /**
     * 这些词通常表示并列、总结、因果或指代关系，应降低切片意愿。
     */
    private static boolean startsWithContextDependency(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.stripLeading();
        return normalized.startsWith("首先")
                || normalized.startsWith("其次")
                || normalized.startsWith("再次")
                || normalized.startsWith("最后")
                || normalized.startsWith("因此")
                || normalized.startsWith("所以")
                || normalized.startsWith("由此可见")
                || normalized.startsWith("该")
                || normalized.startsWith("这")
                || normalized.startsWith("其")
                || normalized.startsWith("上述")
                || normalized.startsWith("前者")
                || normalized.startsWith("后者");
    }

    /**
     * 将原始文本拆成标题或完整句子级单元，并保留近似字符偏移。
     */
    private static List<TextUnit> splitIntoTextUnits(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<TextUnit> units = new ArrayList<>();
        int offset = 0;
        boolean paragraphStart = true;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                paragraphStart = true;
                offset += line.length() + 1;
                continue;
            }

            int lineContentOffset = offset + Math.max(0, line.indexOf(trimmed));
            boolean heading = isHeading(trimmed);
            List<String> pieces = heading
                    ? List.of(trimmed)
                    : splitSentences(trimmed);
            int searchFrom = 0;

            for (String piece : pieces) {
                int localIndex = trimmed.indexOf(piece, searchFrom);
                int pieceOffset = lineContentOffset + Math.max(0, localIndex);
                units.add(new TextUnit(piece, heading, paragraphStart, pieceOffset));
                searchFrom = localIndex < 0
                        ? searchFrom
                        : localIndex + piece.length();
                paragraphStart = false;
            }
            offset += line.length() + 1;
        }
        return units;
    }

    private static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isBlank()) {
                sentences.addAll(splitOversizedSentence(sentence));
            }
        }
        if (sentences.isEmpty() && !text.isBlank()) {
            sentences.addAll(splitOversizedSentence(text.trim()));
        }
        return sentences;
    }

    /**
     * 超长单句先按逗号、顿号和冒号拆分，最后才按固定长度兜底。
     */
    private static List<String> splitOversizedSentence(String sentence) {
        if (sentence.length() <= MAX_CHUNK_SIZE) {
            return List.of(sentence);
        }

        List<String> clauses = new ArrayList<>();
        Matcher matcher = SOFT_BREAK_PATTERN.matcher(sentence);
        StringBuilder current = new StringBuilder();

        while (matcher.find()) {
            String clause = matcher.group().trim();
            if (clause.isBlank()) {
                continue;
            }
            if (!current.isEmpty()
                    && current.length() + clause.length() > MAX_CHUNK_SIZE) {
                clauses.add(current.toString().trim());
                current.setLength(0);
            }
            if (clause.length() > MAX_CHUNK_SIZE) {
                clauses.addAll(splitByLength(clause));
            } else {
                current.append(clause);
            }
        }
        if (!current.isEmpty()) {
            clauses.add(current.toString().trim());
        }
        return clauses.isEmpty() ? splitByLength(sentence) : clauses;
    }

    private static List<String> splitByLength(String text) {
        List<String> parts = new ArrayList<>();
        for (int start = 0; start < text.length(); start += MAX_CHUNK_SIZE) {
            parts.add(text.substring(
                    start,
                    Math.min(start + MAX_CHUNK_SIZE, text.length())
            ).trim());
        }
        return parts;
    }

    private static boolean isHeading(String text) {
        return text.length() <= 120 && HEADING_PATTERN.matcher(text).matches();
    }

    private static void addChunk(List<Chunk> chunks, List<TextUnit> units) {
        if (units.isEmpty()) {
            return;
        }
        String content = toChunkContent(units);
        if (!content.isBlank()) {
            chunks.add(new Chunk(content, units.get(0).startOffset()));
        }
    }

    /**
     * 合并过短尾段，避免文档末尾产生缺乏上下文的小片段。
     */
    private static List<Chunk> mergeShortTail(List<Chunk> chunks) {
        if (chunks.size() < 2) {
            return chunks;
        }

        Chunk tail = chunks.get(chunks.size() - 1);
        Chunk previous = chunks.get(chunks.size() - 2);
        if (tail.content().length() >= MIN_CHUNK_SIZE
                || previous.content().length() + tail.content().length()
                > MAX_CHUNK_SIZE + MIN_CHUNK_SIZE) {
            return chunks;
        }

        List<Chunk> merged = new ArrayList<>(chunks.subList(0, chunks.size() - 2));
        merged.add(new Chunk(
                previous.content() + "\n" + tail.content(),
                previous.startOffset()
        ));
        return merged;
    }

    private static String toChunkContent(List<TextUnit> units) {
        StringBuilder builder = new StringBuilder();
        for (TextUnit unit : units) {
            if (builder.isEmpty()) {
                builder.append(unit.text());
            } else if (unit.heading() || unit.paragraphStart()) {
                builder.append('\n').append(unit.text());
            } else {
                builder.append(unit.text());
            }
        }
        return builder.toString().trim();
    }

    /**
     * 对外返回的切片结果，不依赖项目中的 JPA 实体。
     */
    public record Chunk(String content, int startOffset) {
    }

    private record TextUnit(
            String text,
            boolean heading,
            boolean paragraphStart,
            int startOffset
    ) {
    }

    private record BoundaryCandidate(int unitIndex, double score) {
    }
}
