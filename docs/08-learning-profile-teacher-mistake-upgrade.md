# 学习画像、定制练题与错题闭环改造说明

## 1. 改造目标

本次改造把知识库问答、定制练题、错题集和刷题练习串成一个学习闭环。系统不再只保存资料和错题本身，而是围绕 `KnowledgeChunk` 记录引用、正确反馈、错误反馈和最近访问/练习时间，再按个人、学科、教材和薄弱知识点聚合为学习画像。

核心目标包括：

- 新用户首次进入系统时完成学科和考研日期初始化。
- 一级学科文件夹作为学科画像的统计维度。
- 答疑助手引用来源时统计 `citeCount`，由用户主动反馈“很清楚 / 忘记了”更新掌握情况。
- 定制练题固定使用知识库和知识溯源，每次只生成一个可追溯到 chunk 的问题。
- 定制题和手动错题都可以关联知识片段。
- 刷错题时记录“写对了 / 写错了”，并回写关联 chunk 的学习统计。
- 新增知识画像页面，展示总览、学科画像、教材画像和薄弱知识点。

## 2. 数据模型变更

### 2.1 `knowledge_chunk`

新增学习统计字段：

| 字段 | 含义 |
| --- | --- |
| `cite_count` | 答疑助手最终返回给前端的来源引用次数 |
| `last_accessed_at` | 最近被引用、反馈或练习回写的时间 |
| `last_practiced_at` | 最近发生“很清楚 / 忘记了 / 写对了 / 写错了”的时间 |

`KnowledgeChunk` 新增两个派生口径：

```text
feedbackCount = correctHitCount + wrongHitCount
masteryRate = (correctHitCount + 2.5) / (correctHitCount + 1.2 * wrongHitCount + 5.0)
```

`masteryRate` 采用平滑项，避免新 chunk 在没有反馈时被误判为完全掌握。

### 2.2 `study_folder`

新增字段：

| 字段 | 含义 |
| --- | --- |
| `subject_folder` | 是否为学科文件夹 |
| `subject_order` | 学科排序 |

学科文件夹口径为：`parent_id IS NULL`、`depth = 1`、`subject_folder = true`。画像按该一级文件夹及其全部子文件夹聚合。

### 2.3 `user_study_profile`

新增用户学习档案表，保存：

- `exam_date`：考研日期。
- `onboarded`：是否完成初始化。
- `subject_count`：初始化时填写的学科数量。

旧账号兼容规则：

- 已有一级文件夹但没有 profile 的账号，自动创建 profile，并把一级文件夹标记为学科文件夹。
- 没有一级文件夹的账号，返回 `onboarded=false`，前端展示初始化页。

### 2.4 `mistake_question_chunk`

新增错题与知识片段关联表：

| 字段 | 含义 |
| --- | --- |
| `id` | 主键 |
| `mistake_id` | 错题 ID |
| `chunk_id` | 知识片段 ID |
| `source_type` | 关联来源，例如手动关联或定制题生成 |
| `created_at` | 创建时间 |

唯一约束：`(mistake_id, chunk_id)`，防止同一道错题重复绑定同一知识片段。

## 3. 后端接口变更

### 3.1 学习初始化

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/study-profile` | 获取当前用户学习档案，兼容旧账号自动初始化 |
| `POST` | `/api/study-profile/onboarding` | 首次设置学科数量、学科名称和考研日期，并自动创建学科文件夹 |
| `PUT` | `/api/study-profile` | 更新考研日期等 profile 信息 |

### 3.2 Chunk 交互统计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/chat/chunks/{chunkId}/feedback` | 记录 chunk 反馈 |

请求体：

```json
{ "type": "CLEAR" }
```

或：

```json
{ "type": "FORGOT" }
```

处理规则：

- `CLEAR`：`correctHitCount + 1`。
- `FORGOT`：`wrongHitCount + 1`。
- 两者都会更新 `lastPracticedAt` 和 `lastAccessedAt`。
- 统一由 `KnowledgeChunkInteractionService` 校验 chunk 归属，防止越权反馈。

答疑助手中，只有最终返回给前端的 `sources` 会执行：

- `citeCount + 1`
- `lastAccessedAt = now`

