# 基于句子边界与语义连续性的知识切片方案

## 1. 设计背景

原系统中的知识切片采用固定字符窗口策略：每个 `KnowledgeChunk` 约 800 个字符，相邻片段重叠 120 个字符。该方式实现简单，但存在两个问题：

1. 容易从句子中间切断内容，导致片段语义不完整。
2. 后续如果基于命中次数统计知识掌握情况，固定窗口更容易反映“文本位置热度”，而不是“知识点掌握情况”。

因此，系统将切片方案调整为“句子级切分 + 语义连续性合并 + 句子边界兜底”的自适应切片策略，使每个 `KnowledgeChunk` 尽量对应一个相对完整的知识点解释单元。

## 2. 设计目标

本方案主要服务于两个目标：

1. 提高 RAG 问答检索质量  
   切片边界尽量落在句子、段落或标题位置，避免破坏概念定义、例子说明和结论表达。

2. 支持个人知识掌握画像  
   让每个 chunk 更接近“知识点单元”，后续可根据问答、错题、复习等行为的命中情况，统计教材知识点覆盖率、薄弱知识点和掌握情况。

## 3. 核心思路

系统不再直接按照固定字符长度截取全文，而是先把文本拆成更小的句子级单元，再根据语义连续性与长度约束合并为知识片段。

整体流程如下：

```text
原始抽取文本
  ↓
文本清洗与换行规范化
  ↓
识别标题、段落和句子
  ↓
生成句子级 TextUnit
  ↓
按语义连续性合并
  ↓
超长内容按句子或软标点拆分
  ↓
生成 KnowledgeChunk
  ↓
写入数据库并重建检索索引
```

## 4. 切片规则

### 4.1 句子级基本单元

系统优先按句子结束符切分文本，常见边界包括：

```text
。！？；.!?;
```

这样可以保证常规中文、英文句子不会被硬切断。

如果单句过长，系统不会立即按固定长度截断，而是优先按软边界继续拆分，例如：

```text
，,、：:
```

只有当句子或子句仍然过长时，才使用最大长度兜底拆分。

### 4.2 标题识别

系统会识别常见教材、笔记和 Markdown 标题格式，例如：

```text
# 标题
第1章 数据库系统
一、关系模型
（一）实体完整性
1.1 数据模型
1.1.1 关系完整性
考点：事务特性
知识点：范式
```

标题会作为强边界信号。遇到新标题时，通常会开启新的 chunk，使后续统计更接近章节或知识点结构。

### 4.3 语义连续性合并

句子级单元不会直接一条句子生成一个 chunk，而是会被合并成更完整的知识片段。

合并时主要考虑：

1. 同一段落内的连续句子优先合并。
2. 当前 chunk 未达到目标长度时，继续吸收后续句子。
3. 遇到标题、长段落结束或主题转折词时，可以开启新 chunk。
4. 合并结果达到目标长度并位于自然句子边界时，可以结束当前 chunk。

常见主题转折词包括：

```text
首先、其次、再次、最后、另一方面、与此相对、相比之下
```

这些词用于辅助判断主题是否发生变化。

### 4.4 长度约束

当前系统使用以下参数：

```text
minChunkSize = 300
targetChunkSize = 800
maxChunkSize = 1100
overlapSentenceCount = 1
```

含义如下：

| 参数 | 含义 |
| --- | --- |
| `minChunkSize` | 最小建议长度，过短尾段会尝试与前一段合并 |
| `targetChunkSize` | 目标切片长度，达到后可在自然边界结束 |
| `maxChunkSize` | 最大切片长度，超过后必须拆分 |
| `overlapSentenceCount` | 相邻 chunk 保留的句子级重叠数量 |

这种方式不是固定长度切分，而是在长度约束下尽量保留句子和知识点完整性。

### 4.5 句子级重叠

原方案使用固定 120 字符重叠。新方案改为句子级重叠：

```text
相邻 chunk 重叠上一段末尾 1 个句子
```

这样可以让上下文衔接更自然，也避免重叠内容从半句话开始或结束。

## 5. 数据结构调整

`KnowledgeChunk` 增加了以下字段：

