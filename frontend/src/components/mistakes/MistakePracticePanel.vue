<!-- [SEARCH:VIEW_MISTAKE_PRACTICE] 错题复习面板，负责题组进度、计时和作答结果提交。 -->
<template>
<div class="practice-panel">
            <div class="section-head split">
              <div>
                <h3>刷错题</h3>
                <p>题目从未掌握错题中随机抽取，结束前只显示题目和切换按钮。</p>
              </div>
              <div class="mistake-section-actions">
                <div v-if="practiceStarted && practiceForm.timed && !practiceFinished" class="practice-clock">
                  <Timer :size="17" />
                  {{ practiceClock }}
                </div>
                <button class="secondary-btn slim" type="button" @click="backToMistakeMenu">返回</button>
              </div>
            </div>

            <form v-if="!practiceStarted" class="practice-form" @submit.prevent="startPractice">
              <label>
                题数
                <input v-model.number="practiceForm.count" type="number" min="1" max="100" />
              </label>
              <label class="check-row">
                <input v-model="practiceForm.timed" type="checkbox" />
                <span>开启倒计时</span>
              </label>
              <label v-if="practiceForm.timed">
                分钟
                <input v-model.number="practiceForm.minutes" type="number" min="1" max="240" />
              </label>
              <div class="practice-subject-filter">
                <strong>科目筛选</strong>
                <div class="mistake-status-picker">
                  <button
                  v-for="item in mistakeSubjectOptions"
                  :key="item.folder.id"
                  class="status-pill"
                  :class="{ active: practiceForm.subjectTagIds.includes(item.tag.id) }"
                  type="button"
                  @click="toggleIdInArray(practiceForm.subjectTagIds, item.tag.id)"
                >
                  {{ item.folder.name }}
                </button>
                  <span v-if="mistakeSubjectOptions.length === 0" class="empty-inline">暂无学科文件夹</span>
                </div>
              </div>
              <button class="primary-btn compact" type="submit" :disabled="loading">
                <BookOpenCheck :size="17" />
                开始刷题
              </button>
            </form>

            <div v-else-if="practiceCurrentQuestion" class="practice-board">
              <div class="practice-head">
                <strong>第 {{ practiceIndex + 1 }} / {{ practiceQuestions.length }} 题</strong>
                <button class="secondary-btn slim" type="button" @click="finishPractice">结束并查看答案</button>
              </div>
              <article class="mistake-question">
                <div v-if="practiceCurrentQuestion.questionText" v-html="renderRichText(practiceCurrentQuestion.questionText)"></div>
                <div v-if="practiceCurrentQuestion.questionAttachments?.length" class="saved-image-strip">
                  <figure
                    v-for="attachment in practiceCurrentQuestion.questionAttachments"
                    :key="attachment.id || attachment.displayName"
                    @dblclick="enlargeSavedAttachment(attachment)"
                  >
                    <img v-if="attachment.id && attachmentPreviewUrls[attachment.id]" :src="attachmentPreviewUrls[attachment.id]" alt="题目图片" />
                    <img v-else-if="!attachment.id && questionPreviewUrls[practiceCurrentQuestion.id]" :src="questionPreviewUrls[practiceCurrentQuestion.id]" alt="题目图片" />
                    <figcaption>{{ attachment.displayName || attachment.originalName }}</figcaption>
                  </figure>
                </div>
                <img v-if="questionPreviewUrls[practiceCurrentQuestion.id] && !practiceCurrentQuestion.questionAttachments?.length" :src="questionPreviewUrls[practiceCurrentQuestion.id]" alt="题目附件" />
                <span v-else-if="practiceCurrentQuestion.hasQuestionFile && !practiceCurrentQuestion.questionAttachments?.length">
                  <Image :size="15" />
                  {{ practiceCurrentQuestion.questionOriginalName || '题目附件' }}
                </span>
              </article>
              <div class="practice-actions">
                <button class="secondary-btn slim" type="button" :disabled="practiceIndex === 0" @click="nextPracticeQuestion(-1)">上一题</button>
                <button class="secondary-btn slim" type="button" :disabled="practiceIndex >= practiceQuestions.length - 1" @click="nextPracticeQuestion(1)">下一题</button>
                <button class="secondary-btn slim" type="button" @click="closePractice">退出</button>
              </div>
              <div v-if="practiceFinished" class="practice-answer">
                <strong>答案解析</strong>
                <div v-if="practiceCurrentQuestion.solutionText" v-html="renderRichText(practiceCurrentQuestion.solutionText)"></div>
                <div v-if="practiceCurrentQuestion.solutionAttachments?.length" class="saved-image-strip">
                  <figure
                    v-for="attachment in practiceCurrentQuestion.solutionAttachments"
                    :key="attachment.id || attachment.displayName"
                    @dblclick="enlargeSavedAttachment(attachment)"
                  >
                    <img v-if="attachment.id && attachmentPreviewUrls[attachment.id]" :src="attachmentPreviewUrls[attachment.id]" alt="解析图片" />
                    <img v-else-if="!attachment.id && solutionPreviewUrls[practiceCurrentQuestion.id]" :src="solutionPreviewUrls[practiceCurrentQuestion.id]" alt="解析图片" />
                    <figcaption>{{ attachment.displayName || attachment.originalName }}</figcaption>
                  </figure>
                </div>
                <img v-if="solutionPreviewUrls[practiceCurrentQuestion.id] && !practiceCurrentQuestion.solutionAttachments?.length" :src="solutionPreviewUrls[practiceCurrentQuestion.id]" alt="解析图片" />
                <span v-if="!practiceCurrentQuestion.solutionText && !practiceCurrentQuestion.hasSolutionFile">这道题还没有解析。</span>
                <div class="practice-actions">
                  <button class="secondary-btn slim" type="button" :disabled="practiceResultFor(practiceCurrentQuestion)" @click="recordPracticeResult(practiceCurrentQuestion, true)">写对了</button>
                  <button class="secondary-btn slim" type="button" :disabled="practiceResultFor(practiceCurrentQuestion)" @click="recordPracticeResult(practiceCurrentQuestion, false)">写错了</button>
                  <span v-if="practiceResultFor(practiceCurrentQuestion)" class="empty-inline">
                    已记录{{ practiceResultFor(practiceCurrentQuestion).correct ? '写对了' : '写错了' }}，更新 {{ practiceResultFor(practiceCurrentQuestion).updatedChunkCount }} 个知识片段
                  </span>
                </div>
              </div>
            </div>
            <div v-else class="empty-note">暂无未掌握错题，先上传或把错题标记为未掌握。</div>
          </div>
</template>
<script setup>
import { useAppContext } from '../../composables/appContext'

const { BookOpenCheck, Image, Timer, loading, practiceForm, practiceQuestions, practiceIndex, practiceStarted, practiceFinished, solutionPreviewUrls, questionPreviewUrls, attachmentPreviewUrls, mistakeSubjectOptions, practiceCurrentQuestion, practiceClock, backToMistakeMenu, enlargeSavedAttachment, toggleIdInArray, startPractice, finishPractice, closePractice, nextPracticeQuestion, practiceResultFor, recordPracticeResult, renderRichText } = useAppContext()
</script>
