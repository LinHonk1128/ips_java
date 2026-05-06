<template>
  <main class="app-shell">
    <section v-if="!session" class="auth-panel" aria-label="登录">
      <div class="auth-copy">
        <div class="brand-mark">研</div>
        <h1>智能考研系统</h1>
        <p>整理教材、资料和笔记，构建自己的科目知识库，再用大模型做答疑和教师式追问。</p>
      </div>
      <form class="auth-card" @submit.prevent="submitAuth">
        <div class="segmented">
          <button type="button" :class="{ active: authMode === 'login' }" @click="authMode = 'login'">登录</button>
          <button type="button" :class="{ active: authMode === 'register' }" @click="authMode = 'register'">注册</button>
        </div>
        <label>
          用户名
          <input v-model="authForm.username" required minlength="3" autocomplete="username" />
        </label>
        <label>
          密码
          <input v-model="authForm.password" required minlength="6" type="password" autocomplete="current-password" />
        </label>
        <label v-if="authMode === 'register'">
          昵称
          <input v-model="authForm.displayName" maxlength="128" />
        </label>
        <button class="primary-btn" type="submit" :disabled="loading">
          <LogIn :size="18" />
          {{ authMode === 'login' ? '进入学习空间' : '创建账号' }}
        </button>
        <p v-if="error" class="error-text">{{ error }}</p>
      </form>
    </section>

    <section v-else class="workspace">
      <aside class="sidebar">
        <div class="side-top">
          <div class="brand-line">
            <div class="brand-mark small">研</div>
            <div>
              <strong>智能考研</strong>
              <span>{{ session.displayName || session.username }}</span>
            </div>
          </div>
          <button class="icon-btn" title="退出登录" @click="logout"><LogOut :size="18" /></button>
        </div>

        <nav class="page-nav" aria-label="功能页面">
          <button
            v-for="item in navItems"
            :key="item.key"
            :class="{ active: activePage === item.key }"
            @click="activePage = item.key"
          >
            <component :is="item.icon" :size="18" />
            <span>{{ item.label }}</span>
          </button>
        </nav>

        <div class="folder-context">
          <div class="context-head">
            <span>当前文件夹</span>
            <button class="icon-btn mini" title="刷新文件夹" @click="loadFolders"><RefreshCw :size="15" /></button>
          </div>
          <button class="folder-item" :class="{ selected: !activeFolder }" @click="selectRoot">
            <FolderOpen :size="18" />
            <span>我的资料</span>
          </button>
          <button
            v-for="folder in folderTree"
            :key="folder.id"
            class="folder-item"
            :class="{ selected: activeFolder?.id === folder.id }"
            :style="{ '--folder-indent': `${(folder.depth - 1) * 18}px` }"
            @click="selectFolder(folder)"
          >
            <Folder :size="18" />
            <span>{{ folder.name }}</span>
          </button>
          <div v-if="folders.length === 0" class="empty-note">还没有文件夹，请先到“我的资料”页面创建。</div>
        </div>
      </aside>

      <section class="content-area">
        <header class="topbar">
          <div>
            <h2>{{ pageTitle }}</h2>
            <p>{{ pageDescription }}</p>
          </div>
          <div class="folder-chip" :class="{ muted: !activeFolder }">
            <FolderOpen :size="18" />
            <span>{{ currentFolderName }}</span>
          </div>
        </header>

        <p v-if="error" class="error-banner">{{ error }}</p>

        <section v-if="activePage === 'library'" class="page-panel library-page">
          <div class="section-head split">
            <div>
              <h3>创建文件夹</h3>
              <p>{{ createFolderHint }}</p>
            </div>
            <button class="secondary-btn" type="button" :disabled="!activeFolder" @click="useCurrentFolderAsKnowledgeBase">
              <MessageSquare :size="17" />
              以当前文件夹为知识库
            </button>
          </div>
          <form class="folder-form wide" @submit.prevent="createFolder">
            <input v-model="folderForm.name" :placeholder="folderNamePlaceholder" :disabled="!canCreateFolder" required />
            <input v-model="folderForm.description" placeholder="描述，可选" :disabled="!canCreateFolder" />
            <button class="primary-btn compact" type="submit" :disabled="loading || !canCreateFolder">
              <FolderPlus :size="17" />
              创建
            </button>
          </form>

          <div class="folder-breadcrumb" aria-label="文件夹路径">
            <button class="secondary-btn slim" type="button" :class="{ active: !activeFolder }" @click="selectRoot">
              我的资料
            </button>
            <template v-for="folder in folderPath" :key="folder.id">
              <ChevronRight :size="16" />
              <button class="secondary-btn slim" type="button" @click="selectFolder(folder)">
                {{ folder.name }}
              </button>
            </template>
          </div>

          <div class="folder-board">
            <div
              v-for="folder in visibleFolders"
              :key="folder.id"
              class="folder-card"
              :class="{ selected: activeFolder?.id === folder.id }"
              role="button"
              tabindex="0"
              @click="selectFolder(folder)"
              @keydown.enter="selectFolder(folder)"
              @keydown.space.prevent="selectFolder(folder)"
            >
              <Folder :size="22" />
              <strong>{{ folder.name }}</strong>
              <span>{{ folder.description || '暂无描述' }}</span>
              <button class="icon-btn mini folder-edit-btn" type="button" title="修改文件夹" @click.stop="openEditFolder(folder)">
                <Pencil :size="15" />
              </button>
              <button class="icon-btn mini danger folder-delete-btn" type="button" title="删除文件夹" @click.stop="deleteFolder(folder)">
                <Trash2 :size="15" />
              </button>
            </div>
            <div v-if="visibleFolders.length === 0" class="empty-state">
              <FolderPlus :size="30" />
              <strong>{{ emptyFolderTitle }}</strong>
              <span>{{ emptyFolderDescription }}</span>
            </div>
          </div>

          <form v-if="editingFolder" class="folder-edit-panel" @submit.prevent="saveFolderEdit">
            <strong>修改文件夹</strong>
            <input v-model="editFolderForm.name" required maxlength="120" />
            <input v-model="editFolderForm.description" maxlength="400" placeholder="描述，可选" />
            <button class="primary-btn compact" type="submit" :disabled="loading">
              <Save :size="16" />
              保存
            </button>
            <button class="secondary-btn slim" type="button" @click="cancelEditFolder">取消</button>
          </form>

          <div class="section-head split">
            <div>
              <h3>文件夹中的文件</h3>
              <p>{{ activeFolder ? '这里仅展示当前文件夹的文件。上传和编辑请进入“上传编辑”页面。' : '选择一个文件夹后查看文件列表。' }}</p>
            </div>
            <button class="secondary-btn" :disabled="!activeFolder" @click="activePage = 'editor'">
              <Upload :size="17" />
              去上传
            </button>
          </div>
          <div class="file-list roomy">
            <div
              v-for="file in files"
              :key="file.id"
              class="file-row"
              :class="{ selected: activeFile?.id === file.id }"
              role="button"
              tabindex="0"
              @click="selectFile(file)"
              @keydown.enter="selectFile(file)"
              @keydown.space.prevent="selectFile(file)"
            >
              <FileText :size="18" />
              <div class="file-main">
                <span class="file-name">{{ displayFileName(file) }}</span>
                <div class="file-meta">
                  <b>{{ tagLabel(file.tag) }}</b>
                  <em class="knowledge-badge" :class="{ off: !file.knowledgeEnabled }">
                    {{ file.knowledgeEnabled ? '已加入知识库' : '未加入知识库' }}
                  </em>
                </div>
              </div>
              <div class="file-actions">
                <button
                  class="secondary-btn slim knowledge-action"
                  type="button"
                  @click.stop="toggleKnowledge(file)"
                >
                  {{ file.knowledgeEnabled ? '移出' : '加入' }}
                </button>
                <button class="icon-btn mini" type="button" title="移动文件" @click.stop="openMoveFile(file)">
                  <MoveRight :size="15" />
                </button>
                <button class="icon-btn mini danger" type="button" title="删除文件" @click.stop="deleteFile(file)">
                  <Trash2 :size="15" />
                </button>
              </div>
            </div>
            <div v-if="activeFolder && files.length === 0" class="empty-note">当前文件夹还没有文件。</div>
          </div>

          <form v-if="movingFile" class="file-move-panel" @submit.prevent="moveFile">
            <strong>移动“{{ displayFileName(movingFile) }}”到</strong>
            <select v-model="moveFileTargetId">
              <option v-for="target in fileMoveTargetOptions" :key="target.id" :value="String(target.id)">
                {{ folderOptionLabel(target) }}
              </option>
            </select>
            <button class="primary-btn compact" type="submit" :disabled="loading || !canSubmitFileMove">
              <MoveRight :size="16" />
              移动
            </button>
            <button class="secondary-btn slim" type="button" @click="cancelMoveFile">取消</button>
          </form>
        </section>

        <section v-else-if="activePage === 'chat'" class="page-panel chat-page">
          <div class="qa-toolbar">
            <div class="mode-tabs">
              <button :class="{ active: chatForm.mode === 'QA' }" @click="setChatMode('QA')">答疑模式</button>
              <button :class="{ active: chatForm.mode === 'TEACHER' }" @click="setChatMode('TEACHER')">教师模式</button>
            </div>
            <div class="ai-summary">
              <Bot :size="18" />
              <span>{{ aiSettings.aiRole }}</span>
              <b>{{ aiSettings.chatModel }}</b>
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
                  <button
                    v-for="item in folderChatHistories"
                    :key="item.id"
                    type="button"
                    class="history-title"
                    :class="{ active: item.id === activeConversationIds[item.mode] && item.mode === chatForm.mode }"
                    @click="openConversation(item)"
                  >
                    <strong>{{ item.title }}</strong>
                    <span>{{ chatModeLabel(item.mode) }} · {{ formatHistoryTime(item.updatedAt) }}</span>
                  </button>
                  <div v-if="folderChatHistories.length === 0" class="history-empty">当前文件夹暂无历史记录</div>
                </div>
              </div>
              <button class="secondary-btn slim" type="button" :disabled="!activeFolder" @click="startNewConversation">
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
            </article>
            <article v-if="chatLoading" class="message assistant pending-message" aria-live="polite">
              <LoaderCircle :size="18" />
              <span>正在检索知识库并生成回答…</span>
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
              <span>双击窗口可打开完整文件，并定位到这段依据。</span>
            </div>
            <div v-if="messages.length === 0" class="empty-chat">
              <MessageSquare :size="30" />
              <strong>{{ activeFolder ? '开始围绕当前知识库提问' : '先选择一个文件夹' }}</strong>
              <span>答疑模式会追溯资料来源，教师模式会根据知识点向你提问。</span>
            </div>
          </div>

          <form class="question-box" @submit.prevent="ask">
            <textarea v-model="chatForm.question" :disabled="!activeFolder || chatLoading" placeholder="输入问题，或在教师模式下输入：开始抽问本章重点" />
            <button class="primary-btn" type="submit" :disabled="!activeFolder || !chatForm.question || loading">
              <LoaderCircle v-if="chatLoading" :size="18" class="spin-icon" />
              <Send v-else :size="18" />
              {{ chatLoading ? '生成中' : '发送' }}
            </button>
          </form>
        </section>

        <section v-else-if="activePage === 'editor'" class="page-panel editor-page">
          <form class="upload-strip" @submit.prevent="uploadFile">
            <select v-model="uploadTag" :disabled="!activeFolder">
              <option value="TEXTBOOK">教材</option>
              <option value="MATERIAL">资料</option>
              <option value="NOTE">笔记</option>
              <option value="EXERCISE">习题</option>
              <option value="OTHER">其他</option>
            </select>
            <input ref="fileInput" type="file" :disabled="!activeFolder" accept=".pdf,.doc,.docx,.txt,.md,.png,.jpg,.jpeg,.webp" />
            <button class="primary-btn compact" type="submit" :disabled="!activeFolder || loading">
              <Upload :size="17" />
              上传并扫描
            </button>
          </form>

          <div class="editor-layout">
            <div class="file-list editor-list">
              <div
                v-for="file in files"
                :key="file.id"
                class="file-row"
                :class="{ selected: activeFile?.id === file.id }"
                role="button"
                tabindex="0"
                @click="selectFile(file)"
                @keydown.enter="selectFile(file)"
                @keydown.space.prevent="selectFile(file)"
              >
                <FileText :size="18" />
                <div class="file-main">
                  <span class="file-name">{{ displayFileName(file) }}</span>
                  <div class="file-meta">
                    <b>{{ tagLabel(file.tag) }}</b>
                    <em class="knowledge-badge" :class="{ off: !file.knowledgeEnabled }">
                      {{ file.knowledgeEnabled ? '已加入知识库' : '未加入知识库' }}
                    </em>
                  </div>
                </div>
                <div class="file-actions">
                  <button
                    class="secondary-btn slim knowledge-action"
                    type="button"
                    @click.stop="toggleKnowledge(file)"
                  >
                    {{ file.knowledgeEnabled ? '移出' : '加入' }}
                  </button>
                  <button class="icon-btn mini" type="button" title="移动文件" @click.stop="openMoveFile(file)">
                    <MoveRight :size="15" />
                  </button>
                  <button class="icon-btn mini danger" type="button" title="删除文件" @click.stop="deleteFile(file)">
                    <Trash2 :size="15" />
                  </button>
                </div>
              </div>
              <form v-if="movingFile" class="file-move-panel compact-panel" @submit.prevent="moveFile">
                <strong>移动“{{ displayFileName(movingFile) }}”到</strong>
                <select v-model="moveFileTargetId">
                  <option v-for="target in fileMoveTargetOptions" :key="target.id" :value="String(target.id)">
                    {{ folderOptionLabel(target) }}
                  </option>
                </select>
                <button class="primary-btn compact" type="submit" :disabled="loading || !canSubmitFileMove">
                  <MoveRight :size="16" />
                  移动
                </button>
                <button class="secondary-btn slim" type="button" @click="cancelMoveFile">取消</button>
              </form>
              <div v-if="activeFolder && files.length === 0" class="empty-note">上传 PDF、图片、Word 或文本文件后，可在右侧校正文档文本。</div>
              <div v-if="!activeFolder" class="empty-note">请先选择一个文件夹。</div>
            </div>

            <div v-if="activeFile" class="editor-panel">
              <div class="editor-head">
                <strong>{{ displayFileName(activeFile) }}</strong>
                <select v-model="activeFile.tag">
                  <option value="TEXTBOOK">教材</option>
                  <option value="MATERIAL">资料</option>
                  <option value="NOTE">笔记</option>
                  <option value="EXERCISE">习题</option>
                  <option value="OTHER">其他</option>
                </select>
              </div>
              <div class="page-toolbar">
                <button class="secondary-btn slim" type="button" :disabled="activeFilePageIndex === 0" @click="goFilePage(activeFilePageIndex - 1)">
                  上一页
                </button>
                <select :value="activeFilePageIndex" @change="goFilePage(Number($event.target.value))">
                  <option v-for="(page, index) in activeFilePages" :key="index" :value="index">
                    第 {{ index + 1 }} 页
                  </option>
                </select>
                <button class="secondary-btn slim" type="button" :disabled="activeFilePageIndex >= activeFilePages.length - 1" @click="goFilePage(activeFilePageIndex + 1)">
                  下一页
                </button>
                <span>共 {{ activeFilePages.length }} 页</span>
              </div>
              <div class="rich-toolbar" aria-label="文档编辑工具栏">
                <button class="icon-btn mini" type="button" title="加粗" @click="formatEditor('bold')">
                  <Bold :size="15" />
                </button>
                <button class="icon-btn mini" type="button" title="斜体" @click="formatEditor('italic')">
                  <Italic :size="15" />
                </button>
                <button class="icon-btn mini" type="button" title="下划线" @click="formatEditor('underline')">
                  <Underline :size="15" />
                </button>
                <label class="color-tool" title="文字颜色">
                  <Palette :size="15" />
                  <input v-model="editorTextColor" type="color" @input="setEditorTextColor(editorTextColor)" />
                </label>
                <button class="icon-btn mini" type="button" title="项目符号" @click="formatEditor('insertUnorderedList')">
                  <List :size="15" />
                </button>
                <button class="icon-btn mini" type="button" title="编号列表" @click="formatEditor('insertOrderedList')">
                  <ListOrdered :size="15" />
                </button>
                <button class="icon-btn mini" type="button" title="插入表格" @click="insertTable">
                  <Table2 :size="15" />
                </button>
              </div>
              <div
                ref="editorElement"
                class="rich-editor"
                contenteditable="true"
                spellcheck="false"
                @input="syncEditorContent"
                @blur="renderCurrentEditorPage"
              ></div>
              <button class="primary-btn compact" @click="saveFileText" :disabled="loading">
                <Save :size="17" />
                保存为知识库
              </button>
              <button
                class="secondary-btn compact"
                type="button"
                @click="toggleKnowledge(activeFile)"
                :disabled="loading"
              >
                {{ activeFile.knowledgeEnabled ? '移出知识库' : '加入知识库' }}
              </button>
            </div>
            <div v-else class="empty-editor">
              <ScanText :size="34" />
              <strong>选择或上传文件后开始编辑</strong>
              <span>扫描出的文本可以在这里校正，保存后会用于知识问答。</span>
            </div>
          </div>
        </section>

        <section v-else class="page-panel settings-page">
          <div class="settings-layout">
            <form class="settings-form" @submit.prevent="saveAiSettings">
              <div class="section-head">
                <h3>AI 角色与提示词</h3>
                <p>这些设置会应用到知识问答和教师模式，适合按你的复习习惯调整回答风格。</p>
              </div>

              <label>
                角色定位
                <input v-model="aiSettings.aiRole" maxlength="80" placeholder="例如：严谨的考研专业课答疑老师" />
              </label>

              <label>
                提示词
                <textarea
                  v-model="aiSettings.systemPrompt"
                  class="prompt-textarea"
                  spellcheck="false"
                  placeholder="例如：优先使用知识库内容回答；指出依据；遇到不确定信息要说明无法从资料中确认。"
                />
              </label>

              <div class="section-head">
                <h3>答题模型接口</h3>
                <p>支持 OpenAI 官方接口，也支持兼容 Chat Completions 的模型服务。</p>
              </div>

              <div class="settings-grid">
                <label>
                  模型
                  <select v-model="aiSettings.chatModel">
                    <option value="gpt-4o-mini">gpt-4o-mini</option>
                    <option value="gpt-4o">gpt-4o</option>
                    <option value="gpt-4.1-mini">gpt-4.1-mini</option>
                    <option value="qwen-plus">qwen-plus</option>
                    <option value="deepseek-chat">deepseek-chat</option>
                  </select>
                </label>
                <label>
                  Endpoint
                  <input v-model="aiSettings.chatEndpoint" placeholder="https://api.openai.com/v1/chat/completions" />
                </label>
              </div>

              <label>
                答题 API Key
                <input v-model="aiSettings.chatApiKey" type="password" autocomplete="off" placeholder="sk-..." />
              </label>

              <div class="section-head">
                <h3>Embedding 检索模型</h3>
                <p>用于 Elasticsearch 语义向量检索；保存资料时会用这里的配置生成向量索引。</p>
              </div>

              <div class="settings-grid">
                <label>
                  Embedding 模型
                  <select v-model="aiSettings.embeddingModel">
                    <option value="text-embedding-3-small">text-embedding-3-small</option>
                    <option value="text-embedding-3-large">text-embedding-3-large</option>
                    <option value="text-embedding-v3">text-embedding-v3</option>
                    <option value="bge-m3">bge-m3</option>
                  </select>
                </label>
                <label>
                  Embedding Endpoint
                  <input v-model="aiSettings.embeddingEndpoint" placeholder="https://api.openai.com/v1/embeddings" />
                </label>
              </div>

              <div class="settings-grid">
                <label>
                  Embedding API Key
                  <input v-model="aiSettings.embeddingApiKey" type="password" autocomplete="off" placeholder="sk-..." />
                </label>
                <label>
                  向量维度
                  <input v-model.number="aiSettings.embeddingDimensions" type="number" min="1" max="4096" />
                </label>
              </div>

              <div class="settings-actions">
                <button class="primary-btn compact" type="submit">
                  <Save :size="17" />
                  保存设置
                </button>
                <button class="secondary-btn" type="button" @click="resetAiSettings">
                  <RotateCcw :size="17" />
                  恢复默认
                </button>
                <span v-if="settingsSaved" class="saved-note">已保存</span>
              </div>
            </form>

            <aside class="settings-preview">
              <div class="preview-icon">
                <Bot :size="24" />
              </div>
              <strong>{{ aiSettings.aiRole || defaultAiSettings.aiRole }}</strong>
              <span>{{ aiSettings.chatModel || defaultAiSettings.chatModel }}</span>
              <p>{{ aiSettings.systemPrompt || defaultAiSettings.systemPrompt }}</p>
              <div class="key-state" :class="{ ready: aiSettings.chatApiKey }">
                <KeyRound :size="17" />
                <span>{{ aiSettings.chatApiKey ? '答题 API Key 已配置' : '未配置答题 API Key，将使用本地检索兜底回答' }}</span>
              </div>
              <div class="key-state" :class="{ ready: aiSettings.embeddingApiKey }">
                <KeyRound :size="17" />
                <span>{{ aiSettings.embeddingApiKey ? `Embedding 已配置：${aiSettings.embeddingModel}` : '未配置 Embedding，将只使用关键词检索' }}</span>
              </div>
            </aside>
          </div>
        </section>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import {
  Bold,
  Bot,
  ChevronRight,
  Clock,
  FileText,
  Folder,
  FolderOpen,
  FolderPlus,
  KeyRound,
  Library,
  LoaderCircle,
  LogIn,
  LogOut,
  Italic,
  List,
  ListOrdered,
  MessageSquare,
  MoveRight,
  NotebookPen,
  Palette,
  Pencil,
  RefreshCw,
  RotateCcw,
  Save,
  ScanText,
  Send,
  Settings,
  Table2,
  Trash2,
  Underline,
  Upload
} from 'lucide-vue-next'
import { aiSettingsApi, authApi, chatApi, clearSession, fileApi, folderApi, getSession, setSession } from './api/client'

