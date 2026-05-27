# 知识画像增强设计说明

## 1. 改造背景

原有个人知识画像主要展示整体掌握度、覆盖率、学科画像、教材画像和薄弱知识点列表，统计维度较少。由于掌握率主要由知识片段的答对、答错次数直接计算，用户刚上传资料后只进行一两次反馈，就可能出现掌握率过高或过低的问题，画像结果稳定性不足。

本次改造分两期完成：

- 第一期：稳住掌握率，丰富画像仪表盘，并记录知识片段学习事件。
- 第二期：加入学习趋势、遗忘风险、复习压力、诊断建议和计划联动。

改造目标是让知识画像从静态统计页升级为能够反映学习过程、发现薄弱点、辅助复习决策的学习诊断中心。

## 2. 第一期改造：画像仪表盘与事件沉淀

### 2.1 核心目标

第一期重点解决两个问题：

- 掌握率波动过大：使用带先验平滑的掌握率公式，避免少量反馈造成极端结果。
- 画像页面单调：增加统计卡片和 ECharts 图表，使用户能够快速查看整体学习状态。

### 2.2 数据模型

新增知识片段事件表 `knowledge_chunk_event`，用于记录用户围绕知识片段产生的学习行为。

主要字段：

- `owner_id`：用户 ID
- `chunk_id`：知识片段 ID
- `file_id`：资料文件 ID
- `folder_id`：文件夹 ID
- `event_type`：事件类型
- `correct`：是否正确，仅反馈和练习事件使用
- `created_at`：事件时间

事件类型包括：

- `CITED`：知识片段被问答或定制练题引用
- `FEEDBACK_CLEAR`：用户反馈“很清楚”
- `FEEDBACK_FORGOT`：用户反馈“忘记了”
- `PRACTICE_CORRECT`：错题练习答对
- `PRACTICE_WRONG`：错题练习答错

### 2.3 掌握率平滑

知识片段继续保留原有计数字段：

- `correctHitCount`
- `wrongHitCount`
- `citeCount`
- `lastAccessedAt`
- `lastPracticedAt`

掌握率计算改为：

```text
masteryRate = (correctHitCount + 2.5)
              / (correctHitCount + 1.2 * wrongHitCount + 5.0)
```

该公式引入先验值，使 0 次或少量反馈时掌握率不会剧烈波动。0 次反馈的知识点不参与平均掌握率统计，前端显示为“未评估”。

置信度按反馈次数划分：

- `NONE`：0 次反馈
- `LOW`：1-2 次反馈
- `MEDIUM`：3-5 次反馈
- `HIGH`：6 次及以上反馈

### 2.4 后端接口

第一期扩展了原有知识画像接口：

- `GET /api/knowledge-profile/overview`
- `GET /api/knowledge-profile/subjects`
- `GET /api/knowledge-profile/files`
- `GET /api/knowledge-profile/weak-chunks`

新增统计字段包括：

- 未练习知识点数
- 熟练知识点数
- 中等掌握知识点数
- 高风险知识点数
- 平均置信度
- 近 14 天练习次数
- 近 14 天答对次数
- 近 14 天答错次数

新增接口：

- `GET /api/knowledge-profile/trends?days=14`
- `GET /api/knowledge-profile/distribution`

其中 `trends` 返回每日练习、答对、答错和引用次数；`distribution` 返回未评估、薄弱、一般、良好、熟练的知识点数量分布。

### 2.5 前端页面

前端新增 `echarts` 依赖，将知识画像页升级为仪表盘。

第一期页面包括：

- 顶部指标卡：整体掌握度、覆盖率、置信度、薄弱点、待复习、高风险、近 14 天练习、距离考研。
- 学科掌握率柱状图。
- 掌握等级分布环形图。
- 近 14 天学习趋势折线图。
- 教材画像选择区。
- 薄弱知识点列表。

页面支持窗口大小变化时自动调整图表尺寸，移动端改为单列布局。

## 3. 第二期改造：趋势、遗忘风险与学习诊断

### 3.1 核心目标

第二期在第一期事件沉淀基础上继续增强，使画像能够回答以下问题：

- 最近 7 天、14 天、30 天学习趋势如何？
- 哪些天学习最活跃？
- 哪些知识点存在遗忘风险？
- 考研越临近，复习压力如何变化？
- 今天最应该复习哪些内容？
- 这些建议能否直接转化为学习计划？

### 3.2 错题复盘事件表

新增错题复盘事件表 `mistake_practice_event`，用于记录用户刷错题的行为。

主要字段：

- `owner_id`：用户 ID
- `mistake_id`：错题 ID
- `correct`：是否答对
- `created_at`：复盘时间

设计原因是部分错题可能没有关联知识片段。如果只依赖 `knowledge_chunk_event`，这些错题练习不会进入画像趋势统计。新增该表后，无论错题是否关联知识片段，都可以计入错题复盘次数。

### 3.3 遗忘风险公式

第二期为每个知识片段计算 `riskScore`，范围为 0-100。

核心变量：