### 3.3 定制练题

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/chat/teacher/question` | 基于知识库生成一个可追溯问题 |

请求包含：

- `folderId`：当前文件夹。
- `subjectFolderId`：可选，限定学科范围。
- `requirement`：用户输入的提问要求，例如“CPU”。
- `excludeChunkIds`：本轮已问过的 chunk，避免连续重复。
- AI 设置字段。

响应包含：

- `question`
- `referenceAnswer`
- `source`
- `chunkId`

定制练题固定使用知识库和来源追溯。底部不再提供“使用知识库 / 引用来源 / 深度回答”的多选项。

### 3.4 错题与刷题

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/mistakes/from-teacher-question` | 把当前定制题加入错题，并关联 chunk |
| `POST` | `/api/mistakes/{mistakeId}/practice-result` | 记录刷错题结果 |
| `GET` | `/api/knowledge-profile/chunks` | 搜索可关联的知识片段，支持按学科文件夹和文件缩小范围 |

定制题加入错题时：

- 如果用户已经点过“忘记了”，则只创建错题和 chunk 关联，不重复增加 `wrongHitCount`。
- 如果没有点过“忘记了”，则创建错题时执行一次 `wrongHitCount + 1`。

刷错题时：

- `correct=true`：关联的每个 chunk 执行 `correctHitCount + 1`。
- `correct=false`：关联的每个 chunk 执行 `wrongHitCount + 1`。
- 两种情况都更新 `lastPracticedAt` 和 `lastAccessedAt`。
- 没有关联 chunk 的错题不会报错，只返回 `updatedChunkCount = 0`。
- 第一版不自动修改错题 `mastered` 状态，避免一次写对就直接视为完全掌握。

