<!-- [SEARCH:VIEW_STUDY_PLAN] 学习规划页面，包含手工日程和 AI 草稿预览两种工作流。 -->
<template>
<section class="page-panel planner-page">
          <div v-if="!planModule" class="planner-module-landing">
            <button class="mistake-module-card" type="button" @click="openPlanModule('manual')">
              <CalendarDays :size="34" />
              <strong>自我规划</strong>
              <span>像课程表一样手动安排学习、复盘、考试和休息，并支持撤回修改。</span>
            </button>
            <button class="mistake-module-card" type="button" @click="openPlanModule('ai')">
              <Sparkles :size="34" />
              <strong>AI 规划</strong>
              <span>和大模型多轮讨论，按周保存草稿，确认后再写入真实日程。</span>
            </button>
          </div>

          <template v-if="planModule === 'manual'">
          <div class="planner-toolbar">
            <div class="planner-week-switch">
              <button class="secondary-btn slim" type="button" @click="shiftPlanWeek(-1)">
                <ChevronRight class="flip-x" :size="16" />
                上周
              </button>
              <button class="secondary-btn slim" type="button" @click="goCurrentPlanWeek">本周</button>
              <button class="secondary-btn slim" type="button" @click="shiftPlanWeek(1)">
                下周
                <ChevronRight :size="16" />
              </button>
            </div>
            <strong>{{ planWeekLabel }}</strong>
            <div class="planner-stats">
              <span>共 {{ planStats.total }} 项</span>
              <span>完成 {{ planStats.done }} 项</span>
              <span>高优先级 {{ planStats.high }} 项</span>
              <button class="secondary-btn slim" type="button" :disabled="!planUndoStack.length || planUndoLoading" @click="undoStudyPlanChange">
                <Undo2 :size="16" />
                撤回
              </button>
              <button class="primary-btn compact" type="button" @click="openPlannerAiPage">
                <Sparkles :size="17" />
                AI 规划
              </button>
            </div>
          </div>

          <div class="planner-layout">
            <section class="planner-board" aria-label="周学习课程表">
              <article
                v-for="day in planWeekDays"
                :key="day.iso"
                class="planner-day"
                :class="{ today: isToday(day.iso) }"
              >
                <header>
                  <div>
                    <span>{{ day.weekday }}</span>
                    <strong>{{ day.monthDay }}</strong>
                  </div>
                  <button class="icon-btn mini" type="button" title="新增规划" @click="openNewStudyPlan(day.iso)">
                    <CalendarPlus :size="15" />
                  </button>
                </header>
                <div class="planner-day-list">
                  <button
                    v-for="item in studyPlanItemsByDate[day.iso]"
                    :key="item.id"
                    class="plan-block"
                    :class="[`type-${item.itemType}`, `priority-${item.priority}`, { done: item.status === 'DONE' }]"
                    type="button"
                    @click="editStudyPlan(item)"
                  >
                    <time>{{ normalizeTimeValue(item.startTime) }} - {{ normalizeTimeValue(item.endTime) }}</time>
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.subject || planTypeLabel(item.itemType) }}</span>
                  </button>
                  <div v-if="!studyPlanItemsByDate[day.iso]?.length" class="planner-empty-slot">空档</div>
                </div>
              </article>
            </section>

            <aside class="planner-side">
              <form class="planner-form" @submit.prevent="saveStudyPlanItem">
                <div class="section-head split">
                  <div>
                    <h3>{{ editingPlanItem ? '修改规划' : '手动安排' }}</h3>
                    <p>{{ editingPlanItem ? `正在修改 #${editingPlanItem.id}` : '新增课程、自习、复盘或休息块。' }}</p>
                  </div>
                  <button v-if="editingPlanItem" class="secondary-btn slim" type="button" @click="resetPlanForm(planForm.startDate)">取消</button>
                </div>

                <label>
                  标题
                  <input v-model="planForm.title" maxlength="120" required placeholder="如：数学强化刷题" />
                </label>
                <div class="planner-form-grid">
                  <label>
                    科目
                    <input v-model="planForm.subject" maxlength="120" placeholder="数学 / 英语 / 专业课" />
                  </label>
                  <label>
                    类型
                    <select v-model="planForm.itemType">
                      <option value="COURSE">课程</option>
                      <option value="SELF_STUDY">自习</option>
                      <option value="REVIEW">复盘</option>
                      <option value="EXAM">考试</option>
                      <option value="TASK">任务</option>
                      <option value="REST">休息</option>
                    </select>
                  </label>
                </div>
                <div class="planner-form-grid three">
                  <label>
                    日期
                    <input v-model="planForm.startDate" type="date" required />
                  </label>
                  <label>
                    开始
                    <input v-model="planForm.startTime" type="time" required />
                  </label>
                  <label>
                    结束
                    <input v-model="planForm.endTime" type="time" required />
                  </label>
                </div>
                <div class="planner-form-grid">
                  <label>
                    优先级
                    <select v-model="planForm.priority">
                      <option value="LOW">低</option>
                      <option value="MEDIUM">中</option>
                      <option value="HIGH">高</option>
                    </select>
                  </label>
                  <label>
                    状态
                    <select v-model="planForm.status">
                      <option value="TODO">待完成</option>
                      <option value="DONE">已完成</option>
                      <option value="SKIPPED">已跳过</option>
                    </select>
                  </label>
                </div>
                <label>
                  地点
                  <input v-model="planForm.location" maxlength="120" placeholder="教室 / 图书馆 / 自习室" />
                </label>
                <label>
                  说明
                  <textarea v-model="planForm.description" class="planner-note" maxlength="800" placeholder="学习内容、目标或注意事项" />
                </label>
                <div class="planner-form-actions">
                  <button class="primary-btn compact" type="submit" :disabled="loading">
                    <Save :size="17" />
                    {{ editingPlanItem ? '保存修改' : '加入规划' }}
                  </button>
                  <button v-if="editingPlanItem" class="secondary-btn slim" type="button" @click="toggleStudyPlanDone(editingPlanItem)">
                    {{ editingPlanItem.status === 'DONE' ? '标为待完成' : '标为完成' }}
                  </button>
                  <button v-if="editingPlanItem" class="icon-btn mini danger" type="button" title="删除规划" @click="deleteStudyPlanItem(editingPlanItem)">
                    <Trash2 :size="15" />
                  </button>
                </div>
              </form>
            </aside>
          </div>
          </template>

          <template v-if="planModule === 'ai'">
          <div class="planner-toolbar">
            <div class="planner-week-switch">
              <button class="secondary-btn slim" type="button" @click="shiftPlanWeek(-1)">
                <ChevronRight class="flip-x" :size="16" />
                上周
              </button>
              <button class="secondary-btn slim" type="button" @click="goCurrentPlanWeek">本周</button>
              <button class="secondary-btn slim" type="button" @click="shiftPlanWeek(1)">
                下周
                <ChevronRight :size="16" />
              </button>
            </div>
            <strong>{{ planWeekLabel }}</strong>
            <div class="planner-stats">
              <span>草稿 {{ planDraftStats.total }} 项</span>
              <span>新增 {{ planDraftStats.created }} 项</span>
              <span>待保存 {{ planDraftDirty ? '是' : '否' }}</span>
              <button class="secondary-btn slim" type="button" :disabled="!planUndoStack.length || planUndoLoading" @click="undoStudyPlanChange">
                <Undo2 :size="16" />
                撤回
              </button>
              <button class="secondary-btn slim" type="button" @click="planModule = 'manual'">
                <CalendarDays :size="16" />
                自我规划
              </button>
            </div>
          </div>

          <div class="planner-ai-layout">
            <section class="planner-ai-panel large">
              <div class="section-head split">
                <div>
                  <h3>AI 时间规划</h3>
                  <p>{{ aiSettings.aiRole }}</p>
                </div>
                <div class="planner-ai-actions">
                  <button class="secondary-btn slim" type="button" @click="activePage = 'settings'">
                    <Settings :size="16" />
                    设置
                  </button>
                  <button class="secondary-btn slim danger-text" type="button" @click="clearPlanAiChat">
                    <Trash2 :size="16" />
                    清空聊天
                  </button>
                </div>
              </div>

              <div class="planner-ai-log tall">
                <article v-for="(message, index) in planAiMessages" :key="index" :class="['message', message.role]">
                  <div class="message-content">{{ displayPlanAiMessage(message) }}</div>
                </article>
                <article v-if="planAiLoading || planGenerateLoading || planSaveLoading" class="message assistant pending-message">
                  <LoaderCircle :size="18" />
                  <span>{{ planSaveLoading ? '正在保存草稿规划…' : planGenerateLoading ? '正在更新草稿预览…' : '正在分析当前规划…' }}</span>
                </article>
              </div>

              <form class="planner-ai-box" @submit.prevent="sendPlanAiMessage">
                <textarea
                  v-model="planAiInput"
                  :disabled="planAiLoading || planGenerateLoading || planSaveLoading"
                  placeholder="例如：我晚上效率高，帮我把数学和专业课错开安排。发送后会直接更新右侧草稿预览。"
                />
                <div class="planner-ai-actions">
                  <button class="secondary-btn slim" type="submit" :disabled="planAiLoading || planGenerateLoading || planSaveLoading || !planAiInput.trim()">
                    <Send :size="16" />
                    发送并预览
                  </button>
                  <button class="primary-btn compact" type="button" :disabled="!planDraftDirty || planSaveLoading || planGenerateLoading || planAiLoading" @click="savePlanAiDraft">
                    <Save :size="17" />
                    保存到日程
                  </button>
                </div>
              </form>
            </section>

            <aside class="planner-draft-panel">
              <div class="section-head split">
                <div>
                  <h3>草稿缩略图</h3>
                  <p>这里只能查看预览，不会直接修改真实日程。</p>
                </div>
                <button class="secondary-btn slim" type="button" :disabled="!planDraftDirty" @click="resetPlanDraft">
                  <RotateCcw :size="16" />
                  重置草稿
                </button>
              </div>
              <section class="planner-mini-board" aria-label="AI 草稿日程预览">
                <article
                  v-for="day in planWeekDays"
                  :key="day.iso"
                  class="planner-mini-day"
                  :class="{ today: isToday(day.iso) }"
                >
                  <header>
                    <span>{{ day.weekday }}</span>
                    <strong>{{ day.monthDay }}</strong>
                  </header>
                  <div class="planner-mini-list">
                    <div
                      v-for="item in planDraftItemsByDate[day.iso]"
                      :key="item.id"
                      class="plan-mini-block"
                      :class="[`type-${item.itemType}`, `priority-${item.priority}`, { draft: item.id < 0, done: item.status === 'DONE' }]"
                    >
                      <time>{{ normalizeTimeValue(item.startTime) }}-{{ normalizeTimeValue(item.endTime) }}</time>
                      <strong>{{ item.title }}</strong>
                      <span>{{ item.subject || planTypeLabel(item.itemType) }}</span>
                    </div>
                    <div v-if="!planDraftItemsByDate[day.iso]?.length" class="planner-mini-empty">空档</div>
                  </div>
                </article>
              </section>

              <div v-if="planLastOperations.length" class="planner-ops">
                <strong>草稿操作</strong>
                <span v-for="operation in planLastOperations" :key="`${operation.operation}-${operation.id}-${operation.title}`">
                  {{ operation.operation }} · {{ operation.title || operation.id }} · {{ operation.detail }}
                </span>
              </div>
            </aside>
          </div>
          </template>
        </section>
</template>
<script setup>
import { useAppContext } from '../composables/appContext'

const { CalendarDays, CalendarPlus, ChevronRight, LoaderCircle, RotateCcw, Save, Send, Settings, Sparkles, Trash2, Undo2, activePage, loading, planModule, editingPlanItem, planAiMessages, planAiInput, planAiLoading, planGenerateLoading, planSaveLoading, planUndoLoading, planLastOperations, planUndoStack, planForm, aiSettings, planWeekDays, planWeekLabel, studyPlanItemsByDate, planDraftItemsByDate, planDraftDirty, planDraftStats, planStats, isToday, normalizeTimeValue, planTypeLabel, resetPlanDraft, undoStudyPlanChange, shiftPlanWeek, goCurrentPlanWeek, openNewStudyPlan, openPlanModule, openPlannerAiPage, editStudyPlan, resetPlanForm, saveStudyPlanItem, deleteStudyPlanItem, toggleStudyPlanDone, displayPlanAiMessage, clearPlanAiChat, sendPlanAiMessage, savePlanAiDraft } = useAppContext()
</script>
