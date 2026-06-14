<!-- [SEARCH:VIEW_KNOWLEDGE_PROFILE] 知识画像页面，展示掌握度、风险、趋势和复习建议。 -->
<template>
<section class="page-panel profile-page">
          <div class="section-head split">
            <div>
              <h3>知识画像总览</h3>
              <p>根据知识片段引用、反馈、错题和刷题结果统计掌握情况。</p>
            </div>
            <button class="secondary-btn slim" type="button" :disabled="profileLoading" @click="loadKnowledgeProfile">
              <RefreshCw :size="16" />
              刷新画像
            </button>
          </div>

          <div class="profile-grid enhanced">
            <article v-for="metric in profileMetricCards" :key="metric.label" class="profile-card metric-card" :style="{ '--metric-accent': metric.accent }">
              <div class="metric-card-head">
                <span>{{ metric.label }}</span>
                <component :is="metric.icon" :size="20" />
              </div>
              <strong>{{ metric.value }}</strong>
              <div class="metric-rail" aria-hidden="true">
                <i :style="{ width: `${metric.progress}%` }"></i>
              </div>
              <em>{{ metric.detail }}</em>
            </article>
          </div>

          <section class="profile-block diagnosis-panel">
            <div class="diagnosis-head">
              <div>
                <h3>学习诊断</h3>
                <p>每天首次登录自动诊断一次；需要重新判断时，可以手动刷新。</p>
              </div>
              <div class="diagnosis-actions">
                <div class="segmented compact">
                  <button
                    v-for="days in [7, 14, 30]"
                    :key="days"
                    type="button"
                    :class="{ active: profileTrendDays === days }"
                    @click="changeProfileTrendDays(days)"
                  >
                    {{ days }} 天
                  </button>
                </div>
                <button class="secondary-btn slim" type="button" :disabled="profileDiagnosisLoading" @click="refreshProfileDiagnosis">
                  <LoaderCircle v-if="profileDiagnosisLoading" :size="16" class="spin-icon" />
                  <RefreshCw v-else :size="16" />
                  刷新诊断
                </button>
              </div>
            </div>
            <div class="diagnosis-chat-bubble" :class="{ loading: profileDiagnosisLoading, notice: profileDiagnosisInsufficient }">
              <LoaderCircle v-if="profileDiagnosisLoading" :size="20" class="spin-icon" />
              <p>{{ profileDiagnosisMessage }}</p>
            </div>
            <div v-if="!profileDiagnosisLoading && profileDiagnosisItems.length > 0" class="diagnosis-grid">
              <article v-for="item in profileDiagnosisItems" :key="item.label" class="diagnosis-card" :class="item.severity?.toLowerCase()">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
                <em>{{ item.detail }}</em>
              </article>
            </div>
          </section>

          <div class="profile-charts">
            <section class="profile-block chart-panel">
              <h3>学科掌握率</h3>
              <div ref="profileSubjectChartRef" class="profile-chart" role="img" aria-label="学科掌握率柱状图"></div>
            </section>
            <section class="profile-block chart-panel">
              <h3>掌握等级分布</h3>
              <div ref="profileDistributionChartRef" class="profile-chart" role="img" aria-label="掌握等级分布环形图"></div>
            </section>
            <section class="profile-block chart-panel wide">
              <h3>学习趋势</h3>
              <div ref="profileTrendChartRef" class="profile-chart trend" role="img" aria-label="近 14 天学习趋势折线图"></div>
            </section>
            <section class="profile-block chart-panel">
              <h3>学习活跃热力图</h3>
              <div ref="profileHeatmapChartRef" class="profile-chart" role="img" aria-label="学习活跃日历热力图"></div>
            </section>
            <section class="profile-block chart-panel">
              <div class="chart-title-row">
                <h3>复习压力趋势</h3>
                <label class="chart-filter">
                  <span>范围</span>
                  <select v-model="profilePressureSubjectId" @change="changeProfilePressureSubject">
                    <option value="all">全科</option>
                    <option
                      v-for="subject in profileSubjects"
                      :key="subject.subjectFolderId"
                      :value="String(subject.subjectFolderId)"
                    >
                      {{ subject.subjectName }}
                    </option>
                  </select>
                </label>
              </div>
              <div ref="profilePressureChartRef" class="profile-chart" role="img" aria-label="复习压力趋势图"></div>
            </section>
          </div>

          <section class="profile-block recommendation-panel">
            <div class="section-head split">
              <div>
                <h3>今日复习建议</h3>
                <p>按遗忘风险、错题反馈、引用频率和考研倒计时排序。</p>
              </div>
              <button class="secondary-btn slim" type="button" :disabled="profileLoading || profileDiagnosisLoading" @click="refreshProfileRecommendations">
                <RefreshCw :size="16" />
                更新建议
              </button>
            </div>
            <article v-for="suggestion in profileSuggestions" :key="suggestion.chunkId" class="recommendation-row">
              <div>
                <strong>{{ suggestion.title }}</strong>
                <p>{{ suggestion.reason }} · 风险 {{ Math.round(suggestion.riskScore || 0) }} · 预计 {{ suggestion.estimatedMinutes || 30 }} 分钟</p>
                <span>{{ suggestion.fileName }} · 第 {{ suggestion.pageNumber || 1 }} 页</span>
              </div>
              <div class="recommendation-actions">
                <button class="secondary-btn slim" type="button" @click="openTeacherForSuggestion(suggestion)">进入定制练题</button>
                <button class="primary-btn compact" type="button" :disabled="loading" @click="addSuggestionToPlan(suggestion)">
                  <CalendarPlus :size="16" />
                  加入计划
                </button>
              </div>
            </article>
            <div v-if="profileSuggestions.length === 0" class="empty-note">暂无高风险复习建议，可以先从未评估知识点开始练习。</div>
          </section>

          <div class="profile-sections">
            <section class="profile-block profile-subject-list wide">
              <div class="profile-block-heading">
                <div>
                  <h3>学科画像</h3>
                  <p>各学科掌握度与薄弱点总览</p>
                </div>
                <span>{{ profileSubjects.length }} 个学科</span>
              </div>
              <div v-if="profileSubjects.length > 0" class="subject-profile-grid">
                <article
                  v-for="(subject, index) in profileSubjects"
                  :key="subject.subjectFolderId"
                  class="subject-profile-card"
                  :style="{
                    '--subject-progress': subjectProgressDegrees(subject.masteryRate),
                    '--subject-accent': subjectAccent(index)
                  }"
                >
                  <div class="subject-card-top">
                    <span class="subject-icon-badge">
                      <BookOpenCheck :size="18" />
                    </span>
                    <div class="mastery-ring" :aria-label="`${subject.subjectName}掌握度${formatPercent(subject.masteryRate)}`">
                      <span>{{ formatPercent(subject.masteryRate) }}</span>
                    </div>
                  </div>
                  <div class="subject-card-main">
                    <strong>{{ subject.subjectName }}</strong>
                    <span>掌握度</span>
                  </div>
                  <div class="subject-metrics">
                    <div>
                      <em>覆盖率</em>
                      <strong>{{ formatPercent(subject.coverageRate) }}</strong>
                    </div>
                    <div>
                      <em>近 14 天</em>
                      <strong>{{ subject.recentPracticeCount || 0 }} 次</strong>
                    </div>
                  </div>
                  <div class="subject-risk-state" :class="{ warn: (subject.highRiskChunkCount || 0) > 0 }">
                    <span v-if="(subject.highRiskChunkCount || 0) > 0">⚠ 重点突破 {{ subject.highRiskChunkCount }} 个</span>
                    <span v-else>✓ 当前状态良好暂无高风险知识点</span>
                  </div>
                </article>
              </div>
              <div v-if="profileSubjects.length === 0" class="empty-note">暂无学科画像数据。</div>
            </section>

            <section class="profile-block wide">
              <h3>薄弱知识点</h3>
              <article
                v-for="chunk in paginatedProfileWeakChunks"
                :key="chunk.chunkId"
                class="weak-chunk-row"
                tabindex="0"
                title="双击打开对应文件内容"
                @dblclick="openWeakChunkInEditor(chunk)"
                @keydown.enter="openWeakChunkInEditor(chunk)"
              >
                <div>
                  <strong>{{ displayFileName({ originalName: chunk.fileName }) }} · 第 {{ chunk.pageNumber || 1 }} 页</strong>
                  <p>{{ chunk.excerpt }}</p>
                </div>
                <div class="weak-chunk-meta">
                  <span>掌握度 {{ formatPercent(chunk.masteryRate) }}</span>
                  <span>置信度 {{ confidenceLabel(chunk.confidenceLevel) }}</span>
                  <span>优先级 {{ formatPercent(chunk.reviewPriority) }}</span>
                  <span>最近练习 {{ formatDateTime(chunk.lastPracticedAt) }}</span>
                  <span>错 {{ chunk.wrongHitCount }} / 对 {{ chunk.correctHitCount }}</span>
                  <button class="secondary-btn slim" type="button" @click.stop="openWeakChunkInEditor(chunk)">打开原文</button>
                  <button class="secondary-btn slim" type="button" @click.stop="openTeacherForWeakChunk(chunk)">进入定制练题</button>
                </div>
              </article>
              <div v-if="profileWeakChunks.length === 0" class="empty-note">暂无已反馈的薄弱知识点。</div>
              <div v-else class="profile-pagination">
                <span>{{ profileWeakChunkPageSummary }}</span>
                <div class="pagination-actions">
                  <button class="secondary-btn slim" type="button" :disabled="profileWeakChunkPage <= 1" @click="profileWeakChunkPage = 1">首页</button>
                  <button class="secondary-btn slim" type="button" :disabled="profileWeakChunkPage <= 1" @click="profileWeakChunkPage -= 1">上一页</button>
                  <button
                    v-for="page in profileWeakChunkPageItems"
                    :key="page"
                    class="page-dot"
                    :class="{ active: page === profileWeakChunkPage, ellipsis: typeof page !== 'number' }"
                    type="button"
                    :disabled="typeof page !== 'number'"
                    @click="profileWeakChunkPage = page"
                  >
                    {{ typeof page === 'number' ? page : '...' }}
                  </button>
                  <button class="secondary-btn slim" type="button" :disabled="profileWeakChunkPage >= profileWeakChunkPageCount" @click="profileWeakChunkPage += 1">下一页</button>
                  <button class="secondary-btn slim" type="button" :disabled="profileWeakChunkPage >= profileWeakChunkPageCount" @click="profileWeakChunkPage = profileWeakChunkPageCount">尾页</button>
                </div>
              </div>
            </section>
          </div>
        </section>
</template>
<script setup>
import { useAppContext } from '../composables/appContext'

const { BookOpenCheck, CalendarPlus, LoaderCircle, RefreshCw, loading, profileSubjects, profileWeakChunks, profileWeakChunkPage, profileWeakChunkPageCount, profileWeakChunkPageItems, profileWeakChunkPageSummary, paginatedProfileWeakChunks, profileTrendDays, profilePressureSubjectId, profileLoading, profileDiagnosisLoading, profileSubjectChartRef, profileDistributionChartRef, profileTrendChartRef, profileHeatmapChartRef, profilePressureChartRef, profileMetricCards, profileDiagnosisMessage, profileDiagnosisInsufficient, profileDiagnosisItems, profileSuggestions, displayFileName, formatPercent, subjectProgressDegrees, subjectAccent, confidenceLabel, formatDateTime, openTeacherForWeakChunk, openWeakChunkInEditor, loadKnowledgeProfile, refreshProfileDiagnosis, refreshProfileRecommendations, changeProfilePressureSubject, changeProfileTrendDays, openTeacherForSuggestion, addSuggestionToPlan } = useAppContext()
</script>