```text
chunkingVersion
correctHitCount
wrongHitCount
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `chunkingVersion` | 标记切片算法版本，当前语义切片版本为 2 |
| `correctHitCount` | 正确命中次数，由来源反馈、定制练题反馈和错题答对等场景回写 |
| `wrongHitCount` | 错误命中次数，由来源反馈、定制练题反馈和错题答错等场景回写 |

当前项目已经接入引用、反馈、定制练题和错题练习回写逻辑，相关事件会进入 `knowledge_chunk_event`，并被知识画像统计使用。

## 6. 旧数据升级策略

系统通过 `chunkingVersion` 判断已有知识片段是否来自旧版切分算法。

应用启动时会检查已开启知识库的文件：

```text
如果文件没有 chunk
或旧 chunk 页码需要修复
或 chunkingVersion 小于当前版本
则重新构建该文件的 KnowledgeChunk
```

这样可以保证已有资料逐步升级到新版切分策略，同时避免每次启动都重复重建。

## 7. 与知识掌握统计的关系

后续个人知识画像可以基于 chunk 命中行为继续扩展。

建议将命中分为两类：

1. 正确命中  
   表示用户在问答、复习或错题练习中对该知识点表现较好，可增加 `correctHitCount`。

2. 错误命中  
   表示用户在错题、反复追问或反馈“不懂”时关联到该知识点，可增加 `wrongHitCount`。

后续可进一步计算：

```text
掌握度 = correctHitCount / (correctHitCount + wrongHitCount)
薄弱度 = wrongHitCount
学习热度 = correctHitCount + wrongHitCount
教材覆盖率 = 被命中过的 chunk 数 / 教材总 chunk 数
```

这样，系统可以从“资料问答工具”进一步扩展为“个人知识掌握分析系统”。

## 8. 当前实现位置

后端核心实现位置：

```text
backend/src/main/java/com/example/exam/service/FileService.java
```

核心方法包括：

```text
rebuildKnowledge()
splitIntoSemanticChunks()
splitIntoTextUnits()
splitSentences()
splitOversizedSentence()
shouldStartNewChunk()
overlapTail()
mergeShortTail()
```

相关数据结构：

```text
backend/src/main/java/com/example/exam/model/KnowledgeChunk.java
```

数据库增量字段：

```text
backend/src/main/resources/schema.sql
```

## 9. 方案总结

本方案采用“句子级切分 + 语义连续性合并 + 句子级重叠”的策略，在不依赖大模型或 embedding 的情况下，提升知识片段的语义完整性。

相比固定长度切分，新方案更适合教材、笔记、错题解析等学习资料场景。它既能服务 RAG 问答中的知识召回，也能为后续基于 chunk 命中次数的教材掌握度、薄弱知识点和学习画像统计提供更可靠的数据基础。

## 10. 字符二元组连续性增强方案

### 10.1 方案状态说明

本节是对当前切片算法的后续优化设计。可编译的参考实现已经放入：

```text
backend/src/main/java/com/example/exam/service/chunking/LexicalContinuityChunker.java
```

该类没有添加 `@Service` 或 `@Component`，也没有被 `FileService` 调用。因此代码会参与项目编译，但不会接入当前运行流程，也不会改变现有资料的切片结果。

当前项目实际使用的仍是 `FileService` 中 `chunkingVersion = 2` 的规则切片。答辩时应将本节表述为“针对现有算法提出并完成代码级设计的优化方案”或“下一步可以接入的改进方案”，不能表述为已经上线运行的功能。

该方案不调用大模型和 Embedding，而是在标题、段落、句子边界和长度约束的基础上，使用局部字符二元组 Jaccard 相似度判断边界两侧的词汇连续性。

### 10.2 优化目标

当前算法达到目标长度后，会在自然句子边界结束切片。该方式稳定且开销较低，但没有比较边界前后的内容关联程度。

优化后的目标是：

1. 少于最小长度时避免产生过短片段。
2. 在合理长度范围内收集多个候选边界。
3. 比较候选边界前后的局部字符二元组。
4. 综合内容不连续度、标题、段落和长度选择最佳边界。
5. 超过最大长度时仍在最近的自然句子边界强制切分。

“首先、其次、再次、最后”等词不再视为主题变化信号。这类词通常表示同一主题内部的并列关系，应降低在该位置切片的意愿。

### 10.3 建议参数

```java
// 一般不在 500 字以前按词汇连续性主动切片。
private static final int MIN_CHUNK_SIZE = 500;

// 最理想的片段长度，候选边界越接近该值，长度得分越高。
private static final int TARGET_CHUNK_SIZE = 800;

