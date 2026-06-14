<!-- [SEARCH:VIEW_HOME] 首页，展示考试倒计时和当前时间段的学习任务。 -->
<template>
<section class="page-panel home-page">
          <div class="home-hero">
            <div class="home-countdown">
              <template v-if="savedExamDate">
                <div v-if="examCountdownDays > 0" class="countdown-line">
                  <span class="countdown-title">成为研究生倒计时</span>
                  <div class="countdown-value" title="双击调整考研时间" @dblclick="openExamDatePicker">
                    <strong class="countdown-display">{{ examCountdownDays }}</strong>
                    <span>天</span>
                  </div>
                </div>
                <strong v-else-if="examCountdownDays === 0" class="countdown-display" title="双击调整考研时间" @dblclick="openExamDatePicker">今天</strong>
                <strong v-else class="countdown-display" title="双击调整考研时间" @dblclick="openExamDatePicker">已结束</strong>
                <span v-if="examCountdownDays === 0">稳住节奏，认真完成这一场</span>
                <span v-else-if="examCountdownDays < 0">可以重新设置下一次目标日期</span>
              </template>
              <template v-else>
                <strong class="countdown-display placeholder" title="双击设置考研时间" @dblclick="openExamDatePicker">--:--</strong>
                <span>设置考研时间后，首页会自动显示倒计时。</span>
              </template>
              <input ref="examDateInput" v-model="examDate" class="exam-date-trigger" type="date" :min="todayIso" aria-label="选择考研时间" @change="saveExamDate" />
            </div>
          </div>

          <section class="home-task-panel" aria-label="当前任务">
            <div class="section-head split">
              <div>
                <h3>当前任务</h3>
                <p>根据学习规划中今天的安排自动显示。</p>
              </div>
              <button class="secondary-btn" type="button" @click="setActivePage('planner')">
                <CalendarDays :size="17" />
                去学习规划
              </button>
            </div>

            <article v-if="currentHomeTask" class="current-task-card" :class="[`type-${currentHomeTask.itemType}`, `priority-${currentHomeTask.priority}`]">
              <div class="current-task-time">
                <Clock :size="18" />
                <span>{{ currentHomeTaskState }}</span>
                <time>{{ normalizeTimeValue(currentHomeTask.startTime) }} - {{ normalizeTimeValue(currentHomeTask.endTime) }}</time>
              </div>
              <div class="current-task-main">
                <strong>{{ currentHomeTask.title }}</strong>
                <span>{{ currentHomeTask.subject || planTypeLabel(currentHomeTask.itemType) }}</span>
                <p v-if="currentHomeTask.description">{{ currentHomeTask.description }}</p>
              </div>
              <span class="status-pill compact">{{ planPriorityLabel(currentHomeTask.priority) }}优先级</span>
            </article>

            <div v-else class="empty-state home-empty-task">
              <CalendarPlus :size="30" />
              <strong>当前暂未安排，那就休息一下吧~</strong>
              <span>也可以去学习规划里为今天添加一个任务。</span>
            </div>
          </section>
        </section>
</template>
<script setup>
import { useAppContext } from '../composables/appContext'

const { CalendarDays, CalendarPlus, Clock, savedExamDate, examDate, examDateInput, todayIso, examCountdownDays, currentHomeTask, currentHomeTaskState, normalizeTimeValue, planTypeLabel, planPriorityLabel, saveExamDate, openExamDatePicker, setActivePage } = useAppContext()
</script>