const session = ref(getSession())
const authMode = ref('login')
const authForm = reactive({ username: '', password: '', displayName: '' })
const folderForm = reactive({ name: '', description: '' })
const editFolderForm = reactive({ name: '', description: '' })
const folders = ref([])
const files = ref([])
const activeFolder = ref(null)
const activeFile = ref(null)
const activePage = ref('library')
const uploadTag = ref('NOTE')
const fileInput = ref(null)
const editorElement = ref(null)
const activeFilePages = ref([])
const activeFilePageIndex = ref(0)
const editorTextColor = ref('#24231f')
const loading = ref(false)
const chatLoading = ref(false)
const noteLoading = ref(false)
const error = ref('')
const chatMessages = reactive({ QA: [], TEACHER: [] })
const activeConversationIds = reactive({ QA: null, TEACHER: null })
const folderChatHistories = ref([])
const historyPanelOpen = ref(false)
const activeSource = ref(null)
const movingFile = ref(null)
const editingFolder = ref(null)
const moveFileTargetId = ref('')
const defaultAiSettings = {
  aiRole: '严谨的考研答疑老师',
  systemPrompt: '优先依据当前知识库回答；给出可追溯依据；如果资料不足，明确说明无法从知识库确认。',
  chatModel: 'gpt-4o-mini',
  chatApiKey: '',
  chatEndpoint: 'https://api.openai.com/v1/chat/completions',
  embeddingModel: 'text-embedding-3-small',
  embeddingApiKey: '',
  embeddingEndpoint: 'https://api.openai.com/v1/embeddings',
  embeddingDimensions: 1536
}
const chatForm = reactive({ mode: 'QA', question: '' })
const aiSettings = reactive(loadAiSettings())
const settingsSaved = ref(false)
const maxFolderDepth = 3
const chatHistoryRetentionMs = 24 * 60 * 60 * 1000