// 达到该长度后应优先选择已经出现的最佳候选边界。
private static final int PREFERRED_MAX_CHUNK_SIZE = 1000;

// 超过该长度必须在自然边界切片，防止片段无限增长。
private static final int MAX_CHUNK_SIZE = 1100;

// 仅比较候选边界左右各 400 字，避免重复扫描整篇资料。
private static final int CONTEXT_WINDOW_SIZE = 400;
```

### 10.4 参考数据结构

```java
/**
 * 一个候选切片边界。
 *
 * @param unitIndex   新片段开始位置，即 TextUnit 列表中的下标
 * @param chunkLength 从当前片段起点到该边界的字符数
 * @param score       综合边界得分，分数越高越适合切片
 */
private record BoundaryCandidate(
        int unitIndex,
        int chunkLength,
        double score
) {
}
```

### 10.5 二元组生成与相似度计算

```java
/**
 * 计算两个局部文本窗口的字符二元组 Jaccard 相似度。
 *
 * 相似度越高，说明边界前后的词汇连续性越强，不宜切片。
 * 相似度越低，说明两侧内容可能发生变化，更适合作为边界。
 */
private double lexicalSimilarity(String leftText, String rightText) {
    Set<String> leftBigrams = characterBigrams(leftText);
    Set<String> rightBigrams = characterBigrams(rightText);

    // 两侧都没有有效内容时，不把该位置误判为主题变化。
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

    return union.isEmpty()
            ? 1.0
            : intersection.size() / (double) union.size();
}

/**
 * 把规范化文本转换为字符二元组集合。
 *
 * 例如“数据库事务”会生成“数据、据库、库事、事务”。
 * 这里只保留中文、英文字母和数字，不引入中文分词依赖。
 */
