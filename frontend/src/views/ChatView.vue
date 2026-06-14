<!-- [SEARCH:VIEW_CHAT] 知识问答页面，展示流式回答、引用来源、练题模式和对话历史。 -->
<template>
<section class="page-panel chat-page">
          <div class="qa-toolbar">
            <div class="mode-tabs">
              <button :class="{ active: chatForm.mode === 'QA' }" @click="setChatMode('QA')">答疑助手</button>
              <button :class="{ active: chatForm.mode === 'TEACHER' }" @click="setChatMode('TEACHER')">定制练题</button>
            </div>
            <div class="ai-summary">
              <Bot :size="18" />
              <span>{{ aiSettings.aiRole }}</span>
              <button class="secondary-btn slim" type="button" @click="activePage = 'settings'">
                <Settings :size="16" />
                设置
              </button>
            </div>
            <div class="history-actions">
              <div class="history-menu">
                <button class="secondary-btn slim history-trigger" type="button" :disabled="!activeFolder" @click="historyPanelOpen = !historyPanelOpen">
                  <Clock :size="16" />
                  历史记录
                  <span>{{ folderChatHistories.length }}</span>
                </button>
                <div v-if="historyPanelOpen" class="history-popover">
                  <div
                    v-for="item in folderChatHistories"
                    :key="item.id"
                    class="history-row"
                    :class="{ active: item.id === activeConversationIds[item.mode] && item.mode === chatForm.mode }"
                  >
                    <button type="button" class="history-title" @click="openConversation(item)">
                      <strong>{{ item.title }}</strong>
                      <span>{{ chatModeLabel(item.mode) }} · {{ formatHistoryTime(item.updatedAt) }}</span>
                    </button>
                    <button class="icon-btn mini danger history-delete" type="button" title="删除历史记录" @click="deleteChatHistory(item)">
                      <Trash2 :size="14" />
                    </button>
                  </div>
                  <div v-if="folderChatHistories.length === 0" class="history-empty">当前文件夹暂无历史记录</div>
                </div>
              </div>
              <button class="secondary-btn slim" type="button" :disabled="chatForm.useKnowledgeBase && !activeFolder" @click="startNewConversation">
                <RotateCcw :size="16" />
                新对话
              </button>
              <button class="secondary-btn slim" type="button" :disabled="!activeFolder || !currentChatHasMessages || noteLoading" @click="createNoteFromConversation">
                <NotebookPen :size="16" />
                {{ noteLoading ? '整理中' : '整理为笔记' }}
              </button>
            </div>
          </div>

          <div class="chat-log">
            <article v-for="(message, index) in messages" :key="index" :class="['message', message.role]">
              <div class="message-content">
                <template v-for="(part, partIndex) in messageParts(message)" :key="partIndex">
                  <button
                    v-if="part.type === 'citation'"
                    class="citation-link"
                    type="button"
                    :title="sourceLabel(part.source)"
                    @click="showSource(part.source)"
                  >
                    {{ part.text }}
                  </button>
                  <span v-else v-html="renderRichText(part.text)"></span>
                </template>
              </div>
              <div v-if="chatForm.mode === 'TEACHER' && message.role === 'assistant' && message.teacherQuestion" class="teacher-actions">
                <button class="secondary-btn slim" type="button" :disabled="message.addedToMistake" @click="addTeacherMessageToMistake(message)">添加到错题</button>
                <button class="secondary-btn slim" type="button" @click="nextTeacherQuestion">下一题</button>
              </div>
            </article>
            <article v-if="chatLoading" class="message assistant pending-message" aria-live="polite">
              <LoaderCircle :size="18" />
              <span>{{ pendingChatText }}</span>
            </article>
            <div
              v-if="activeSource"
              class="source-popover"
              role="dialog"
              aria-label="依据来源"
              @dblclick="openSourceFile(activeSource)"
            >
              <div class="source-popover-head">
                <strong>{{ sourceLabel(activeSource) }}</strong>
                <button class="icon-btn mini" type="button" title="关闭" @click="activeSource = null">×</button>
              </div>
              <p>{{ activeSource.excerpt }}</p>
              <div class="source-stats">
                <span>掌握度 {{ formatPercent(activeSource.masteryRate) }}</span>
                <span>引用 {{ activeSource.citeCount || 0 }}</span>
                <span>对 {{ activeSource.correctHitCount || 0 }} / 错 {{ activeSource.wrongHitCount || 0 }}</span>
              </div>
              <div class="source-actions">
                <template v-if="!activeSource.feedbackType">
                  <button
                    class="secondary-btn slim source-feedback-btn clear"
                    type="button"
                    :disabled="activeSource.feedbackPending"
                    @click="feedbackActiveSource('CLEAR')"
                  >
                    很清楚
                  </button>
                  <button
                    class="secondary-btn slim source-feedback-btn forgot"
                    type="button"
                    :disabled="activeSource.feedbackPending"
                    @click="feedbackActiveSource('FORGOT')"
                  >
                    忘记了
                  </button>
                </template>
                <span
                  v-else
                  class="source-feedback-result"
                  :class="activeSource.feedbackType === 'CLEAR' ? 'clear' : 'forgot'"
                >
                  已记录：{{ activeSource.feedbackType === 'CLEAR' ? '很清楚' : '忘记了' }}
                </span>
              </div>
              <span>双击窗口可打开完整文件，并定位到这段依据。</span>
            </div>
            <div v-if="messages.length === 0" class="empty-chat">
              <MessageSquare :size="30" />
              <strong>{{ emptyChatTitle }}</strong>
              <span>{{ emptyChatDescription }}</span>
            </div>
          </div>

          <form class="question-box" @submit.prevent="ask">
            <div class="question-main">
              <textarea v-model="chatForm.question" :disabled="chatInputDisabled" :placeholder="chatPlaceholder" />
              <div v-if="chatForm.mode !== 'TEACHER'" class="chat-options">
                <label class="chat-toggle">
                  <input v-model="chatForm.useKnowledgeBase" type="checkbox" :disabled="chatLoading" @change="handleKnowledgeModeChange" />
                  <span>使用知识库</span>
                </label>
                <label class="chat-toggle" :class="{ muted: !chatForm.useKnowledgeBase }">
                  <input v-model="chatForm.withCitations" type="checkbox" :disabled="!chatForm.useKnowledgeBase || !activeFolder || chatLoading" />
                  <span>引用来源</span>
                </label>
                <label class="chat-toggle" :class="{ muted: !chatForm.useKnowledgeBase }">
                  <input v-model="chatForm.deepAnswer" type="checkbox" :disabled="!chatForm.useKnowledgeBase || !activeFolder || chatLoading" />
                  <span>深度回答</span>
                </label>
              </div>
            </div>
            <button class="primary-btn" type="submit" :disabled="!canSubmitChat">
              <LoaderCircle v-if="chatLoading" :size="18" class="spin-icon" />
              <Send v-else :size="18" />
              {{ chatLoading ? '生成中' : (chatForm.mode === 'TEACHER' ? '生成问题' : '发送') }}
            </button>
          </form>
        </section>
</template>
<script setup>
import { useAppContext } from '../composables/appContext'

const { Bot, Clock, LoaderCircle, MessageSquare, NotebookPen, RotateCcw, Send, Settings, Trash2, activeFolder, activePage, chatLoading, noteLoading, activeConversationIds, folderChatHistories, historyPanelOpen, activeSource, chatForm, aiSettings, messages, currentChatHasMessages, chatInputDisabled, canSubmitChat, chatPlaceholder, pendingChatText, emptyChatTitle, emptyChatDescription, startNewConversation, openConversation, deleteChatHistory, chatModeLabel, formatHistoryTime, setChatMode, handleKnowledgeModeChange, renderRichText, ask, nextTeacherQuestion, addTeacherMessageToMistake, feedbackActiveSource, createNoteFromConversation, showSource, messageParts, sourceLabel, formatPercent, openSourceFile } = useAppContext()
</script>