const navItems = [
  { key: 'library', label: '我的资料', icon: Library },
  { key: 'chat', label: '知识问答', icon: MessageSquare },
  { key: 'editor', label: '上传编辑', icon: ScanText },
  { key: 'settings', label: 'AI 设置', icon: Settings }
]

const pageMeta = {
  library: {
    title: '我的资料',
    description: '创建资料文件夹，查看当前文件夹中的文件，保持知识库结构清晰。'
  },
  chat: {
    title: '知识问答',
    description: '选择一个文件夹作为知识范围，进行资料溯源答疑或教师式抽问。'
  },
  editor: {
    title: '上传编辑',
    description: '上传文件、校正扫描文本，并保存为后续问答可检索的知识片段。'
  },
  settings: {
    title: 'AI 设置',
    description: '配置答疑模式中的角色定位、提示词、模型服务和 API Key。'
  }
}

const pageTitle = computed(() => pageMeta[activePage.value].title)
const pageDescription = computed(() => pageMeta[activePage.value].description)
const messages = computed(() => chatMessages[chatForm.mode])
const currentChatHasMessages = computed(() => messages.value.length > 0)
const visibleFolders = computed(() => {
  const parentId = activeFolder.value?.id ?? null
  return folders.value.filter((folder) => (folder.parentId ?? null) === parentId)
})
const folderTree = computed(() => {
  const byParent = new Map()
  folders.value.forEach((folder) => {
    const parentId = folder.parentId ?? null
    const siblings = byParent.get(parentId) || []
    siblings.push(folder)
    byParent.set(parentId, siblings)
  })

  const ordered = []
  const appendChildren = (parentId) => {
    ;(byParent.get(parentId) || []).forEach((folder) => {
      ordered.push(folder)
      appendChildren(folder.id)
    })
  }
  appendChildren(null)
  return ordered
})
const folderPath = computed(() => {
  if (!activeFolder.value) return []
  const byId = new Map(folders.value.map((folder) => [folder.id, folder]))
  const path = []
  let current = activeFolder.value
  while (current) {
    path.unshift(current)
    current = current.parentId ? byId.get(current.parentId) : null
  }
  return path
})
const fileMoveTargetOptions = computed(() => folderTree.value)
const canSubmitFileMove = computed(() => {
  if (!movingFile.value || !moveFileTargetId.value) return false
  return movingFile.value.folderId !== Number(moveFileTargetId.value)
})
const currentFolderName = computed(() => folderPath.value.map((folder) => folder.name).join(' / ') || '我的资料')
const canCreateFolder = computed(() => (activeFolder.value?.depth ?? 0) < maxFolderDepth)
const folderNamePlaceholder = computed(() => (activeFolder.value ? '新建子文件夹' : '新建资料文件夹'))
const createFolderHint = computed(() => {
  if (!canCreateFolder.value) return '当前文件夹已达到 3 层上限，不能继续创建子文件夹。'
  return activeFolder.value
    ? `在“${activeFolder.value.name}”中创建子文件夹，最多支持 3 层。`
    : '按科目、章节或资料来源建立分类，再进入文件夹查看已有文件。'
})
const emptyFolderTitle = computed(() => (activeFolder.value ? '当前文件夹没有子文件夹' : '先创建一个资料文件夹'))
const emptyFolderDescription = computed(() =>
  activeFolder.value ? '可以继续上传文件，或在未达到 2 层时创建子文件夹。' : '文件夹会作为上传、编辑和知识问答的工作范围。'
)

