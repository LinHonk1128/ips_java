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
          <button
            v-for="folder in folders"
            :key="folder.id"
            class="folder-item"
            :class="{ selected: activeFolder?.id === folder.id }"
            @click="selectFolder(folder)"
          >
            <Folder :size="18" />
            <span>{{ folder.name }}</span>
          </button>
          <div v-if="folders.length === 0" class="empty-note">还没有文件夹，请先到“文件夹与文件”页面创建。</div>
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
            <span>{{ activeFolder?.name || '未选择文件夹' }}</span>
          </div>
        </header>

        <p v-if="error" class="error-banner">{{ error }}</p>

        <section v-if="activePage === 'library'" class="page-panel library-page">
          <div class="section-head">
            <div>
              <h3>创建文件夹</h3>
              <p>按科目、章节或资料来源建立分类，再进入文件夹查看已有文件。</p>
            </div>
          </div>
          <form class="folder-form wide" @submit.prevent="createFolder">
            <input v-model="folderForm.name" placeholder="新建科目文件夹" required />
            <input v-model="folderForm.description" placeholder="描述，可选" />
            <button class="primary-btn compact" type="submit" :disabled="loading">
              <FolderPlus :size="17" />
              创建
            </button>
          </form>

          <div class="folder-board">
            <button
              v-for="folder in folders"
              :key="folder.id"
              class="folder-card"
              :class="{ selected: activeFolder?.id === folder.id }"
              @click="selectFolder(folder)"
            >
              <Folder :size="22" />
              <strong>{{ folder.name }}</strong>
              <span>{{ folder.description || '暂无描述' }}</span>
            </button>
            <div v-if="folders.length === 0" class="empty-state">
              <FolderPlus :size="30" />
              <strong>先创建一个资料文件夹</strong>
              <span>文件夹会作为上传、编辑和知识问答的工作范围。</span>
            </div>
          </div>

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
            <button
              v-for="file in files"
              :key="file.id"
              class="file-row"
              :class="{ selected: activeFile?.id === file.id }"
              @click="selectFile(file)"
            >
              <FileText :size="18" />
              <span>{{ file.originalName }}</span>
              <b>{{ tagLabel(file.tag) }}</b>
            </button>
            <div v-if="activeFolder && files.length === 0" class="empty-note">当前文件夹还没有文件。</div>
          </div>
        </section>

        <section v-else-if="activePage === 'chat'" class="page-panel chat-page">
          <div class="qa-toolbar">
            <div class="mode-tabs">
              <button :class="{ active: chatForm.mode === 'QA' }" @click="chatForm.mode = 'QA'">答疑模式</button>
              <button :class="{ active: chatForm.mode === 'TEACHER' }" @click="chatForm.mode = 'TEACHER'">教师模式</button>
            </div>
            <div class="ai-summary">
              <Bot :size="18" />
              <span>{{ aiSettings.aiRole }}</span>
              <b>{{ aiSettings.model }}</b>
              <button class="secondary-btn slim" type="button" @click="activePage = 'settings'">
                <Settings :size="16" />
                设置
              </button>
            </div>
          </div>

          <div class="chat-log">
            <article v-for="(message, index) in messages" :key="index" :class="['message', message.role]">
              <p>{{ message.content }}</p>
              <div v-if="message.sources?.length" class="sources">
                <span v-for="source in message.sources" :key="source.fileId + source.excerpt">
                  {{ source.fileName }}：{{ source.excerpt }}
                </span>
              </div>
            </article>
            <div v-if="messages.length === 0" class="empty-chat">
              <MessageSquare :size="30" />
              <strong>{{ activeFolder ? '开始围绕当前知识库提问' : '先选择一个文件夹' }}</strong>
              <span>答疑模式会追溯资料来源，教师模式会根据知识点向你提问。</span>
            </div>
          </div>

          <form class="question-box" @submit.prevent="ask">
            <textarea v-model="chatForm.question" :disabled="!activeFolder" placeholder="输入问题，或在教师模式下输入：开始抽问本章重点" />
            <button class="primary-btn" type="submit" :disabled="!activeFolder || !chatForm.question || loading">
              <Send :size="18" />
              发送
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
              <button
                v-for="file in files"
                :key="file.id"
                class="file-row"
                :class="{ selected: activeFile?.id === file.id }"
                @click="selectFile(file)"
              >
                <FileText :size="18" />
                <span>{{ file.originalName }}</span>
                <b>{{ tagLabel(file.tag) }}</b>
              </button>
              <div v-if="activeFolder && files.length === 0" class="empty-note">上传 PDF、图片、Word 或文本文件后，可在右侧校正文档文本。</div>
              <div v-if="!activeFolder" class="empty-note">请先选择一个文件夹。</div>
            </div>

            <div v-if="activeFile" class="editor-panel">
              <div class="editor-head">
                <strong>{{ activeFile.originalName }}</strong>
                <select v-model="activeFile.tag">
                  <option value="TEXTBOOK">教材</option>
                  <option value="MATERIAL">资料</option>
                  <option value="NOTE">笔记</option>
                  <option value="EXERCISE">习题</option>
                  <option value="OTHER">其他</option>
                </select>
              </div>
              <textarea v-model="activeFile.extractedText" spellcheck="false" />
              <button class="primary-btn compact" @click="saveFileText" :disabled="loading">
                <Save :size="17" />
                保存为知识库
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
                <h3>OpenAI 兼容接口</h3>
                <p>支持 OpenAI 官方接口，也支持兼容 Chat Completions 的模型服务。</p>
              </div>

              <div class="settings-grid">
                <label>
                  模型
                  <select v-model="aiSettings.model">
                    <option value="gpt-4o-mini">gpt-4o-mini</option>
                    <option value="gpt-4o">gpt-4o</option>
                    <option value="gpt-4.1-mini">gpt-4.1-mini</option>
                    <option value="qwen-plus">qwen-plus</option>
                    <option value="deepseek-chat">deepseek-chat</option>
                  </select>
                </label>
                <label>
                  Endpoint
                  <input v-model="aiSettings.endpoint" placeholder="https://api.openai.com/v1/chat/completions" />
                </label>
              </div>

              <label>
                API Key
                <input v-model="aiSettings.apiKey" type="password" autocomplete="off" placeholder="sk-..." />
              </label>

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
              <span>{{ aiSettings.model || defaultAiSettings.model }}</span>
              <p>{{ aiSettings.systemPrompt || defaultAiSettings.systemPrompt }}</p>
              <div class="key-state" :class="{ ready: aiSettings.apiKey }">
                <KeyRound :size="17" />
                <span>{{ aiSettings.apiKey ? 'API Key 已配置' : '未配置 API Key，将使用本地检索兜底回答' }}</span>
              </div>
            </aside>
          </div>
        </section>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Bot,
  FileText,
  Folder,
  FolderOpen,
  FolderPlus,
  KeyRound,
  Library,
  LogIn,
  LogOut,
  MessageSquare,
  RefreshCw,
  RotateCcw,
  Save,
  ScanText,
  Send,
  Settings,
  Upload
} from 'lucide-vue-next'
import { authApi, chatApi, clearSession, fileApi, folderApi, getSession, setSession } from './api/client'