```text
masteryRisk = feedbackCount == 0 ? 0.55 : 1 - masteryRate
wrongRisk = (wrong + 1.2) / (correct + wrong + 3.0)
attentionRisk = log1p(citeCount) / log1p(maxUserCiteCount + 1)
examUrgency = clamp(1 - daysUntilExam / 180, 0.15, 1.0)
baseInterval = daysUntilExam <= 30 ? 5 : daysUntilExam <= 90 ? 10 : 14
targetInterval = baseInterval * (0.75 + masteryRate)
forgettingRisk = clamp(daysSincePractice / targetInterval, 0, 1)
confidencePenalty = feedbackCount < 3 ? 0.08 : 0
```

最终风险分：

```text
riskScore = clamp(
  100 * (
    0.32 * masteryRisk
    + 0.22 * forgettingRisk
    + 0.18 * wrongRisk
    + 0.14 * attentionRisk
    + 0.14 * examUrgency
    + confidencePenalty
  ),
  0,
  100
)
```

该公式综合考虑：

- 掌握率
- 距离上次复习的时间
- 错误反馈比例
- 问答引用频率
- 距离考研时间
- 数据置信度

当考研日期越近，`examUrgency` 越高；同时目标复习间隔会缩短，使复习压力更敏感。

### 3.4 复习压力趋势

复习压力按天计算，返回：

- `averageRiskScore`：当天平均风险分
- `dueChunkCount`：风险分大于等于 65 的待复习知识点数
- `highRiskChunkCount`：风险分大于等于 80 的高风险知识点数

历史趋势以事件表启用后的行为为精确依据；已有旧数据仍参与当前风险计算，但不会伪造过去事件。

### 3.5 后端接口

第二期新增接口：

```text
GET /api/knowledge-profile/activity?days=30
```

返回内容：

- 每日练习次数
- 每日答对次数
- 每日答错次数
- 每日引用次数
- 每日错题复盘次数
- 每日新增掌握知识点数
- 学科活跃度

```text
GET /api/knowledge-profile/risk?days=30
```

返回内容：

- 高风险知识点 Top 10
- 复习压力趋势
- 知识点风险气泡图数据

```text
GET /api/knowledge-profile/diagnosis?days=30&ai=true
```

返回内容：

- 规则诊断总结
- AI 润色总结
- 诊断卡片
- 今日复习建议
- 可写入学习计划的任务 payload

### 3.6 规则诊断与 AI 润色

诊断采用“规则计算 + AI 润色”的方式。

后端始终先生成规则诊断，包括：

- 最薄弱学科
- 最活跃学科
- 最长未复习教材
- 最高风险知识点
- 今日复习建议

如果用户已配置聊天模型 API Key，则后端再调用用户当前 AI 设置，生成老师式诊断总结。AI 只负责润色和解释，不改变风险排序，不参与核心计算。

如果 AI 未配置或调用失败，接口仍返回规则诊断结果，前端不弹出错误。

### 3.7 前端页面增强

第二期画像页新增时间窗口切换：

- 7 天
- 14 天
- 30 天

新增图表：

- 学习趋势折线图：练习、答对、答错、引用、错题复盘。
- 学习活跃热力图：按天显示学习活跃度。
- 遗忘风险 Top 10 柱状图。
- 复习压力趋势图。
- 知识点风险气泡图。

新增学习诊断区域：

- AI 或规则诊断总结。
- 最薄弱学科、最活跃学科、最长未复习教材、最高风险知识点。
- 今日复习建议列表。

每条建议展示：

- 建议标题
- 推荐原因
- 风险分
- 预计复习时长
- 资料名和页码

操作按钮：

- 进入定制练题
- 加入计划

### 3.8 学习计划联动

第二期新增接口：

```text
POST /api/study-plan/profile-suggestion
```

用于将画像页复习建议写入学习计划，并保存为 AI 来源任务。

默认写入规则：

- 标题：`复习：{知识点/资料名}`
- 类型：`REVIEW`
- 优先级：风险分大于等于 80 为 `HIGH`，否则为 `MEDIUM`
- 日期：今天
- 时间：默认当前整点后的 30 分钟；如果当前已晚于 22:00，则安排到明天 09:00-09:30
- 描述：包含推荐原因、资料名、页码和知识片段摘要

这样画像结果可以直接转化为学习行动，形成“统计分析 -> 复习建议 -> 学习计划”的闭环。

## 4. 验证结果

两期改造完成后均通过以下验证：

```bash
mvn.cmd -q -DskipTests package
npm.cmd run build
```

前端构建存在 ECharts 导致的 chunk size 提示，这是构建体积警告，不影响系统运行。

## 5. 改造效果

改造后，知识画像模块具备以下能力：

- 掌握率更稳定，不会因少量反馈大幅波动。
- 统计维度更丰富，覆盖整体、学科、教材、知识点、错题复盘和学习趋势。
- 图表更直观，支持趋势、分布、热力、风险和压力多种视角。
- 能根据考研日期动态计算复习压力。
- 能识别高风险知识点并给出今日复习建议。
- 能使用 AI 对规则诊断进行自然语言总结。
- 能一键把复习建议加入学习计划，形成完整学习闭环。