function folderChatHistoryKey(folderId) {
  const userKey = session.value?.userId || session.value?.username || 'guest'
  return `smart_exam_chat_histories_v2:${userKey}:${folderId}`
}

function legacyChatHistoryKey(folderId, mode) {
  const userKey = session.value?.userId || session.value?.username || 'guest'
  return `smart_exam_chat_history_v1:${userKey}:${folderId}:${mode}`
}

function loadChatHistory(folderId) {
  clearChatMessages()
  folderChatHistories.value = []
  activeConversationIds.QA = null
  activeConversationIds.TEACHER = null
  historyPanelOpen.value = false
  if (!folderId) return
  folderChatHistories.value = loadFolderHistories(folderId)
  ;['QA', 'TEACHER'].forEach((mode) => {
    const latest = folderChatHistories.value.find((item) => item.mode === mode)
    if (!latest) return
    activeConversationIds[mode] = latest.id
    chatMessages[mode].push(...latest.messages)
  })
}

function loadFolderHistories(folderId) {
  const key = folderChatHistoryKey(folderId)
  const now = Date.now()
  let histories = []
  try {
    const raw = localStorage.getItem(key)
    histories = raw ? JSON.parse(raw) : []
    if (!Array.isArray(histories)) histories = []
  } catch {
    histories = []
  }

  histories.push(...loadLegacyHistories(folderId))
  const valid = dedupeHistories(histories)
    .filter((item) => item.expiresAt && item.expiresAt > now && Array.isArray(item.messages) && item.messages.length > 0)
    .sort((a, b) => b.updatedAt - a.updatedAt)
  localStorage.setItem(key, JSON.stringify(valid))
  return valid
}