const session = ref(getSession())
const authMode = ref('login')
const authForm = reactive({ username: '', password: '', displayName: '' })
const folderForm = reactive({ name: '', description: '' })
const folders = ref([])
const files = ref([])
const activeFolder = ref(null)
const activeFile = ref(null)
const activePage = ref('library')
const uploadTag = ref('NOTE')
const fileInput = ref(null)
const loading = ref(false)
const error = ref('')
const messages = ref([])
const defaultAiSettings = {
  aiRole: '严谨的考研答疑老师',
  systemPrompt: '优先依据当前知识库回答；给出可追溯依据；如果资料不足，明确说明无法从知识库确认。',
  model: 'gpt-4o-mini',
  apiKey: '',
  endpoint: 'https://api.openai.com/v1/chat/completions'
}
const chatForm = reactive({ mode: 'QA', question: '' })
const aiSettings = reactive(loadAiSettings())
const settingsSaved = ref(false)

const navItems = [
  { key: 'library', label: '文件夹与文件', icon: Library },
  { key: 'chat', label: '知识问答', icon: MessageSquare },
  { key: 'editor', label: '上传编辑', icon: ScanText },
  { key: 'settings', label: 'AI 设置', icon: Settings }
]

const pageMeta = {
  library: {
    title: '文件夹与文件',
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

onMounted(() => {
  if (session.value) loadFolders()
})

async function submitAuth() {
  await run(async () => {
    const action = authMode.value === 'login' ? authApi.login : authApi.register
    const result = await action(authForm)
    setSession(result)
    session.value = result
    await loadFolders()
  })
}

async function loadFolders() {
  await run(async () => {
    folders.value = await folderApi.list()
    if (!activeFolder.value && folders.value.length) {
      await selectFolder(folders.value[0])
    }
  })
}

async function createFolder() {
  await run(async () => {
    const folder = await folderApi.create({ ...folderForm })
    folderForm.name = ''
    folderForm.description = ''
    folders.value.unshift(folder)
    await selectFolder(folder)
  })
}

async function selectFolder(folder) {
  activeFolder.value = folder
  activeFile.value = null
  files.value = await fileApi.list(folder.id)
  messages.value = []
}

function selectFile(file) {
  activeFile.value = { ...file }
}

async function uploadFile() {
  const selected = fileInput.value?.files?.[0]
  if (!selected || !activeFolder.value) return
  await run(async () => {
    const uploaded = await fileApi.upload(activeFolder.value.id, uploadTag.value, selected)
    files.value.unshift(uploaded)
    activeFile.value = { ...uploaded }
    fileInput.value.value = ''
  })
}

async function saveFileText() {
  if (!activeFile.value) return
  await run(async () => {
    const saved = await fileApi.update(activeFile.value.id, {
      extractedText: activeFile.value.extractedText,
      tag: activeFile.value.tag
    })
    files.value = files.value.map((file) => (file.id === saved.id ? saved : file))
    activeFile.value = { ...saved }
  })
}

async function ask() {
  const question = chatForm.question.trim()
  if (!question || !activeFolder.value) return
  messages.value.push({ role: 'user', content: question })
  chatForm.question = ''
  await run(async () => {
    const response = await chatApi.ask({ ...aiSettings, ...chatForm, question, folderId: activeFolder.value.id })
    messages.value.push({ role: 'assistant', content: response.answer, sources: response.sources })
  })
}

function loadAiSettings() {
  try {
    const raw = localStorage.getItem('smart_exam_ai_settings')
    return raw ? { ...defaultAiSettings, ...JSON.parse(raw) } : { ...defaultAiSettings }
  } catch {
    return { ...defaultAiSettings }
  }
}

function saveAiSettings() {
  localStorage.setItem('smart_exam_ai_settings', JSON.stringify({ ...aiSettings }))
  settingsSaved.value = true
  window.setTimeout(() => {
    settingsSaved.value = false
  }, 1800)
}

function resetAiSettings() {
  Object.assign(aiSettings, defaultAiSettings)
  saveAiSettings()
}

function logout() {
  clearSession()
  session.value = null
  folders.value = []
  files.value = []
  activeFolder.value = null
  activeFile.value = null
  messages.value = []
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