### 3.5 知识画像

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/knowledge-profile/overview` | 个人画像总览 |
| `GET` | `/api/knowledge-profile/subjects` | 学科画像 |
| `GET` | `/api/knowledge-profile/files` | 教材/资料画像，支持 `folderId` 缩小范围 |
| `GET` | `/api/knowledge-profile/weak-chunks` | 薄弱知识片段 |
| `GET` | `/api/knowledge-profile/trends` | 学习趋势，支持 `days` 参数 |
| `GET` | `/api/knowledge-profile/distribution` | 掌握度分布 |
| `GET` | `/api/knowledge-profile/activity` | 近期活动统计，支持 `days` 参数 |
| `GET` | `/api/knowledge-profile/risk` | 高风险知识点和复习压力，支持 `days`、`folderId` 参数 |
| `GET` | `/api/knowledge-profile/diagnosis` | 规则诊断和可选 AI 总结，支持 `days`、`ai` 参数 |
| `GET` | `/api/knowledge-profile/chunks` | 知识片段搜索，支持 `folderId`、`fileId` 和 `query` |

统计口径：

- 覆盖率只统计 `feedbackCount > 0` 的 chunk。
- 学科画像按一级学科文件夹及其子文件夹聚合。
- 教材画像按 `StudyFile` 聚合。
- 薄弱知识点只包含已反馈 chunk。
- 无学科归属的 chunk 归入“未分类”。
- 风险分析综合掌握度、遗忘间隔、错误次数、引用次数和最近练习时间。
- 诊断接口在数据不足时返回规则化提示；数据充足且 AI Key 可用时附加 AI 总结。

## 4. 定制练题选题规则

本次采用方案 C，自适应兼顾相关性、薄弱程度、遗忘风险和引用关注度。

```text
forgetRisk = min(1, daysSinceLastPracticed / 14)
attentionScore = normalized(log(1 + citeCount))
reviewPriority = (1 - masteryRate) * 0.6 + forgetRisk * 0.25 + attentionScore * 0.15
score = relevanceScore * 0.50 + reviewPriority * 0.45 + randomFactor * 0.05
```

选题范围规则：

- 传入 `subjectFolderId` 时，在该学科及子文件夹内选题。
- 否则在当前 `folderId` 及子文件夹内选题。
- 排除 `excludeChunkIds` 中本轮已问过的 chunk。
- 候选为空时自动清空排除列表重新选题。

Prompt 约束：

- 只根据当前 chunk 明确出现的信息出题。
- 避免要求比较、扩展或解释资料中没有直接支撑的内容。
- 若模型生成的问题超出 chunk 证据范围，后端使用保守兜底问题。

## 5. 前端交互变更

### 5.1 初始化页

登录后先请求 `/api/study-profile`：

- `onboarded=false`：显示初始化页，要求填写学科数量、学科名称和考研日期。
- `onboarded=true`：进入主界面。

初始化成功后，系统自动创建一级学科文件夹，并把这些文件夹作为后续画像和错题科目标签的基础。

### 5.2 答疑来源反馈

来源弹窗展示：

- 掌握度。
- 引用次数。
- 正确/错误反馈次数。
- 最近访问和最近练习时间。

每个来源提供轻量按钮：

- “很清楚” -> `CLEAR`
- “忘记了” -> `FORGOT`

点击后局部刷新该来源统计。

### 5.3 定制练题

定制练题维护独立状态：

- `requirement`
- `currentQuestion`
- `referenceAnswer`
- `currentChunkId`
- `currentSource`
- `feedbackType`
- `addedToMistake`
- `askedChunkIds`

用户可以：

- 输入提问要求。
- 选择学科范围。
- 生成当前问题。
- 点击“很清楚 / 忘记了”反馈。
- 点击“添加到错题”生成错题并关联 chunk。
- 点击“下一题”继续在同一要求下换题。

### 5.4 错题关联知识片段

上传错题和修改错题表单新增“关联知识片段”区域：

- 先选择学科文件夹。
- 再选择该学科范围内的具体资料文件。
- 输入关键词搜索知识片段。
- 点击候选片段即可关联。

这样可以把搜索范围从全库缩小到“学科 -> 资料 -> 关键词”，避免候选过多。

科目标签与学科文件夹联动：

- 后端列出错题科目标签时会同步一级学科文件夹。
- 手动选择学科文件夹搜索 chunk 时，前端会自动勾选同名科目标签。

### 5.5 刷错题回写画像

随机练习卡片新增：

- “写对了”
- “写错了”

提交后显示本轮记录结果和更新到的知识片段数量。同一轮同一道题只能记录一次；重新开始一轮练习后可以再次记录。

## 6. 关键代码文件

### 6.1 后端

| 文件 | 说明 |
| --- | --- |
| `backend/src/main/resources/schema.sql` | 历史兼容 DDL；当前默认由 Hibernate `ddl-auto=update` 维护表结构 |
| `KnowledgeChunk.java` | chunk 学习统计字段和掌握度计算 |
| `StudyFolder.java` | 学科文件夹字段 |
| `UserStudyProfile.java` | 用户学习档案 |
| `MistakeQuestionChunk.java` | 错题和 chunk 关联 |
| `StudyProfileController/Service` | 初始化和 profile 管理 |
| `KnowledgeChunkInteractionService` | 引用、反馈、时间更新和权限校验 |
| `KnowledgeProfileController/Service` | 画像聚合和 chunk 搜索 |
| `ChatService` | 答疑引用统计、定制练题选题和出题 |
| `MistakeService` | 错题关联 chunk、定制题入错题、刷题结果回写 |

### 6.2 前端

| 文件 | 说明 |
| --- | --- |
| `frontend/src/api/client.js` | 新增 profile、画像、chunk feedback、定制题、错题回写 API |
| `frontend/src/views/*.vue`、`frontend/src/composables/useSmartExamApp.js` | 初始化页、知识画像页、定制练题、错题关联、刷题回写 |
| `frontend/src/styles/*.css` | 新增画像、练题反馈、错题关联筛选和提示样式 |

## 7. 验证清单

- 后端构建：`mvn -DskipTests package`。
- 前端构建：`npm.cmd run build`。
- 新账号初始化后自动创建学科文件夹。
- 旧账号已有一级文件夹时自动生成 profile 并标记学科。
- 答疑返回来源后，只有最终 sources 增加引用次数。
- 来源弹窗“很清楚 / 忘记了”可以更新 hit count 和最近练习时间。
- 定制练题能按要求和学科范围出题，并能“下一题”排除已问 chunk。
- 定制题加入错题时不会重复记录已点击过的“忘记了”。
- 上传/修改错题可以按学科和资料文件缩小 chunk 搜索范围。
- 刷错题“写对了 / 写错了”能回写关联 chunk。
- 没有关联 chunk 的错题刷题结果不会报错。
- 知识画像能正确展示总览、学科、教材和薄弱知识点。