function loadLegacyHistories(folderId) {
  const now = Date.now()
  return ['QA', 'TEACHER'].flatMap((mode) => {
    const key = legacyChatHistoryKey(folderId, mode)
    try {
      const raw = localStorage.getItem(key)
      if (!raw) return []
      const saved = JSON.parse(raw)
      localStorage.removeItem(key)
      if (!saved?.expiresAt || saved.expiresAt <= now || !Array.isArray(saved.messages) || saved.messages.length === 0) return []
      return [normalizeHistoryItem({
        id: crypto.randomUUID(),
        mode,
        messages: saved.messages,
        createdAt: now,
        updatedAt: now,
        expiresAt: Math.min(saved.expiresAt, now + chatHistoryRetentionMs)
      })]
    } catch {
      localStorage.removeItem(key)
      return []
    }
  })
}

function saveCurrentChatHistory() {
  if (!activeFolder.value) return
  const savedMessages = messages.value.map(({ role, content, sources }) => ({ role, content, sources }))
  if (savedMessages.length === 0) return
  const now = Date.now()
  const id = activeConversationIds[chatForm.mode] || crypto.randomUUID()
  activeConversationIds[chatForm.mode] = id
  const existing = folderChatHistories.value.find((item) => item.id === id)
  const saved = normalizeHistoryItem({
    ...existing,
    id,
    mode: chatForm.mode,
    messages: savedMessages,
    createdAt: existing?.createdAt || now,
    updatedAt: now,
    expiresAt: now + chatHistoryRetentionMs
  })
  folderChatHistories.value = dedupeHistories([saved, ...folderChatHistories.value])
    .sort((a, b) => b.updatedAt - a.updatedAt)
  localStorage.setItem(folderChatHistoryKey(activeFolder.value.id), JSON.stringify(folderChatHistories.value))
}

function startNewConversation() {
  if (!activeFolder.value) return
  saveCurrentChatHistory()
  messages.value.splice(0)
  activeConversationIds[chatForm.mode] = null
  activeSource.value = null
  historyPanelOpen.value = false
}

function openConversation(item) {
  chatForm.mode = item.mode
  chatMessages[item.mode].splice(0, chatMessages[item.mode].length, ...item.messages)
  activeConversationIds[item.mode] = item.id
  activeSource.value = null
  historyPanelOpen.value = false
}

function normalizeHistoryItem(item) {
  const messages = Array.isArray(item.messages) ? item.messages : []
  return {
    id: item.id || crypto.randomUUID(),
    mode: item.mode === 'TEACHER' ? 'TEACHER' : 'QA',
    title: historyTitle(messages),
    messages,
    createdAt: Number(item.createdAt || Date.now()),
    updatedAt: Number(item.updatedAt || Date.now()),
    expiresAt: Number(item.expiresAt || Date.now() + chatHistoryRetentionMs)
  }
}

function dedupeHistories(histories) {
  const byId = new Map()
  histories.map(normalizeHistoryItem).forEach((item) => {
    const existing = byId.get(item.id)
    if (!existing || item.updatedAt > existing.updatedAt) {
      byId.set(item.id, item)
    }
  })
  return Array.from(byId.values())
}

function historyTitle(messages) {
  const firstQuestion = messages.find((message) => message.role === 'user')?.content || '新的对话'
  const title = firstQuestion.replace(/\s+/g, ' ').trim()
  return title.length > 22 ? `${title.slice(0, 22)}...` : title
}

function chatModeLabel(mode) {
  return mode === 'TEACHER' ? '教师模式' : '答疑模式'
}

function formatHistoryTime(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '刚刚'
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  if (session.value) {
    loadFolders()
    loadRemoteAiSettings()
  }
})

async function submitAuth() {
  await run(async () => {
    const action = authMode.value === 'login' ? authApi.login : authApi.register
    const result = await action(authForm)
    setSession(result)
    session.value = result
    await loadFolders()
    await loadRemoteAiSettings()
  })
}

async function loadFolders() {
  await run(async () => {
    folders.value = await folderApi.list()
    if (activeFolder.value) {
      const refreshedFolder = folders.value.find((folder) => folder.id === activeFolder.value.id)
      if (refreshedFolder) {
        activeFolder.value = refreshedFolder
      } else {
        selectRoot()
      }
    }
  })
}

async function createFolder() {
  if (!canCreateFolder.value) return
  await run(async () => {
    const folder = await folderApi.create({
      ...folderForm,
      parentId: activeFolder.value?.id ?? null
    })
    folderForm.name = ''
    folderForm.description = ''
    folders.value.unshift(folder)
    await selectFolder(folder)
  })
}

function selectRoot() {
  activeFolder.value = null
  activeFile.value = null
  clearEditorContent()
  files.value = []
  clearChatMessages()
  activeConversationIds.QA = null
  activeConversationIds.TEACHER = null
  folderChatHistories.value = []
  historyPanelOpen.value = false
  activeSource.value = null
  cancelMoveFile()
  cancelEditFolder()
}

function openEditFolder(folder) {
  editingFolder.value = folder
  editFolderForm.name = folder.name
  editFolderForm.description = folder.description || ''
}

function cancelEditFolder() {
  editingFolder.value = null
  editFolderForm.name = ''
  editFolderForm.description = ''
}