private Set<String> characterBigrams(String text) {
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
```

### 10.6 获取边界两侧局部文本

```java
/**
 * 获取候选边界左侧最多 400 字。
 *
 * 只比较边界附近内容，避免当前片段前半部分稀释局部主题。
 */
private String leftContext(List<TextUnit> units, int startIndex, int boundaryIndex) {
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
 * 获取候选边界右侧最多 400 字。
 */
private String rightContext(List<TextUnit> units, int boundaryIndex) {
    StringBuilder result = new StringBuilder();
    for (int i = boundaryIndex; i < units.size(); i++) {
        result.append(units.get(i).text());
        if (result.length() >= CONTEXT_WINDOW_SIZE) {
            return result.substring(0, CONTEXT_WINDOW_SIZE);
        }
    }
    return result.toString();
}
```

### 10.7 前文依赖判断

```java
/**
 * 判断下一句是否明显依赖前文。
 *
 * 这些词通常表示并列、总结、因果或指代关系，不代表真正主题变化。
 * 如果下一句依赖前文，应降低该候选边界的得分。
 */
private boolean startsWithContextDependency(String text) {
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
```

### 10.8 候选边界评分

```java
/**
 * 对一个候选边界进行综合评分。
 *
 * 评分由四部分组成：
 * 1. 边界两侧的词汇不连续度。
 * 2. 标题、段落等结构信号。
 * 3. 当前片段长度与目标长度的接近程度。
 * 4. 下一句是否依赖前文。
 */
private double boundaryScore(
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

    // 越接近 800 字，长度得分越高，最大为 20 分。
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
```

### 10.9 收集和选择候选边界

```java
/**
 * 在当前片段范围内寻找得分最高的候选边界。
 *
 * boundaryIndex 表示新片段从哪个 TextUnit 开始，因此候选位置必须大于
 * chunkStartIndex。标题和新段落是主要候选位置，普通完整句末也可以参与评分。
 */
private BoundaryCandidate findBestBoundary(
        List<TextUnit> units,
        int chunkStartIndex,
        int searchEndExclusive
) {
    BoundaryCandidate best = null;
    int chunkLength = 0;

    for (int i = chunkStartIndex; i < searchEndExclusive; i++) {
        chunkLength += units.get(i).text().length();

        // 边界位于当前单元之后，因此最后一个单元不能作为候选起点。
        int boundaryIndex = i + 1;
        if (boundaryIndex >= units.size()) {
            break;
        }

        // 标题可作为强边界，普通位置至少达到最小长度才参与评分。
        boolean strongHeadingBoundary = units.get(boundaryIndex).heading();
        if (chunkLength < MIN_CHUNK_SIZE && !strongHeadingBoundary) {
            continue;
        }

        double score = boundaryScore(
                units,
                chunkStartIndex,
                boundaryIndex,
                chunkLength
        );

        BoundaryCandidate candidate = new BoundaryCandidate(
                boundaryIndex,
                chunkLength,
                score
        );

        if (best == null || candidate.score() > best.score()) {
            best = candidate;
        }
    }
    return best;
}
```

### 10.10 主切片流程参考实现

```java
/**
 * 使用结构边界、字符二元组连续性和长度约束生成知识片段。
 *
 * 该代码对应项目中的 LexicalContinuityChunker 参考实现。
 * 当前没有被 FileService 调用，因此不会改变实际切片结果。
 */
private List<SemanticChunk> splitWithLexicalContinuity(String text) {
    List<TextUnit> units = splitIntoTextUnits(text);
    if (units.isEmpty()) {
        return List.of();
    }

    List<SemanticChunk> chunks = new ArrayList<>();
    int chunkStartIndex = 0;

    while (chunkStartIndex < units.size()) {
        int searchEndExclusive = chunkStartIndex;
        int accumulatedLength = 0;

        // 先向后扫描到最大长度附近，形成本轮候选边界搜索区间。
        while (searchEndExclusive < units.size()) {
            int nextLength = units.get(searchEndExclusive).text().length();
            if (accumulatedLength > 0
                    && accumulatedLength + nextLength > MAX_CHUNK_SIZE) {
                break;
            }
            accumulatedLength += nextLength;
            searchEndExclusive++;

            // 达到优选最大长度后，不再无条件继续扩大片段。
            if (accumulatedLength >= PREFERRED_MAX_CHUNK_SIZE) {
                break;
            }
        }

        // 已经扫描到全文末尾时，剩余内容作为最后一个片段。
        if (searchEndExclusive >= units.size()) {
            addChunk(
                    chunks,
                    new ArrayList<>(units.subList(chunkStartIndex, units.size()))
            );
            break;
        }

        BoundaryCandidate best = findBestBoundary(
                units,
                chunkStartIndex,
                searchEndExclusive
        );

        int boundaryIndex;
        if (best != null) {
            boundaryIndex = best.unitIndex();
        } else {
            // 没有满足条件的候选位置时，在最大长度前的最后一个完整单元切分。
            boundaryIndex = Math.max(chunkStartIndex + 1, searchEndExclusive);
        }

        addChunk(
                chunks,
                new ArrayList<>(units.subList(chunkStartIndex, boundaryIndex))
        );
        chunkStartIndex = boundaryIndex;
    }

    return mergeShortTail(chunks);
}
```

### 10.11 接入项目时的调用位置

如果后续正式接入，应在 `FileService.rebuildKnowledge()` 中将：

```java
for (SemanticChunk semanticChunk : splitIntoSemanticChunks(text)) {
    // 保存 KnowledgeChunk
}
```

调整为：

```java
for (SemanticChunk semanticChunk : splitWithLexicalContinuity(text)) {
    // 保存 KnowledgeChunk
}
```

正式接入时还需要完成以下工作：

1. 将 `CHUNKING_VERSION` 从 2 升级为 3。
2. 为二元组相似度、候选评分和边界选择增加单元测试。
3. 使用教材、笔记、OCR 文本和中英文混排资料校准参数。
4. 评估重建 chunk 对学习统计和错题关联的影响。
5. 不直接自动重建已有演示数据，避免旧 chunk ID 变化导致关联失效。

### 10.12 答辩表述建议

可以这样介绍当前实现和优化方案：

> 当前系统已采用基于标题、段落、句子边界和长度约束的规则切片，避免固定字符窗口从句子中间截断内容。在此基础上，我进一步编写了字符二元组连续性增强方案的独立实现。该实现比较候选边界左右局部文本的 Jaccard 相似度，并综合标题、段落和目标长度选择边界，不依赖外部模型，计算复杂度接近线性。考虑到历史知识片段已经关联学习统计和错题数据，该类目前没有接入 FileService，尚未替换现有运行算法。

该表述能够区分“当前已经实现的功能”和“已经完成设计但尚未接入的优化”，避免把设计方案误述为线上功能。