async function saveFolderEdit() {
  if (!editingFolder.value) return
  const folderId = editingFolder.value.id
  await run(async () => {
    const updated = await folderApi.update(folderId, {
      name: editFolderForm.name.trim(),
      description: editFolderForm.description
    })
    folders.value = folders.value.map((folder) => (folder.id === updated.id ? updated : folder))
    if (activeFolder.value?.id === updated.id) {
      activeFolder.value = updated
    }
    cancelEditFolder()
  })
}

async function deleteFolder(folder) {
  if (!folder || !window.confirm(`确定删除“${folder.name}”及其中的子文件夹、文件和知识库片段吗？`)) return
  const deletedIds = collectFolderIds(folder.id)
  const parentId = folder.parentId ?? null
  await run(async () => {
    await folderApi.delete(folder.id)
    folders.value = folders.value.filter((item) => !deletedIds.has(item.id))
    if (activeFolder.value && deletedIds.has(activeFolder.value.id)) {
      const parent = parentId ? folders.value.find((item) => item.id === parentId) : null
      if (parent) {
        await selectFolder(parent)
      } else {
        selectRoot()
      }
    }
    if (editingFolder.value && deletedIds.has(editingFolder.value.id)) {
      cancelEditFolder()
    }
  })
}

function collectFolderIds(folderId) {
  const ids = new Set([folderId])
  let changed
  do {
    changed = false
    folders.value.forEach((folder) => {
      if (folder.parentId && ids.has(folder.parentId) && !ids.has(folder.id)) {
        ids.add(folder.id)
        changed = true
      }
    })
  } while (changed)
  return ids
}

function folderOptionLabel(folder) {
  return `${'　'.repeat(Math.max(0, folder.depth - 1))}${folder.name}`
}

function openMoveFile(file) {
  movingFile.value = file
  moveFileTargetId.value = file.folderId ? String(file.folderId) : ''
}

function cancelMoveFile() {
  movingFile.value = null
  moveFileTargetId.value = ''
}

async function moveFile() {
  if (!movingFile.value || !canSubmitFileMove.value) return
  const fileId = movingFile.value.id
  const targetFolderId = Number(moveFileTargetId.value)
  await run(async () => {
    const moved = await fileApi.move(fileId, { folderId: targetFolderId })
    files.value = files.value.filter((file) => file.id !== moved.id)
    if (activeFile.value?.id === moved.id) {
      activeFile.value = null
      clearEditorContent()
    }
    cancelMoveFile()
  })
}

function useCurrentFolderAsKnowledgeBase() {
  if (!activeFolder.value) return
  activePage.value = 'chat'
  activeSource.value = null
}

async function selectFolder(folder) {
  activeFolder.value = folder
  activeFile.value = null
  clearEditorContent()
  files.value = await fileApi.list(folder.id)
  loadChatHistory(folder.id)
  activeSource.value = null
  cancelMoveFile()
  cancelEditFolder()
}

function selectFile(file) {
  activeFile.value = { ...file }
  nextTick(() => setEditorContent(activeFile.value.extractedText, 0))
}

function setChatMode(mode) {
  chatForm.mode = mode
  activeSource.value = null
}

function setEditorContent(content = '', pageIndex = activeFilePageIndex.value) {
  if (!editorElement.value) return
  activeFilePages.value = paginateEditorContent(content)
  activeFilePageIndex.value = clampPageIndex(pageIndex)
  setEditorPage(activeFilePageIndex.value)
}

function clearEditorContent() {
  activeFilePages.value = []
  activeFilePageIndex.value = 0
  if (editorElement.value) {
    editorElement.value.innerHTML = ''
  }
}

function syncEditorContent() {
  if (activeFile.value && editorElement.value) {
    activeFilePages.value[activeFilePageIndex.value] = editorElement.value.innerHTML
    activeFile.value.extractedText = activeFilePages.value.join('')
  }
}

function setEditorPage(pageIndex) {
  if (!editorElement.value) return
  editorElement.value.innerHTML = renderEditorHtml(activeFilePages.value[pageIndex] || '')
}

function renderCurrentEditorPage() {
  syncEditorContent()
  setEditorPage(activeFilePageIndex.value)
  syncEditorContent()
}

function goFilePage(pageIndex) {
  syncEditorContent()
  activeFilePageIndex.value = clampPageIndex(pageIndex)
  setEditorPage(activeFilePageIndex.value)
}

function clampPageIndex(pageIndex) {
  const maxIndex = Math.max(0, activeFilePages.value.length - 1)
  return Math.min(Math.max(Number.isFinite(pageIndex) ? pageIndex : 0, 0), maxIndex)
}

function paginateEditorContent(content = '') {
  const html = isHtmlContent(content) ? content : plainTextToEditorHtml(content)
  const template = document.createElement('template')
  template.innerHTML = html || '<p><br></p>'
  const blocks = Array.from(template.content.childNodes)
  const pages = []
  let current = ''
  let currentLength = 0
  const maxPageChars = 3500

  blocks.forEach((node) => {
    const wrapper = document.createElement('div')
    wrapper.appendChild(node.cloneNode(true))
    const blockHtml = wrapper.innerHTML
    const blockLength = (node.textContent || '').trim().length
    if (current && currentLength + blockLength > maxPageChars) {
      pages.push(current)
      current = ''
      currentLength = 0
    }
    current += blockHtml
    currentLength += blockLength
  })
  if (current || pages.length === 0) {
    pages.push(current || '<p><br></p>')
  }
  return pages
}

function formatEditor(command) {
  editorElement.value?.focus()
  document.execCommand(command, false, null)
  syncEditorContent()
}

function setEditorTextColor(color) {
  editorElement.value?.focus()
  document.execCommand('foreColor', false, color)
  syncEditorContent()
}

function insertTable() {
  editorElement.value?.focus()
  document.execCommand('insertHTML', false, '<table><tbody><tr><td>单元格</td><td>单元格</td></tr><tr><td>单元格</td><td>单元格</td></tr></tbody></table><p><br></p>')
  syncEditorContent()
}

function isHtmlContent(content = '') {
  return /<\/?[a-z][\s\S]*>/i.test(content)
}

function plainTextToEditorHtml(text = '') {
  return text
    .split(/\n{2,}/)
    .map((block) => block.trim())
    .filter(Boolean)
    .map((block) => `<p>${renderRichText(block).replace(/\n/g, '<br>')}</p>`)
    .join('')
}

function renderEditorHtml(content = '') {
  if (!content) return '<p><br></p>'
  return isHtmlContent(content) ? renderMathInHtml(content) : plainTextToEditorHtml(content)
}

function renderMathInHtml(html = '') {
  const template = document.createElement('template')
  template.innerHTML = html
  const walker = document.createTreeWalker(template.content, NodeFilter.SHOW_TEXT)
  const textNodes = []
  let node = walker.nextNode()
  while (node) {
    if (node.textContent && /(?:\$|\\\(|\\\[)/.test(node.textContent)) {
      textNodes.push(node)
    }
    node = walker.nextNode()
  }
  textNodes.forEach((textNode) => {
    const wrapper = document.createElement('span')
    wrapper.innerHTML = renderRichText(textNode.textContent)
    textNode.replaceWith(...Array.from(wrapper.childNodes))
  })
  return template.innerHTML || '<p><br></p>'
}

function renderRichText(text = '') {
  const parts = splitMathSegments(text)
  return parts.map((part) => {
    if (part.type === 'math') {
      return `<span class="${part.display ? 'math-block' : 'math-inline'}">${renderMathExpression(part.text)}</span>`
    }
    return renderMarkdownInline(part.text)
  }).join('')
}

function splitMathSegments(text = '') {
  const segments = []
  let cursor = 0
  while (cursor < text.length) {
    const next = findNextMathStart(text, cursor)
    if (!next) {
      segments.push({ type: 'text', text: text.slice(cursor) })
      break
    }
    if (next.index > cursor) {
      segments.push({ type: 'text', text: text.slice(cursor, next.index) })
    }
    const end = text.indexOf(next.close, next.index + next.open.length)
    if (end === -1) {
      segments.push({ type: 'text', text: text.slice(next.index) })
      break
    }
    segments.push({
      type: 'math',
      text: text.slice(next.index + next.open.length, end),
      display: next.display
    })
    cursor = end + next.close.length
  }
  return segments
}

function findNextMathStart(text, startIndex) {
  const candidates = [
    { open: '$$', close: '$$', display: true },
    { open: '\\[', close: '\\]', display: true },
    { open: '\\(', close: '\\)', display: false },
    { open: '$', close: '$', display: false }
  ]
    .map((candidate) => ({ ...candidate, index: text.indexOf(candidate.open, startIndex) }))
    .filter((candidate) => candidate.index !== -1)
    .sort((a, b) => a.index - b.index || b.open.length - a.open.length)
  return candidates[0] || null
}

function renderMarkdownInline(text = '') {
  return escapeHtml(text).replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
}

function renderMathExpression(expression = '') {
  let html = escapeHtml(expression.trim())
    .replace(/\\text\{([^{}]+)\}/g, '$1')
    .replace(/\\mathrm\{([^{}]+)\}/g, '$1')
    .replace(/\\left|\\right/g, '')
    .replace(/\\,/g, ' ')
    .replace(/\\;/g, ' ')
    .replace(/\\cdot/g, '·')
    .replace(/\\times/g, '×')
    .replace(/\\leq?/g, '≤')
    .replace(/\\geq?/g, '≥')
    .replace(/\\neq/g, '≠')
    .replace(/\\approx/g, '≈')
    .replace(/\\Omega/g, 'Ω')
    .replace(/\\omega/g, 'ω')
    .replace(/\\mu/g, 'μ')
    .replace(/\\alpha/g, 'α')
    .replace(/\\beta/g, 'β')
    .replace(/\\gamma/g, 'γ')
    .replace(/\\Delta/g, 'Δ')
    .replace(/\\theta/g, 'θ')
    .replace(/\\phi/g, 'φ')

  html = html.replace(/\\frac\{([^{}]+)\}\{([^{}]+)\}/g, (_, numerator, denominator) => (
    `<span class="math-frac"><span>${renderMathExpression(numerator)}</span><span>${renderMathExpression(denominator)}</span></span>`
  ))
  html = html.replace(/_\{([^{}]+)\}/g, (_, value) => `<sub>${renderMathExpression(value)}</sub>`)
  html = html.replace(/\^\{([^{}]+)\}/g, (_, value) => `<sup>${renderMathExpression(value)}</sup>`)
  html = html.replace(/_([A-Za-z0-9()+-])/g, '<sub>$1</sub>')
  html = html.replace(/\^([A-Za-z0-9()+-])/g, '<sup>$1</sup>')
  return html.replace(/\\/g, '')
}

function escapeHtml(text = '') {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

async function uploadFile() {
  const selected = fileInput.value?.files?.[0]
  if (!selected || !activeFolder.value) return
  await run(async () => {
    const uploaded = await fileApi.upload(activeFolder.value.id, uploadTag.value, selected)
    files.value.unshift(uploaded)
    activeFile.value = { ...uploaded }
    fileInput.value.value = ''
    await nextTick()
    setEditorContent(activeFile.value.extractedText, 0)
  })
}

async function saveFileText() {
  if (!activeFile.value) return
  syncEditorContent()
  await run(async () => {
    const saved = await fileApi.update(activeFile.value.id, {
      extractedText: activeFile.value.extractedText,
      tag: activeFile.value.tag
    })
    files.value = files.value.map((file) => (file.id === saved.id ? saved : file))
    activeFile.value = { ...saved }
    await nextTick()
    setEditorContent(activeFile.value.extractedText, activeFilePageIndex.value)
  })
}

async function toggleKnowledge(file) {
  if (!file) return
  if (activeFile.value?.id === file.id) {
    syncEditorContent()
  }
  await run(async () => {
    const saved = !file.knowledgeEnabled && activeFile.value?.id === file.id
      ? await fileApi.update(activeFile.value.id, {
          extractedText: activeFile.value.extractedText,
          tag: activeFile.value.tag
        })
      : await fileApi.updateKnowledgeStatus(file.id, {
          knowledgeEnabled: !file.knowledgeEnabled
        })
    files.value = files.value.map((item) => (item.id === saved.id ? saved : item))
    if (activeFile.value?.id === saved.id) {
      activeFile.value = { ...saved }
      await nextTick()
      setEditorContent(activeFile.value.extractedText, activeFilePageIndex.value)
    }
    if (!saved.knowledgeEnabled && activeSource.value?.fileId === saved.id) {
      activeSource.value = null
    }
  })
}

async function deleteFile(file) {
  if (!file || !window.confirm(`确定删除“${displayFileName(file)}”吗？`)) return
  await run(async () => {
    await fileApi.delete(file.id)
    files.value = files.value.filter((item) => item.id !== file.id)
    if (activeFile.value?.id === file.id) {
      activeFile.value = null
      clearEditorContent()
    }
    if (activeSource.value?.fileId === file.id) {
      activeSource.value = null
    }
  })
}

async function ask() {
  const question = chatForm.question.trim()
  if (!question || !activeFolder.value || chatLoading.value) return
  messages.value.push({ role: 'user', content: question })
  saveCurrentChatHistory()
  chatForm.question = ''
  activeSource.value = null
  chatLoading.value = true
  await run(async () => {
    const response = await chatApi.ask({ ...aiSettings, ...chatForm, question, folderId: activeFolder.value.id })
    messages.value.push({ role: 'assistant', content: response.answer, sources: response.sources })
    saveCurrentChatHistory()
  })
  chatLoading.value = false
}

async function createNoteFromConversation() {
  if (!activeFolder.value || !currentChatHasMessages.value || noteLoading.value) return
  noteLoading.value = true
  await run(async () => {
    const saved = await chatApi.createNote({
      ...aiSettings,
      folderId: activeFolder.value.id,
      mode: chatForm.mode,
      messages: messages.value.map(({ role, content }) => ({ role, content }))
    })
    files.value = [saved, ...files.value.filter((file) => file.id !== saved.id)]
  })
  noteLoading.value = false
}

function showSource(source) {
  activeSource.value = activeSource.value === source ? null : source
}

function messageParts(message) {
  const content = message?.content || ''
  const sourcesByIndex = new Map((message?.sources || []).map((source, index) => [
    Number(source.citationIndex || index + 1),
    source
  ]))
  const parts = []
  const citationPattern = /\[(?:来源|片段)?(\d+)\]/g
  let cursor = 0
  let match = citationPattern.exec(content)
  while (match) {
    if (match.index > cursor) {
      parts.push({ type: 'text', text: content.slice(cursor, match.index) })
    }
    const citationIndex = Number(match[1])
    const source = sourcesByIndex.get(citationIndex)
    parts.push(source
      ? { type: 'citation', text: `[${citationIndex}]`, source }
      : { type: 'text', text: match[0] })
    cursor = match.index + match[0].length
    match = citationPattern.exec(content)
  }
  if (cursor < content.length) {
    parts.push({ type: 'text', text: content.slice(cursor) })
  }
  return parts.length ? parts : [{ type: 'text', text: content }]
}

function sourceLabel(source) {
  return `${displayFileName({ originalName: source.fileName })} · 第 ${source.pageNumber || 1} 页 · 片段 ${source.citationIndex || 1}`
}

function displayFileName(file) {
  const name = file?.originalName || file?.fileName || ''
  return name.replace(/\.[^.\\/\s]+$/u, '')
}

async function openSourceFile(source) {
  if (!source) return
  await run(async () => {
    const file = await fileApi.get(source.fileId)
    const folder = folders.value.find((item) => item.id === file.folderId)
    if (folder) {
      activeFolder.value = folder
      files.value = await fileApi.list(folder.id)
    }
    activeFile.value = { ...file }
    activePage.value = 'editor'
    activeSource.value = null
    await nextTick()
    setEditorContent(activeFile.value.extractedText, Math.max(0, (source.pageNumber || 1) - 1))
    focusExcerpt(source.excerpt)
  })
}

function focusExcerpt(excerpt) {
  const editor = editorElement.value
  if (!editor || !activeFile.value?.extractedText || !excerpt) return
  const text = editor.textContent || ''
  const match = findExcerptRange(text, excerpt)
  if (!match) {
    editor.focus()
    return
  }
  editor.focus()
  const range = document.createRange()
  const selection = window.getSelection()
  const textNode = firstTextNodeContaining(editor, text.slice(match.start, match.end))
  if (textNode && selection) {
    const nodeIndex = textNode.textContent.indexOf(text.slice(match.start, match.end))
    range.setStart(textNode, Math.max(0, nodeIndex))
    range.setEnd(textNode, Math.max(0, nodeIndex) + text.slice(match.start, match.end).length)
    selection.removeAllRanges()
    selection.addRange(range)
  }
}

function firstTextNodeContaining(root, needle) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  let node = walker.nextNode()
  while (node) {
    if (node.textContent.includes(needle)) {
      return node
    }
    node = walker.nextNode()
  }
  return null
}

function findExcerptRange(text, excerpt) {
  const cleanedExcerpt = excerpt.replace(/^\.\.\./, '').replace(/\.\.\.$/, '').trim()
  const candidates = [
    cleanedExcerpt,
    ...cleanedExcerpt.split(/[。！？；;.!?]/).map((part) => part.trim()).filter((part) => part.length >= 12)
  ]

  for (const candidate of candidates) {
    const match = findNormalizedRange(text, candidate)
    if (match) return match
  }

  return null
}

function findNormalizedRange(text, needleText) {
  const needle = needleText.replace(/\s+/g, ' ').trim()
  if (!needle) return null
  const exactIndex = text.indexOf(needle)
  if (exactIndex >= 0) {
    return { start: exactIndex, end: exactIndex + needle.length }
  }

  let normalized = ''
  const indexMap = []
  let lastWasSpace = false
  for (let index = 0; index < text.length; index += 1) {
    const character = text[index]
    if (/\s/.test(character)) {
      if (!lastWasSpace && normalized.length > 0) {
        normalized += ' '
        indexMap.push(index)
        lastWasSpace = true
      }
    } else {
      normalized += character
      indexMap.push(index)
      lastWasSpace = false
    }
  }

  const normalizedIndex = normalized.indexOf(needle)
  if (normalizedIndex < 0) return null
  const start = indexMap[normalizedIndex]
  const end = indexMap[Math.min(normalizedIndex + needle.length - 1, indexMap.length - 1)] + 1
  return { start, end }
}

function clearChatMessages() {
  chatMessages.QA.splice(0)
  chatMessages.TEACHER.splice(0)
}

function loadAiSettings() {
  try {
    const raw = localStorage.getItem('smart_exam_ai_settings')
    return normalizeAiSettings(raw ? { ...defaultAiSettings, ...JSON.parse(raw) } : { ...defaultAiSettings })
  } catch {
    return { ...defaultAiSettings }
  }
}

async function loadRemoteAiSettings() {
  await run(async () => {
    const remote = await aiSettingsApi.get()
    Object.assign(aiSettings, normalizeAiSettings({ ...aiSettings, ...remote }))
    localStorage.setItem('smart_exam_ai_settings', JSON.stringify({ ...aiSettings }))
  })
}

async function saveAiSettings() {
  await run(async () => {
    const saved = await aiSettingsApi.save(normalizeAiSettings(aiSettings))
    Object.assign(aiSettings, normalizeAiSettings(saved))
    localStorage.setItem('smart_exam_ai_settings', JSON.stringify({ ...aiSettings }))
    settingsSaved.value = true
    window.setTimeout(() => {
      settingsSaved.value = false
    }, 1800)
  })
}

function resetAiSettings() {
  Object.assign(aiSettings, defaultAiSettings)
  saveAiSettings()
}

function normalizeAiSettings(settings) {
  return {
    ...defaultAiSettings,
    ...settings,
    chatModel: settings.chatModel || settings.model || defaultAiSettings.chatModel,
    chatEndpoint: settings.chatEndpoint || settings.endpoint || defaultAiSettings.chatEndpoint,
    chatApiKey: settings.chatApiKey || settings.apiKey || defaultAiSettings.chatApiKey,
    embeddingDimensions: Number(settings.embeddingDimensions || defaultAiSettings.embeddingDimensions)
  }
}

function logout() {
  clearSession()
  session.value = null
  folders.value = []
  files.value = []
  activeFolder.value = null
  activeFile.value = null
  activeSource.value = null
  folderChatHistories.value = []
  activeConversationIds.QA = null
  activeConversationIds.TEACHER = null
  historyPanelOpen.value = false
  clearChatMessages()
}

async function run(task) {
  loading.value = true
  error.value = ''
  try {
    await task()
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

function tagLabel(tag) {
  return { TEXTBOOK: '教材', MATERIAL: '资料', NOTE: '笔记', EXERCISE: '习题', OTHER: '其他' }[tag] || '其他'
}
</script>
