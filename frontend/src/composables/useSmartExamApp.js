import * as echarts from 'echarts'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import {
  Bold,
  BookOpenCheck,
  Bot,
  CalendarDays,
  CalendarPlus,
  ChevronRight,
  ClipboardCopy,
  Clock,
  Eye,
  EyeOff,
  FileText,
  Folder,
  FolderOpen,
  FolderPlus,
  Image,
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
  Search,
  Send,
  Settings,
  Sparkles,
  Table2,
  Tag,
  Timer,
  Trash2,
  Underline,
  Undo2,
  Upload,
  UserCog
} from 'lucide-vue-next'
import { aiSettingsApi, authApi, chatApi, clearSession, fileApi, folderApi, getSession, getToken, knowledgeProfileApi, mistakeApi, setSession, studyPlanApi, studyProfileApi } from '../api/client'

export function useSmartExamApp() {

const session = ref(getSession())
const authMode = ref('login')
const authForm = reactive({ username: '', password: '', displayName: '' })
const folderForm = reactive({ name: '', description: '' })
const editFolderForm = reactive({ name: '', description: '' })
const folders = ref([])
const files = ref([])
const activeFolder = ref(null)
const activeFile = ref(null)
const activePage = ref('home')
const studyProfile = ref(null)
const onboardingForm = reactive({
  examDate: '',
  subjectCount: 4,
  subjects: ['政治', '英语', '数学', '专业课']
})
const personalSettingsForm = reactive({
  examDate: '',
  subjects: ['']
})
const uploadTag = ref('NOTE')
const fileInput = ref(null)
const editorElement = ref(null)
const activeFilePages = ref([])
const activeFilePageIndex = ref(0)
const editorTextColor = ref('#24231f')
const loading = ref(false)
const homeClockNow = ref(Date.now())
const homeClockTimerId = ref(null)
const chatLoading = ref(false)
const noteLoading = ref(false)
const error = ref('')
const chatMessages = reactive({ QA: [], TEACHER: [] })
const activeConversationIds = reactive({ QA: null, TEACHER: null })
const folderChatHistories = ref([])
const historyPanelOpen = ref(false)
const activeSource = ref(null)
const activeChunkDetail = ref(null)
const movingFile = ref(null)
const editingFolder = ref(null)
const editingFileName = ref(false)
const editingFileNameValue = ref('')
const savingFileName = ref(false)
const fileNameInput = ref(null)
const moveFileTargetId = ref('')
const rootFolderCollapsed = ref(false)
const collapsedFolderIds = ref(new Set())
const studyPlanItems = ref([])
const homePlanItems = ref([])
const planDraftItems = ref([])
const planSessionByWeek = ref({})
const planModule = ref('')
const knowledgeModule = ref('')
const planWeekStart = ref(startOfWeekIso(new Date()))
const savedExamDate = ref(loadExamDate())
const examDate = ref(savedExamDate.value)
const examDateInput = ref(null)
const profileOverview = ref(null)
const profileSubjects = ref([])
const profileWeakChunks = ref([])
const profileTrends = ref([])
const profileDistribution = ref(null)
const profileActivity = ref(null)
const profileFourteenDayActivity = ref(null)
const profileRisk = ref(null)
const profilePressureRisk = ref(null)
const profileDiagnosis = ref(null)
const profileTrendDays = ref(30)
const profilePressureSubjectId = ref('all')
const profileLoading = ref(false)
const profileDiagnosisLoading = ref(false)
let profileDiagnosisRequestId = 0
const profileSubjectChartRef = ref(null)
const profileDistributionChartRef = ref(null)
const profileTrendChartRef = ref(null)
const profileHeatmapChartRef = ref(null)
const profilePressureChartRef = ref(null)
const profileCharts = {
  subjects: null,
  distribution: null,
  trend: null,
  heatmap: null,
  pressure: null
}
const editingPlanItem = ref(null)
const planAiMessages = ref(initialPlanAiMessages())
const planAiInput = ref('')
const planAiLoading = ref(false)
const planGenerateLoading = ref(false)
const planSaveLoading = ref(false)
const planUndoLoading = ref(false)
const planLastOperations = ref([])
const planPendingOperations = ref([])
const planUndoStack = ref([])
const planForm = reactive({
  title: '',
  subject: '',
  description: '',
  itemType: 'SELF_STUDY',
  startDate: toDateInputValue(new Date()),
  startTime: '19:00',
  endTime: '21:00',
  location: '',
  priority: 'MEDIUM',
  status: 'TODO'
})
const mistakes = ref([])
const mistakeStatuses = ref([])
const mistakeSubjectTags = ref([])
const activeMistake = ref(null)
const editingMistake = ref(null)
const mistakeQuestionAttachmentFile = ref(null)
const mistakeSolutionFile = ref(null)
const questionImageItems = ref([])
const solutionImageItems = ref([])
const enlargedAttachment = ref(null)
const recognitionFile = ref(null)
const recognitionText = ref('')
const recognitionLoading = ref(false)
const newMistakeStatusName = ref('')
const newMistakeSubjectTagName = ref('')
const subjectTagCreatorOpen = ref(false)
const mistakeForm = reactive({
  questionText: '',
  solutionText: '',
  statusKey: 'mastered',
  subjectTagIds: [],
  linkedChunks: []
})
const mistakeChunkQuery = ref('')
const mistakeChunkCandidates = ref([])
const mistakeChunkSearchLoading = ref(false)
const mistakeChunkSubjectFolderId = ref('')
const mistakeChunkFileId = ref('')
const mistakeChunkFiles = ref([])
const practiceForm = reactive({ count: 5, timed: false, minutes: 20, subjectTagIds: [] })
const practiceQuestions = ref([])
const practiceIndex = ref(0)
const practiceStarted = ref(false)
const practiceFinished = ref(false)
const practiceResults = ref({})
const practiceRemainingSeconds = ref(0)
const practiceTimerId = ref(null)
const showBrowseSolution = ref(true)
const browseSolutionVisibility = ref({})
const editingStatusMistakeId = ref(null)
const browseSubjectFilterIds = ref([])
const browseStatusFilterKeys = ref([])
const solutionPreviewUrls = ref({})
const questionPreviewUrls = ref({})
const attachmentPreviewUrls = ref({})
const mistakeModule = ref('')
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
const aiPresetStorageKey = 'smart_exam_ai_setting_presets'
const chatForm = reactive({ mode: 'QA', question: '', useKnowledgeBase: true, withCitations: true, deepAnswer: false })
const teacherState = reactive({
  requirement: '',
  subjectFolderId: '',
  currentQuestion: '',
  referenceAnswer: '',
  currentChunkId: null,
  currentSource: null,
  feedbackType: null,
  addedToMistake: false,
  askedChunkIds: []
})
const aiSettings = reactive(loadAiSettings())
const aiSettingPresets = ref(loadAiSettingPresets())
const selectedAiPresetId = ref('')
const aiPresetName = ref('')
const settingsSaved = ref(false)
const maxFolderDepth = 3
const chatHistoryRetentionMs = 24 * 60 * 60 * 1000

const navItems = [
  { key: 'home', label: '首页', icon: Timer },
  { key: 'knowledge', label: '我的知识库', icon: Library },
  { key: 'profile', label: '知识画像', icon: Sparkles },
  { key: 'planner', label: '学习规划', icon: CalendarDays },
  { key: 'mistakes', label: '错题集', icon: BookOpenCheck },
  { key: 'personal', label: '个人设置', icon: UserCog },
  { key: 'settings', label: 'AI 设置', icon: Settings }
]

const pageMeta = {
  home: {
    title: '首页',
    description: '查看考研倒计时和今天的当前任务。'
  },
  knowledge: {
    title: '我的知识库',
    description: '集中管理资料、上传编辑和知识问答。'
  },
  profile: {
    title: '知识画像',
    description: '根据引用、反馈、错题和练习记录查看个人、学科与教材掌握情况。'
  },
  library: {
    title: '我的资料',
    description: '创建资料文件夹，查看当前文件夹中的文件，保持知识库结构清晰。'
  },
  planner: {
    title: '学习规划',
    description: '选择自我规划或 AI 规划：一个用于手动课程表，一个用于多轮讨论和草稿预览。'
  },
  chat: {
    title: '知识问答',
    description: '选择一个文件夹作为知识范围，进行资料溯源答疑或教师式抽问。'
  },
  editor: {
    title: '上传编辑',
    description: '上传文件、校正扫描文本，并保存为后续问答可检索的知识片段。'
  },
  mistakes: {
    title: '错题集',
    description: '上传错题、管理掌握状态，并从未掌握题目中随机刷题。'
  },
  personal: {
    title: '个人设置',
    description: '维护考研时间和一级学科文件夹。'
  },
  settings: {
    title: 'AI 设置',
    description: '配置答疑模式中的角色定位、提示词、模型服务和 API Key。'
  }
}

const knowledgeModuleMeta = {
  library: pageMeta.library,
  chat: pageMeta.chat,
  editor: pageMeta.editor
}

const currentPageMeta = computed(() => {
  if (activePage.value === 'knowledge' && knowledgeModule.value) {
    return knowledgeModuleMeta[knowledgeModule.value] || pageMeta.knowledge
  }
  return pageMeta[activePage.value] || pageMeta.knowledge
})
const pageTitle = computed(() => currentPageMeta.value.title)
const pageDescription = computed(() => currentPageMeta.value.description)
const todayIso = computed(() => toDateInputValue(new Date(homeClockNow.value)))
const examCountdownDays = computed(() => {
  if (!savedExamDate.value) return null
  const target = parseDateInput(savedExamDate.value)
  const today = parseDateInput(todayIso.value)
  return Math.ceil((target.getTime() - today.getTime()) / 86400000)
})
const canSubmitOnboarding = computed(() => {
  return Boolean(onboardingForm.examDate)
    && onboardingForm.subjects.length > 0
    && onboardingForm.subjects.every((name) => name.trim())
})
const canSavePersonalSettings = computed(() => {
  return Boolean(personalSettingsForm.examDate)
    && personalSettingsForm.subjects.length > 0
    && personalSettingsForm.subjects.every((name) => name.trim())
})
const subjectFolders = computed(() => folders.value.filter((folder) => folder.subjectFolder && !folder.parentId))
const selectedPressureSubject = computed(() =>
  profileSubjects.value.find((subject) => String(subject.subjectFolderId) === String(profilePressureSubjectId.value)) || null
)
const profileMetricCards = computed(() => {
  const overview = profileOverview.value || {}
  const toProgress = (value) => Math.max(0, Math.min(100, Math.round(Number(value || 0) * 100)))
  const overallConfidenceEnough = confidenceRank(overview.averageConfidenceLevel) >= confidenceRank('MEDIUM')
  const recentAccuracy = recentPracticeAccuracy(overview.recentCorrectCount, overview.recentWrongCount)
  const recentComparison = recentAccuracyComparison(profileFourteenDayActivity.value?.daily || [])
  const examProgress = examPrepProgress(overview.examDate)
  return [
    {
      label: '整体掌握度',
      value: overallConfidenceEnough ? formatPercent(overview.overallMasteryRate) : '未评估',
      detail: overallConfidenceEnough ? `已练习 ${overview.practicedChunkCount || 0} / ${overview.totalChunkCount || 0}` : '练习数据不足，请先去学习',
      icon: BookOpenCheck,
      accent: '#0f766e',
      progress: overallConfidenceEnough ? toProgress(overview.overallMasteryRate) : 0
    },
    {
      label: '重点突破',
      value: overview.weakChunkCount || 0,
      detail: `高风险 ${overview.highRiskChunkCount || 0}`,
      icon: Sparkles,
      accent: '#dc2626',
      progress: Math.min(100, Math.max(0, (overview.weakChunkCount || 0) * 12 + (overview.highRiskChunkCount || 0) * 16))
    },
    {
      label: '近14天正确率',
      value: recentAccuracy == null ? '未评估' : formatPercent(recentAccuracy),
      detail: recentComparison,
      icon: CalendarDays,
      accent: '#0891b2',
      progress: recentAccuracy == null ? 0 : toProgress(recentAccuracy)
    },
    {
      label: '考研倒计时',
      value: overview.daysUntilExam == null ? '--' : `${overview.daysUntilExam} 天`,
      detail: overview.examDate ? `备考进度 ${formatPercent(examProgress.rate)}` : '未设置考试日期',
      icon: Clock,
      accent: '#be123c',
      progress: toProgress(examProgress.rate)
    }
  ]
})
const profileDiagnosisSummary = computed(() => {
  const diagnosis = profileDiagnosis.value
  return diagnosis?.aiSummary || diagnosis?.summary || '上传资料并产生练习记录后，会生成学习诊断和今日建议。'
})
const profileDiagnosisMessage = computed(() => {
  if (profileDiagnosisLoading.value) {
    return '正在等待大模型生成学习诊断…'
  }
  return profileDiagnosisSummary.value
})
const profileDiagnosisInsufficient = computed(() => profileDiagnosis.value?.dataSufficient === false)
const profileDiagnosisItems = computed(() => profileDiagnosis.value?.items || [])
const profileSuggestions = computed(() => profileDiagnosis.value?.suggestions || [])
const mistakeSubjectOptions = computed(() => subjectFolders.value
  .map((folder) => ({
    folder,
    tag: mistakeSubjectTags.value.find((tag) => tag.name === folder.name)
  }))
  .filter((item) => item.tag)
)
const messages = computed(() => chatMessages[chatForm.mode])
const currentChatHasMessages = computed(() => messages.value.length > 0)
const chatInputDisabled = computed(() => chatLoading.value || (chatForm.mode !== 'TEACHER' && chatForm.useKnowledgeBase && !activeFolder.value))
const canSubmitChat = computed(() => !loading.value && !chatInputDisabled.value && (chatForm.mode === 'TEACHER' ? Boolean(activeFolder.value) : chatForm.question.trim().length > 0))
const chatPlaceholder = computed(() => chatForm.mode === 'TEACHER'
  ? '输入教师模式提问要求，例如：CPU、进程调度、排序算法'
  : chatForm.useKnowledgeBase
    ? '输入问题，或在教师模式下输入：开始抽问本章重点'
    : '不引用知识库，直接输入要和大模型聊的问题'
)
const pendingChatText = computed(() => {
  if (!chatForm.useKnowledgeBase) return '正在请求大模型生成回答…'
  return chatForm.deepAnswer ? '正在深度检索知识库并生成回答…' : '正在检索知识库并生成回答…'
})
const emptyChatTitle = computed(() => {
  if (!chatForm.useKnowledgeBase) return '直接和大模型聊天'
  return activeFolder.value ? '开始围绕当前知识库提问' : '先选择一个文件夹'
})
const emptyChatDescription = computed(() => {
  if (!chatForm.useKnowledgeBase) return '当前不会检索资料片段，也不会返回来源引用。'
  return '答疑模式会追溯资料来源，教师模式会根据知识点向你提问。'
})
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
      if (!collapsedFolderIds.value.has(folder.id)) {
        appendChildren(folder.id)
      }
    })
  }
  if (!rootFolderCollapsed.value) {
    appendChildren(null)
  }
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
const mistakeStatusOptions = computed(() => [
  { key: 'mastered', label: '完全掌握', mastered: true, statusId: null },
  ...mistakeStatuses.value
    .filter((status) => !status.mastered)
    .map((status) => ({ key: `status:${status.id}`, label: status.name, mastered: false, statusId: status.id }))
])
const unmasteredStatusOptions = computed(() => mistakeStatusOptions.value.filter((option) => !option.mastered))
const filteredMistakes = computed(() => {
  return mistakes.value.filter((mistake) => {
    const matchesSubject = browseSubjectFilterIds.value.length === 0 || (() => {
      const ids = new Set((mistake.subjectTags || []).map((tag) => tag.id))
      return browseSubjectFilterIds.value.some((id) => ids.has(id))
    })()
    const matchesStatus = browseStatusFilterKeys.value.length === 0 || browseStatusFilterKeys.value.includes(statusKeyForMistake(mistake))
    return matchesSubject && matchesStatus
  })
})
const practiceCurrentQuestion = computed(() => practiceQuestions.value[practiceIndex.value] || null)
const practiceClock = computed(() => {
  const minutes = Math.floor(practiceRemainingSeconds.value / 60)
  const seconds = practiceRemainingSeconds.value % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})
const planWeekDays = computed(() => {
  const start = parseDateInput(planWeekStart.value)
  return Array.from({ length: 7 }, (_, index) => {
    const date = addDays(start, index)
    const iso = toDateInputValue(date)
    return {
      iso,
      weekday: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][index],
      monthDay: `${date.getMonth() + 1}/${date.getDate()}`
    }
  })
})
const planWeekEnd = computed(() => planWeekDays.value.at(-1)?.iso || planWeekStart.value)
const planWeekLabel = computed(() => `${formatShortDate(planWeekStart.value)} - ${formatShortDate(planWeekEnd.value)}`)
const studyPlanItemsByDate = computed(() => groupPlanItemsByDate(studyPlanItems.value))
const todaysHomePlanItems = computed(() => homePlanItems.value
  .filter((item) => item.startDate === todayIso.value && item.status !== 'DONE')
  .sort((left, right) => `${left.startTime}`.localeCompare(`${right.startTime}`))
)
const currentHomeTask = computed(() => {
  const now = new Date(homeClockNow.value)
  const minutes = now.getHours() * 60 + now.getMinutes()
  const activeTask = todaysHomePlanItems.value.find((item) => {
    const start = timeToMinutes(item.startTime)
    const end = timeToMinutes(item.endTime)
    return start <= minutes && minutes <= end
  })
  if (activeTask) return activeTask
  return todaysHomePlanItems.value.find((item) => timeToMinutes(item.startTime) > minutes) || null
})
const currentHomeTaskState = computed(() => {
  if (!currentHomeTask.value) return ''
  const now = new Date(homeClockNow.value)
  const minutes = now.getHours() * 60 + now.getMinutes()
  return timeToMinutes(currentHomeTask.value.startTime) <= minutes ? '正在进行' : '下一项'
})
const planDraftItemsByDate = computed(() => groupPlanItemsByDate(planDraftItems.value))
const planDraftDirty = computed(() => planPendingOperations.value.length > 0)
const planDraftStats = computed(() => {
  const total = planDraftItems.value.length
  const created = planDraftItems.value.filter((item) => item.id < 0).length
  return { total, created }
})
const planStats = computed(() => {
  const total = studyPlanItems.value.length
  const done = studyPlanItems.value.filter((item) => item.status === 'DONE').length
  const high = studyPlanItems.value.filter((item) => item.priority === 'HIGH').length
  return { total, done, high }
})

function groupPlanItemsByDate(items) {
  const grouped = {}
  planWeekDays.value.forEach((day) => {
    grouped[day.iso] = []
  })
  items.forEach((item) => {
    if (!grouped[item.startDate]) grouped[item.startDate] = []
    grouped[item.startDate].push(item)
  })
  Object.values(grouped).forEach((items) => {
    items.sort((left, right) => `${left.startTime}`.localeCompare(`${right.startTime}`))
  })
  return grouped
}

function toDateInputValue(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function parseDateInput(value) {
  const [year, month, day] = String(value).split('-').map(Number)
  return new Date(year, month - 1, day)
}

function addDays(date, days) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

function startOfWeekIso(date) {
  const base = new Date(date)
  const day = base.getDay()
  const offset = day === 0 ? -6 : 1 - day
  base.setDate(base.getDate() + offset)
  return toDateInputValue(base)
}

function formatShortDate(value) {
  const date = parseDateInput(value)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function isToday(value) {
  return value === toDateInputValue(new Date())
}

function normalizeTimeValue(value) {
  return String(value || '').slice(0, 5)
}

function planTypeLabel(type) {
  return {
    COURSE: '课程',
    SELF_STUDY: '自习',
    REVIEW: '复盘',
    EXAM: '考试',
    TASK: '任务',
    REST: '休息'
  }[type] || '自习'
}

function planPriorityLabel(priority) {
  return { LOW: '低', MEDIUM: '中', HIGH: '高' }[priority] || '中'
}

function planStatusLabel(status) {
  return { TODO: '待完成', DONE: '已完成', SKIPPED: '已跳过' }[status] || '待完成'
}

function timeToMinutes(value) {
  const [hours, minutes] = normalizeTimeValue(value).split(':').map(Number)
  return (Number.isFinite(hours) ? hours : 0) * 60 + (Number.isFinite(minutes) ? minutes : 0)
}

function examDateStorageKey() {
  const userKey = session.value?.userId || session.value?.username || 'guest'
  return `smart_exam_date:${userKey}`
}

function loadExamDate() {
  try {
    const value = localStorage.getItem(examDateStorageKey()) || ''
    return /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : ''
  } catch {
    return ''
  }
}

async function saveExamDate() {
  if (!examDate.value) return
  if (studyProfile.value?.onboarded) {
    await run(async () => {
      studyProfile.value = await studyProfileApi.update({ examDate: examDate.value })
      savedExamDate.value = studyProfile.value.examDate || examDate.value
      await loadKnowledgeProfile()
    })
  } else {
    localStorage.setItem(examDateStorageKey(), examDate.value)
    savedExamDate.value = examDate.value
  }
}

function clearExamDate() {
  localStorage.removeItem(examDateStorageKey())
  savedExamDate.value = ''
  examDate.value = ''
}

function openExamDatePicker() {
  examDate.value = savedExamDate.value || todayIso.value
  const input = examDateInput.value
  if (!input) return
  try {
    input.focus()
    if (typeof input.showPicker === 'function') {
      input.showPicker()
      return
    }
    input.click()
  } catch {
    input.click()
  }
}

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
  const savedMessages = messages.value.map(({ role, content, sources, teacherQuestion, referenceAnswer, chunkId, feedbackType, addedToMistake }) => ({
    role,
    content,
    sources,
    teacherQuestion,
    referenceAnswer,
    chunkId,
    feedbackType,
    addedToMistake
  }))
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
  if (!activeFolder.value && chatForm.useKnowledgeBase) return
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

function deleteChatHistory(item) {
  if (!activeFolder.value || !item) return
  if (!window.confirm(`确定删除“${item.title}”这条历史记录吗？`)) return
  folderChatHistories.value = folderChatHistories.value.filter((history) => history.id !== item.id)
  localStorage.setItem(folderChatHistoryKey(activeFolder.value.id), JSON.stringify(folderChatHistories.value))
  if (activeConversationIds[item.mode] === item.id) {
    chatMessages[item.mode].splice(0)
    activeConversationIds[item.mode] = null
    activeSource.value = null
  }
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

function setActivePage(page) {
  if (activePage.value === 'planner') {
    persistPlanSession()
  }
  activePage.value = page
  if (page === 'knowledge') {
    knowledgeModule.value = ''
  }
  if (page === 'planner') {
    planModule.value = ''
  }
  if (page === 'profile') {
    loadKnowledgeProfile()
    ensureDailyProfileDiagnosis()
  }
}

function openKnowledgeModule(module) {
  activePage.value = 'knowledge'
  knowledgeModule.value = module
}

onMounted(() => {
  homeClockTimerId.value = window.setInterval(() => {
    homeClockNow.value = Date.now()
  }, 60000)
  window.addEventListener('resize', resizeProfileCharts)
  if (session.value) {
    run(initializeSessionData)
  }
})

onUnmounted(() => {
  if (homeClockTimerId.value) {
    window.clearInterval(homeClockTimerId.value)
    homeClockTimerId.value = null
  }
  window.removeEventListener('resize', resizeProfileCharts)
  disposeProfileCharts()
})

async function submitAuth() {
  await run(async () => {
    const action = authMode.value === 'login' ? authApi.login : authApi.register
    const result = await action(authForm)
    setSession(result)
    session.value = result
    await initializeSessionData()
  })
}

async function initializeSessionData() {
  await loadStudyProfile()
  if (!studyProfile.value?.onboarded) return
  await loadFolders()
  await loadRemoteAiSettings()
  await loadMistakeData()
  await loadStudyPlan()
  await loadKnowledgeProfile()
  ensureDailyProfileDiagnosis()
}

async function loadStudyProfile() {
  const profile = await studyProfileApi.get()
  studyProfile.value = profile
  savedExamDate.value = profile.examDate || loadExamDate()
  examDate.value = savedExamDate.value
  syncPersonalSettingsForm(profile)
  if (!profile.onboarded) {
    onboardingForm.examDate = profile.examDate || todayIso.value
    syncOnboardingSubjects()
  }
}

function syncPersonalSettingsForm(profile = studyProfile.value) {
  personalSettingsForm.examDate = profile?.examDate || savedExamDate.value || todayIso.value
  const subjects = (profile?.subjects || subjectFolders.value).map((subject) => subject.name).filter(Boolean)
  personalSettingsForm.subjects = subjects.length ? subjects : ['']
}

function addPersonalSubject() {
  if (personalSettingsForm.subjects.length < 12) {
    personalSettingsForm.subjects.push('')
  }
}

function removePersonalSubject(index) {
  if (personalSettingsForm.subjects.length <= 1) return
  personalSettingsForm.subjects.splice(index, 1)
}

async function savePersonalSettings() {
  if (!canSavePersonalSettings.value) return
  await run(async () => {
    studyProfile.value = await studyProfileApi.update({
      examDate: personalSettingsForm.examDate,
      subjects: personalSettingsForm.subjects.map((name) => name.trim()).filter(Boolean)
    })
    savedExamDate.value = studyProfile.value.examDate || ''
    examDate.value = savedExamDate.value
    await loadFolders()
    await loadMistakeData()
    await loadKnowledgeProfile()
    syncPersonalSettingsForm()
  })
}

function syncOnboardingSubjects() {
  const count = Math.max(1, Math.min(Number(onboardingForm.subjectCount) || 1, 12))
  onboardingForm.subjectCount = count
  while (onboardingForm.subjects.length < count) {
    onboardingForm.subjects.push('')
  }
  while (onboardingForm.subjects.length > count) {
    onboardingForm.subjects.pop()
  }
}

async function submitOnboarding() {
  if (!canSubmitOnboarding.value) return
  await run(async () => {
    studyProfile.value = await studyProfileApi.onboard({
      examDate: onboardingForm.examDate,
      subjects: onboardingForm.subjects.map((name) => name.trim()).filter(Boolean)
    })
    savedExamDate.value = studyProfile.value.examDate || ''
    examDate.value = savedExamDate.value
    await initializeSessionData()
  })
}

async function loadStudyPlan() {
  await run(async () => {
    const items = await studyPlanApi.list(planWeekStart.value, planWeekEnd.value)
    studyPlanItems.value = items
    syncHomePlanItemsFromCurrentWeek(items)
    restorePlanSession()
  })
}

function syncHomePlanItemsFromCurrentWeek(items = studyPlanItems.value) {
  if (planWeekStart.value === startOfWeekIso(new Date())) {
    homePlanItems.value = clonePlanItems(items)
  }
}

function clonePlanItems(items = studyPlanItems.value) {
  return items.map((item) => ({ ...item }))
}

function clonePlanOperations(items) {
  return (items || []).map((item) => ({ ...item }))
}

function currentPlanSessionKey() {
  const userKey = session.value?.userId || session.value?.username || 'guest'
  return `${userKey}:${planWeekStart.value}`
}

function persistPlanSession() {
  if (!session.value) return
  const key = currentPlanSessionKey()
  planSessionByWeek.value = {
    ...planSessionByWeek.value,
    [key]: {
      draftItems: clonePlanItems(planDraftItems.value),
      pendingOperations: clonePlanOperations(planPendingOperations.value),
      lastOperations: clonePlanOperations(planLastOperations.value),
      messages: planAiMessages.value.map((message) => ({ ...message })),
      input: planAiInput.value
    }
  }
}

function restorePlanSession() {
  const saved = planSessionByWeek.value[currentPlanSessionKey()]
  if (!saved) {
    planDraftItems.value = clonePlanItems(studyPlanItems.value)
    planPendingOperations.value = []
    planLastOperations.value = []
    planAiMessages.value = initialPlanAiMessages()
    planAiInput.value = ''
    persistPlanSession()
    return
  }
  planPendingOperations.value = clonePlanOperations(saved.pendingOperations)
  planLastOperations.value = clonePlanOperations(saved.lastOperations)
  planAiMessages.value = saved.messages?.length ? saved.messages.map((message) => ({ ...message })) : initialPlanAiMessages()
  planAiInput.value = saved.input || ''
  planDraftItems.value = planPendingOperations.value.length
    ? clonePlanItems(saved.draftItems)
    : clonePlanItems(studyPlanItems.value)
}

function syncPlanDraftFromReal() {
  planDraftItems.value = clonePlanItems(studyPlanItems.value)
  planPendingOperations.value = []
  planLastOperations.value = []
  persistPlanSession()
}

function resetPlanDraft() {
  syncPlanDraftFromReal()
  planAiMessages.value.push({ role: 'assistant', content: '草稿已恢复为当前真实日程。' })
  persistPlanSession()
}

function pushPlanUndoSnapshot(label) {
  planUndoStack.value = [
    { id: crypto.randomUUID(), label, items: clonePlanItems(studyPlanItems.value), createdAt: Date.now() },
    ...planUndoStack.value
  ].slice(0, 8)
}

async function undoStudyPlanChange() {
  const snapshot = planUndoStack.value[0]
  if (!snapshot || planUndoLoading.value) return
  planUndoLoading.value = true
  error.value = ''
  try {
    await restoreStudyPlanSnapshot(snapshot.items)
    planUndoStack.value = planUndoStack.value.slice(1)
    await loadStudyPlan()
  } catch (err) {
    error.value = err.message
  } finally {
    planUndoLoading.value = false
  }
}

async function restoreStudyPlanSnapshot(snapshotItems) {
  const currentItems = await studyPlanApi.list(planWeekStart.value, planWeekEnd.value)
  const snapshotById = new Map(snapshotItems.filter((item) => item.id > 0).map((item) => [item.id, item]))
  const currentById = new Map(currentItems.filter((item) => item.id > 0).map((item) => [item.id, item]))

  for (const item of currentItems) {
    if (item.id > 0 && !snapshotById.has(item.id)) {
      await studyPlanApi.delete(item.id)
    }
  }
  for (const item of snapshotItems) {
    const payload = studyPlanPayloadFromItem(item)
    if (item.id > 0 && currentById.has(item.id)) {
      await studyPlanApi.update(item.id, payload)
    } else {
      await studyPlanApi.create(payload)
    }
  }
}

async function shiftPlanWeek(delta) {
  persistPlanSession()
  planWeekStart.value = toDateInputValue(addDays(parseDateInput(planWeekStart.value), delta * 7))
  await loadStudyPlan()
}

async function goCurrentPlanWeek() {
  persistPlanSession()
  planWeekStart.value = startOfWeekIso(new Date())
  await loadStudyPlan()
}

function openNewStudyPlan(date) {
  editingPlanItem.value = null
  resetPlanForm(date)
}

function openPlanModule(module) {
  planModule.value = module
  if (module === 'ai') {
    restorePlanSession()
  }
}

function openPlannerAiPage() {
  openPlanModule('ai')
}

function editStudyPlan(item) {
  editingPlanItem.value = item
  Object.assign(planForm, {
    title: item.title || '',
    subject: item.subject || '',
    description: item.description || '',
    itemType: item.itemType || 'SELF_STUDY',
    startDate: item.startDate,
    startTime: normalizeTimeValue(item.startTime),
    endTime: normalizeTimeValue(item.endTime),
    location: item.location || '',
    priority: item.priority || 'MEDIUM',
    status: item.status || 'TODO'
  })
}

function resetPlanForm(date = planForm.startDate || toDateInputValue(new Date())) {
  editingPlanItem.value = null
  Object.assign(planForm, {
    title: '',
    subject: '',
    description: '',
    itemType: 'SELF_STUDY',
    startDate: date,
    startTime: '19:00',
    endTime: '21:00',
    location: '',
    priority: 'MEDIUM',
    status: 'TODO'
  })
}

function studyPlanPayloadFromForm() {
  return {
    title: planForm.title,
    subject: planForm.subject,
    description: planForm.description,
    itemType: planForm.itemType,
    startDate: planForm.startDate,
    startTime: planForm.startTime,
    endTime: planForm.endTime,
    location: planForm.location,
    priority: planForm.priority,
    status: planForm.status
  }
}

function studyPlanPayloadFromItem(item, patch = {}) {
  return {
    title: item.title,
    subject: item.subject,
    description: item.description,
    itemType: item.itemType,
    startDate: item.startDate,
    startTime: normalizeTimeValue(item.startTime),
    endTime: normalizeTimeValue(item.endTime),
    location: item.location,
    priority: item.priority,
    status: item.status,
    ...patch
  }
}

async function saveStudyPlanItem() {
  await run(async () => {
    pushPlanUndoSnapshot(editingPlanItem.value ? '修改规划' : '新增规划')
    if (editingPlanItem.value) {
      await studyPlanApi.update(editingPlanItem.value.id, studyPlanPayloadFromForm())
    } else {
      await studyPlanApi.create(studyPlanPayloadFromForm())
    }
    await loadStudyPlan()
    resetPlanForm(planForm.startDate)
  })
}

async function deleteStudyPlanItem(item) {
  if (!item || !window.confirm(`确定删除“${item.title}”吗？`)) return
  await run(async () => {
    pushPlanUndoSnapshot('删除规划')
    await studyPlanApi.delete(item.id)
    if (editingPlanItem.value?.id === item.id) resetPlanForm(item.startDate)
    await loadStudyPlan()
  })
}

async function toggleStudyPlanDone(item) {
  const nextStatus = item.status === 'DONE' ? 'TODO' : 'DONE'
  await run(async () => {
    pushPlanUndoSnapshot(nextStatus === 'DONE' ? '标记完成' : '标记待完成')
    await studyPlanApi.update(item.id, studyPlanPayloadFromItem(item, { status: nextStatus }))
    await loadStudyPlan()
  })
}

function compactPlanMessages() {
  return planAiMessages.value
    .filter((message) => message.role === 'user' || message.role === 'assistant')
    .map(({ role, content }) => ({ role, content: cleanPlanAssistantContent(role, content) }))
    .filter((message) => message.content?.trim())
    .filter((message, index) => !(index === 0 && message.role === 'assistant'))
}

function displayPlanAiMessage(message) {
  return cleanPlanAssistantContent(message.role, message.content)
}

function cleanPlanAssistantContent(role, content) {
  if (role !== 'assistant') return content
  return String(content || '')
    .replace(/\n*\s*草稿操作：[\s\S]*$/u, '')
    .replace(/\n*\s*当前只是草稿，保存后才会写入真实日程。$/u, '')
    .trim()
}

function planAiBasePayload() {
  return {
    ...aiSettings,
    fromDate: planWeekStart.value,
    toDate: planWeekEnd.value,
    messages: compactPlanMessages()
  }
}

function initialPlanAiMessages() {
  return [{ role: 'assistant', content: '我会结合当前周规划帮你调整学习节奏。每次发送后都会直接更新右侧草稿，确认后再保存到真实日程。' }]
}

function clearPlanAiChat() {
  planAiMessages.value = initialPlanAiMessages()
  planAiInput.value = ''
  syncPlanDraftFromReal()
  persistPlanSession()
}

async function sendPlanAiMessage() {
  const content = planAiInput.value.trim()
  if (!content || planAiLoading.value || planGenerateLoading.value || planSaveLoading.value) return
  planAiMessages.value.push({ role: 'user', content })
  planAiInput.value = ''
  await updateStudyPlanDraftFromAi('请根据用户最新消息直接更新当前周学习规划草稿。这里只预览，不要假设已经保存；如果信息不足或用户只是询问，请返回空 actions 并用 reply 简短说明。')
}

async function generateStudyPlanFromAi() {
  const pending = planAiInput.value.trim()
  if (pending) {
    planAiMessages.value.push({ role: 'user', content: pending })
    planAiInput.value = ''
  }
  await updateStudyPlanDraftFromAi('请生成当前周学习规划草稿。这里只预览，不要假设已经保存。')
}

async function updateStudyPlanDraftFromAi(instruction) {
  if (planGenerateLoading.value || planSaveLoading.value) return
  planGenerateLoading.value = true
  error.value = ''
  try {
    const response = await studyPlanApi.generate({
      ...planAiBasePayload(),
      instruction
    })
    planDraftItems.value = response.items || clonePlanItems(studyPlanItems.value)
    planLastOperations.value = response.operations || []
    planPendingOperations.value = (response.operations || []).map(operationForApply)
    planAiMessages.value.push({ role: 'assistant', content: response.reply || '已更新草稿规划。' })
    persistPlanSession()
  } catch (err) {
    error.value = err.message
  } finally {
    planGenerateLoading.value = false
  }
}

function operationForApply(operation) {
  return {
    operation: operation.operation,
    id: operation.id && operation.id > 0 ? operation.id : null,
    title: operation.title,
    subject: operation.subject,
    description: operation.description,
    itemType: operation.itemType,
    startDate: operation.startDate,
    startTime: operation.startTime ? normalizeTimeValue(operation.startTime) : null,
    endTime: operation.endTime ? normalizeTimeValue(operation.endTime) : null,
    location: operation.location,
    priority: operation.priority,
    status: operation.status
  }
}

async function savePlanAiDraft() {
  if (!planPendingOperations.value.length || planSaveLoading.value) return
  planSaveLoading.value = true
  error.value = ''
  try {
    pushPlanUndoSnapshot('保存 AI 草稿')
    const response = await studyPlanApi.apply({
      fromDate: planWeekStart.value,
      toDate: planWeekEnd.value,
      operations: planPendingOperations.value
    })
    studyPlanItems.value = response.items || []
    syncHomePlanItemsFromCurrentWeek(studyPlanItems.value)
    syncPlanDraftFromReal()
    planLastOperations.value = response.operations || []
    planAiMessages.value.push({ role: 'assistant', content: response.reply || '草稿已保存到真实日程。' })
    persistPlanSession()
  } catch (err) {
    error.value = err.message
  } finally {
    planSaveLoading.value = false
  }
}

async function loadMistakeData() {
  await run(async () => {
    const [statuses, subjectTags, mistakeList] = await Promise.all([
      mistakeApi.listStatuses(),
      mistakeApi.listSubjectTags(),
      mistakeApi.list()
    ])
    mistakeStatuses.value = statuses
    mistakeSubjectTags.value = subjectTags
    mistakes.value = mistakeList
    activeMistake.value = mistakeList[0] || null
    await refreshAttachmentPreviews(mistakeList)
  })
}

function selectedMistakeStatus() {
  if (mistakeForm.statusKey === 'mastered') {
    return { mastered: true, statusId: null }
  }
  const option = mistakeStatusOptions.value.find((status) => status.key === mistakeForm.statusKey)
  return option || { mastered: false, statusId: null }
}

function resetMistakeForm() {
  editingMistake.value = null
  mistakeForm.questionText = ''
  mistakeForm.solutionText = ''
  mistakeForm.statusKey = 'mastered'
  mistakeForm.subjectTagIds = []
  mistakeForm.linkedChunks = []
  mistakeChunkQuery.value = ''
  mistakeChunkCandidates.value = []
  mistakeChunkSubjectFolderId.value = ''
  mistakeChunkFileId.value = ''
  mistakeChunkFiles.value = []
  if (mistakeQuestionAttachmentFile.value) mistakeQuestionAttachmentFile.value.value = ''
  if (mistakeSolutionFile.value) mistakeSolutionFile.value.value = ''
  clearImageItems('question')
  clearImageItems('solution')
}

function editMistake(mistake) {
  editingMistake.value = mistake
  mistakeForm.questionText = mistake.questionText || ''
  mistakeForm.solutionText = mistake.solutionText || ''
  mistakeForm.statusKey = mistake.mastered ? 'mastered' : mistake.statusId ? `status:${mistake.statusId}` : 'mastered'
  mistakeForm.subjectTagIds = (mistake.subjectTags || []).map((tag) => tag.id)
  mistakeForm.linkedChunks = [...(mistake.linkedChunks || [])]
  mistakeChunkQuery.value = ''
  mistakeChunkCandidates.value = []
  mistakeChunkFileId.value = ''
  mistakeChunkSubjectFolderId.value = subjectFolderForMistake(mistake)?.id || ''
  loadMistakeChunkFiles()
  questionImageItems.value = savedImageItems(mistake.questionAttachments || [], mistake, 'question')
  solutionImageItems.value = savedImageItems(mistake.solutionAttachments || [], mistake, 'solution')
  activeMistake.value = mistake
  mistakeModule.value = 'upload'
}

function openMistakeModule(module) {
  mistakeModule.value = module
}

function backToMistakeMenu() {
  if (mistakeModule.value === 'practice') {
    closePractice()
  }
  mistakeModule.value = ''
}

async function saveMistake() {
  const status = selectedMistakeStatus()
  const payload = {
    questionText: mistakeForm.questionText,
    questionImageFiles: newImageItems(questionImageItems.value).map((item) => item.file),
    questionImageNames: newImageItems(questionImageItems.value).map((item) => item.displayName),
    retainedQuestionAttachmentIds: retainedAttachmentIds(questionImageItems.value),
    solutionText: mistakeForm.solutionText,
    solutionImageFiles: newImageItems(solutionImageItems.value).map((item) => item.file),
    solutionImageNames: newImageItems(solutionImageItems.value).map((item) => item.displayName),
    retainedSolutionAttachmentIds: retainedAttachmentIds(solutionImageItems.value),
    mastered: status.mastered,
    statusId: status.statusId,
    subjectTagIds: mistakeForm.subjectTagIds,
    chunkIds: mistakeForm.linkedChunks.map((chunk) => chunk.chunkId)
  }
  await run(async () => {
    const saved = editingMistake.value
      ? await mistakeApi.update(editingMistake.value.id, payload)
      : await mistakeApi.create(payload)
    mistakes.value = [saved, ...mistakes.value.filter((item) => item.id !== saved.id)]
    activeMistake.value = saved
    resetMistakeForm()
    await refreshAttachmentPreviews(mistakes.value)
  })
}

function setUnmasteredStatus() {
  if (mistakeForm.statusKey === 'mastered') {
    mistakeForm.statusKey = unmasteredStatusOptions.value[0]?.key || 'unmastered'
  }
}

async function searchMistakeChunks() {
  mistakeChunkSearchLoading.value = true
  await run(async () => {
    mistakeChunkCandidates.value = await knowledgeProfileApi.searchChunks({
      query: mistakeChunkQuery.value.trim() || mistakeForm.questionText.trim(),
      folderId: mistakeChunkSubjectFolderId.value || activeFolder.value?.id || null,
      fileId: mistakeChunkFileId.value || null,
      limit: 20
    })
  })
  mistakeChunkSearchLoading.value = false
}

async function handleMistakeChunkSubjectChange() {
  mistakeChunkFileId.value = ''
  await loadMistakeChunkFiles()
  syncMistakeSubjectTagFromFolder()
  mistakeChunkCandidates.value = []
}

async function loadMistakeChunkFiles() {
  if (!mistakeChunkSubjectFolderId.value) {
    mistakeChunkFiles.value = []
    return
  }
  mistakeChunkFiles.value = await knowledgeProfileApi.files({ folderId: mistakeChunkSubjectFolderId.value })
}

function syncMistakeSubjectTagFromFolder() {
  const folder = subjectFolders.value.find((item) => String(item.id) === String(mistakeChunkSubjectFolderId.value))
  if (!folder) return
  const tag = mistakeSubjectTags.value.find((item) => item.name === folder.name)
  if (tag && !mistakeForm.subjectTagIds.includes(tag.id)) {
    mistakeForm.subjectTagIds = [tag.id, ...mistakeForm.subjectTagIds]
  }
}

function subjectFolderForMistake(mistake) {
  const tagNames = new Set((mistake.subjectTags || []).map((tag) => tag.name))
  return subjectFolders.value.find((folder) => tagNames.has(folder.name))
}

function isMistakeChunkLinked(chunkId) {
  return mistakeForm.linkedChunks.some((chunk) => chunk.chunkId === chunkId)
}

function addMistakeLinkedChunk(chunk) {
  if (!chunk?.chunkId || isMistakeChunkLinked(chunk.chunkId)) return
  mistakeForm.linkedChunks = [...mistakeForm.linkedChunks, chunk]
}

function removeMistakeLinkedChunk(chunkId) {
  mistakeForm.linkedChunks = mistakeForm.linkedChunks.filter((chunk) => chunk.chunkId !== chunkId)
}

function openChunkDetail(chunk) {
  activeChunkDetail.value = chunk
}

function onImageSelect(kind) {
  const input = kind === 'question' ? mistakeQuestionAttachmentFile.value : mistakeSolutionFile.value
  const target = kind === 'question' ? questionImageItems : solutionImageItems
  const selected = Array.from(input?.files || [])
  const nextItems = selected.map((file) => ({
    id: crypto.randomUUID(),
    file,
    displayName: displayNameWithoutExtension(file.name),
    previewUrl: URL.createObjectURL(file)
  }))
  target.value = [...target.value, ...nextItems]
  if (input) {
    input.value = ''
  }
}

function removeImageItem(kind, id) {
  const target = kind === 'question' ? questionImageItems : solutionImageItems
  const item = target.value.find((entry) => entry.id === id)
  if (item?.previewUrl && !item.saved) URL.revokeObjectURL(item.previewUrl)
  target.value = target.value.filter((entry) => entry.id !== id)
}

function clearImageItems(kind) {
  const target = kind === 'question' ? questionImageItems : solutionImageItems
  target.value.forEach((item) => {
    if (!item.saved) URL.revokeObjectURL(item.previewUrl)
  })
  target.value = []
}

function enlargeAttachment(item) {
  enlargedAttachment.value = item
}

function enlargeSavedAttachment(attachment) {
  const url = attachment.id ? attachmentPreviewUrls.value[attachment.id] : null
  if (!url) return
  enlargedAttachment.value = {
    ...attachment,
    url
  }
}

function displayNameWithoutExtension(name = '') {
  return name.replace(/\.[^.\\/\s]+$/u, '') || name || '图片'
}

async function createMistakeStatus() {
  const name = newMistakeStatusName.value.trim()
  if (!name) return
  await run(async () => {
    const status = await mistakeApi.createStatus({ name })
    mistakeStatuses.value.push(status)
    mistakeForm.statusKey = `status:${status.id}`
    newMistakeStatusName.value = ''
  })
}

async function createSubjectTag() {
  const name = newMistakeSubjectTagName.value.trim()
  if (!name) return
  await run(async () => {
    const tag = await mistakeApi.createSubjectTag({ name })
    mistakeSubjectTags.value.push(tag)
    if (mistakeModule.value === 'upload' && !mistakeForm.subjectTagIds.includes(tag.id)) {
      mistakeForm.subjectTagIds.push(tag.id)
    }
    newMistakeSubjectTagName.value = ''
    subjectTagCreatorOpen.value = false
  })
}

async function deleteSubjectTag(tag) {
  if (!tag?.id || !window.confirm(`确定删除“${tag.name}”这个科目标签吗？如果已有错题使用它，需要先更换标签或删除对应错题。`)) return
  await run(async () => {
    await mistakeApi.deleteSubjectTag(tag.id)
    mistakeSubjectTags.value = mistakeSubjectTags.value.filter((item) => item.id !== tag.id)
    removeIdFromArray(mistakeForm.subjectTagIds, tag.id)
    removeIdFromArray(practiceForm.subjectTagIds, tag.id)
    removeIdFromArray(browseSubjectFilterIds.value, tag.id)
  })
}

function toggleIdInArray(array, id) {
  const index = array.indexOf(id)
  if (index >= 0) {
    array.splice(index, 1)
  } else {
    array.push(id)
  }
}

function removeIdFromArray(array, id) {
  const index = array.indexOf(id)
  if (index >= 0) {
    array.splice(index, 1)
  }
}

async function recognizeMistakeFile() {
  const selected = recognitionFile.value?.files?.[0]
  if (!selected || recognitionLoading.value) return
  recognitionLoading.value = true
  await run(async () => {
    const result = await mistakeApi.recognize(selected)
    recognitionText.value = result.text || ''
  })
  recognitionLoading.value = false
}

async function copyRecognitionText() {
  if (!recognitionText.value) return
  await navigator.clipboard.writeText(recognitionText.value)
}

async function deleteMistakeStatus(status) {
  if (!status?.id || !window.confirm(`确定删除“${status.name}”这个未掌握状态吗？`)) return
  await run(async () => {
    await mistakeApi.deleteStatus(status.id)
    mistakeStatuses.value = mistakeStatuses.value.filter((item) => item.id !== status.id)
    if (mistakeForm.statusKey === `status:${status.id}`) {
      mistakeForm.statusKey = 'mastered'
    }
    removeIdFromArray(browseStatusFilterKeys.value, `status:${status.id}`)
  })
}

function savedImageItems(attachments, mistake, kind) {
  return attachments
    .filter((attachment) => attachment.image)
    .map((attachment) => {
      const fallbackUrl = kind === 'question'
        ? questionPreviewUrls.value[mistake.id]
        : solutionPreviewUrls.value[mistake.id]
      return {
        id: `saved:${kind}:${attachment.id || mistake.id}`,
        attachmentId: attachment.id,
        displayName: displayNameWithoutExtension(attachment.displayName || attachment.originalName || ''),
        previewUrl: attachment.id ? attachmentPreviewUrls.value[attachment.id] : fallbackUrl,
        saved: true
      }
    })
}

function newImageItems(items) {
  return items.filter((item) => item.file)
}

function retainedAttachmentIds(items) {
  return items.filter((item) => item.saved && item.attachmentId).map((item) => item.attachmentId)
}

async function setMistakeStatus(mistake, option) {
  await run(async () => {
    const saved = await mistakeApi.updateMistakeStatus(mistake.id, {
      mastered: option.mastered,
      statusId: option.statusId
    })
    mistakes.value = mistakes.value.map((item) => (item.id === saved.id ? saved : item))
    if (activeMistake.value?.id === saved.id) activeMistake.value = saved
    if (editingMistake.value?.id === saved.id) editMistake(saved)
  })
}

function statusKeyForMistake(mistake) {
  if (mistake.mastered) return 'mastered'
  return mistake.statusId ? `status:${mistake.statusId}` : 'mastered'
}

function openMistakeStatusEditor(mistake) {
  editingStatusMistakeId.value = mistake.id
  nextTick(() => {
    document.querySelector(`[data-status-editor-id="${mistake.id}"]`)?.focus()
  })
}

async function changeMistakeStatusFromSelect(mistake, event) {
  const option = mistakeStatusOptions.value.find((item) => item.key === event.target.value)
  editingStatusMistakeId.value = null
  if (!option || option.key === statusKeyForMistake(mistake)) return
  await setMistakeStatus(mistake, option)
}

function isBrowseSolutionVisible(mistake) {
  return browseSolutionVisibility.value[mistake.id] ?? showBrowseSolution.value
}

function toggleBrowseSolution(mistake) {
  browseSolutionVisibility.value = {
    ...browseSolutionVisibility.value,
    [mistake.id]: !isBrowseSolutionVisible(mistake)
  }
}

function setAllBrowseSolutions(visible) {
  showBrowseSolution.value = visible
  browseSolutionVisibility.value = Object.fromEntries(filteredMistakes.value.map((mistake) => [mistake.id, visible]))
}

async function deleteMistake(mistake) {
  if (!mistake || !window.confirm('确定删除这道错题吗？')) return
  await run(async () => {
    await mistakeApi.delete(mistake.id)
    mistakes.value = mistakes.value.filter((item) => item.id !== mistake.id)
    activeMistake.value = mistakes.value[0] || null
    if (editingMistake.value?.id === mistake.id) resetMistakeForm()
    if (editingStatusMistakeId.value === mistake.id) editingStatusMistakeId.value = null
    const nextVisibility = { ...browseSolutionVisibility.value }
    delete nextVisibility[mistake.id]
    browseSolutionVisibility.value = nextVisibility
    revokeQuestionPreview(mistake.id)
    revokeSolutionPreview(mistake.id)
  })
}

async function startPractice() {
  await run(async () => {
    practiceQuestions.value = await mistakeApi.practice(practiceForm.count, practiceForm.subjectTagIds)
    practiceIndex.value = 0
    practiceStarted.value = practiceQuestions.value.length > 0
    practiceFinished.value = false
    practiceResults.value = {}
    stopPracticeTimer()
    if (practiceForm.timed && practiceStarted.value) {
      practiceRemainingSeconds.value = Math.max(1, Number(practiceForm.minutes || 1)) * 60
      practiceTimerId.value = window.setInterval(() => {
        practiceRemainingSeconds.value -= 1
        if (practiceRemainingSeconds.value <= 0) {
          finishPractice()
        }
      }, 1000)
    }
  })
}

function finishPractice() {
  practiceFinished.value = true
  stopPracticeTimer()
}

function stopPracticeTimer() {
  if (practiceTimerId.value) {
    window.clearInterval(practiceTimerId.value)
    practiceTimerId.value = null
  }
}

function closePractice() {
  stopPracticeTimer()
  practiceStarted.value = false
  practiceFinished.value = false
  practiceQuestions.value = []
  practiceIndex.value = 0
  practiceResults.value = {}
  practiceRemainingSeconds.value = 0
}

function nextPracticeQuestion(delta) {
  if (!practiceQuestions.value.length) return
  practiceIndex.value = Math.min(practiceQuestions.value.length - 1, Math.max(0, practiceIndex.value + delta))
}

function practiceResultFor(question) {
  return question?.id ? practiceResults.value[question.id] : null
}

async function recordPracticeResult(question, correct) {
  if (!question?.id || practiceResultFor(question)) return
  await run(async () => {
    const result = await mistakeApi.recordPracticeResult(question.id, { correct })
    practiceResults.value = { ...practiceResults.value, [question.id]: result }
    practiceQuestions.value = practiceQuestions.value.map((item) => item.id === question.id
      ? { ...item, linkedChunks: result.linkedChunks || item.linkedChunks }
      : item)
    mistakes.value = mistakes.value.map((item) => item.id === question.id
      ? { ...item, linkedChunks: result.linkedChunks || item.linkedChunks }
      : item)
    await loadKnowledgeProfile()
  })
}

async function refreshAttachmentPreviews(items) {
  for (const item of items) {
    for (const attachment of [...(item.questionAttachments || []), ...(item.solutionAttachments || [])]) {
      if (attachment.id && attachment.image && !attachmentPreviewUrls.value[attachment.id]) {
        await loadSavedAttachmentPreview(attachment)
      }
    }
    if (item.hasQuestionFile && (item.questionContentType || '').startsWith('image/') && !questionPreviewUrls.value[item.id]) {
      await loadAttachmentPreview(item, 'question')
    }
    if (item.hasSolutionFile && (item.solutionContentType || '').startsWith('image/') && !solutionPreviewUrls.value[item.id]) {
      await loadAttachmentPreview(item, 'solution')
    }
  }
}

async function loadSavedAttachmentPreview(attachment) {
  const response = await fetch(mistakeApi.attachmentUrl(attachment.id), {
    headers: { Authorization: `Bearer ${getToken()}` }
  })
  if (!response.ok) return
  const blob = await response.blob()
  attachmentPreviewUrls.value = {
    ...attachmentPreviewUrls.value,
    [attachment.id]: URL.createObjectURL(blob)
  }
}

async function loadAttachmentPreview(mistake, kind) {
  const isQuestion = kind === 'question'
  const contentType = isQuestion ? mistake.questionContentType : mistake.solutionContentType
  if (!mistake || !(contentType || '').startsWith('image/')) return
  const response = await fetch(isQuestion ? mistakeApi.questionFileUrl(mistake.id) : mistakeApi.solutionFileUrl(mistake.id), {
    headers: { Authorization: `Bearer ${getToken()}` }
  })
  if (!response.ok) return
  const blob = await response.blob()
  if (isQuestion) {
    revokeQuestionPreview(mistake.id)
    questionPreviewUrls.value = {
      ...questionPreviewUrls.value,
      [mistake.id]: URL.createObjectURL(blob)
    }
  } else {
    revokeSolutionPreview(mistake.id)
    solutionPreviewUrls.value = {
      ...solutionPreviewUrls.value,
      [mistake.id]: URL.createObjectURL(blob)
    }
  }
}

function revokeQuestionPreview(mistakeId) {
  const url = questionPreviewUrls.value[mistakeId]
  if (url) URL.revokeObjectURL(url)
  const next = { ...questionPreviewUrls.value }
  delete next[mistakeId]
  questionPreviewUrls.value = next
}

function revokeSolutionPreview(mistakeId) {
  const url = solutionPreviewUrls.value[mistakeId]
  if (url) URL.revokeObjectURL(url)
  const next = { ...solutionPreviewUrls.value }
  delete next[mistakeId]
  solutionPreviewUrls.value = next
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
    syncPersonalSettingsForm()
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
  cancelFileNameEdit()
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

function hasFolderChildren(folderId) {
  return folders.value.some((folder) => (folder.parentId ?? null) === folderId)
}

function toggleRootFolder() {
  rootFolderCollapsed.value = !rootFolderCollapsed.value
}

function toggleFolderCollapse(folder) {
  if (!folder || !hasFolderChildren(folder.id)) return
  const nextCollapsed = new Set(collapsedFolderIds.value)
  if (nextCollapsed.has(folder.id)) {
    nextCollapsed.delete(folder.id)
  } else {
    nextCollapsed.add(folder.id)
  }
  collapsedFolderIds.value = nextCollapsed
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
  chatForm.useKnowledgeBase = true
  openKnowledgeModule('chat')
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
  cancelFileNameEdit()
}

function selectFile(file) {
  cancelFileNameEdit()
  activeFile.value = { ...file }
  nextTick(() => setEditorContent(activeFile.value.extractedText, 0))
}

function openFileInEditor(file) {
  cancelFileNameEdit()
  activeFile.value = { ...file }
  openKnowledgeModule('editor')
  nextTick(() => setEditorContent(activeFile.value.extractedText, 0))
}

function setChatMode(mode) {
  chatForm.mode = mode
  if (mode === 'TEACHER') {
    chatForm.useKnowledgeBase = true
    chatForm.withCitations = true
  }
  activeSource.value = null
}

function handleKnowledgeModeChange() {
  if (!chatForm.useKnowledgeBase) {
    activeSource.value = null
  }
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
      originalName: activeFile.value.originalName,
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
          originalName: activeFile.value.originalName,
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
  if (chatForm.mode === 'TEACHER') {
    teacherState.requirement = question || teacherState.requirement
    chatForm.question = ''
    await requestTeacherQuestion(false)
    return
  }
  if (!question || chatInputDisabled.value || loading.value) return
  messages.value.push({ role: 'user', content: question })
  saveCurrentChatHistory()
  chatForm.question = ''
  activeSource.value = null
  chatLoading.value = true
  await run(async () => {
    const payload = { ...aiSettings, ...chatForm, question, folderId: activeFolder.value?.id ?? null }
    const assistantMessage = { role: 'assistant', content: '', sources: [] }
    messages.value.push(assistantMessage)
    try {
      const response = await chatApi.askStream(payload, (delta) => {
        assistantMessage.content += delta
      })
      assistantMessage.content = response?.answer || assistantMessage.content
      assistantMessage.sources = response?.sources || []
    } catch (streamError) {
      if (assistantMessage.content.trim()) {
        saveCurrentChatHistory()
        return
      }
      messages.value.pop()
      const response = await chatApi.ask(payload)
      messages.value.push({ role: 'assistant', content: response.answer, sources: response.sources })
    }
    saveCurrentChatHistory()
  })
  chatLoading.value = false
}

async function requestTeacherQuestion(resetAsked = false) {
  if (!activeFolder.value || chatLoading.value || loading.value) return
  if (resetAsked) {
    teacherState.askedChunkIds = []
  }
  activeSource.value = null
  chatLoading.value = true
  await run(async () => {
    const requirement = teacherState.requirement || chatForm.question.trim()
    const response = await chatApi.teacherQuestion({
      ...aiSettings,
      folderId: activeFolder.value.id,
      subjectFolderId: teacherState.subjectFolderId || null,
      requirement,
      excludeChunkIds: teacherState.askedChunkIds
    })
    teacherState.currentQuestion = response.question
    teacherState.referenceAnswer = response.referenceAnswer || ''
    teacherState.currentChunkId = response.chunkId
    teacherState.currentSource = response.source
    teacherState.feedbackType = null
    teacherState.addedToMistake = false
    if (response.chunkId && !teacherState.askedChunkIds.includes(response.chunkId)) {
      teacherState.askedChunkIds.push(response.chunkId)
    }
    messages.value.push({
      role: 'assistant',
      content: `${response.question} [1]`,
      sources: response.source ? [response.source] : [],
      teacherQuestion: true,
      referenceAnswer: response.referenceAnswer || '',
      chunkId: response.chunkId,
      feedbackType: null,
      addedToMistake: false
    })
    saveCurrentChatHistory()
  })
  chatLoading.value = false
}

async function nextTeacherQuestion() {
  await requestTeacherQuestion(false)
}

async function feedbackTeacherMessage(message, type) {
  if (!message?.chunkId || message.feedbackType === type) return
  await run(async () => {
    const updated = await chatApi.feedbackChunk(message.chunkId, { type })
    message.feedbackType = type
    updateSourceStats(message.chunkId, updated)
    saveCurrentChatHistory()
    await loadKnowledgeProfile()
  })
}

async function addTeacherMessageToMistake(message) {
  if (!message?.chunkId || message.addedToMistake) return
  await run(async () => {
    const saved = await mistakeApi.createFromTeacherQuestion({
      chunkId: message.chunkId,
      questionText: message.content.replace(/\s*\[1\]\s*$/, ''),
      solutionText: message.referenceAnswer || '',
      feedbackAlreadyForgot: message.feedbackType === 'FORGOT',
      subjectTagIds: []
    })
    mistakes.value = [saved, ...mistakes.value.filter((item) => item.id !== saved.id)]
    message.addedToMistake = true
    saveCurrentChatHistory()
    await loadKnowledgeProfile()
  })
}

async function feedbackActiveSource(type) {
  if (!activeSource.value?.chunkId) return
  await run(async () => {
    const updated = await chatApi.feedbackChunk(activeSource.value.chunkId, { type })
    updateSourceStats(activeSource.value.chunkId, updated)
    await loadKnowledgeProfile()
  })
}

function updateSourceStats(chunkId, updated) {
  const apply = (source) => {
    if (!source || source.chunkId !== chunkId) return
    source.citeCount = updated.citeCount
    source.correctHitCount = updated.correctHitCount
    source.wrongHitCount = updated.wrongHitCount
    source.masteryRate = updated.masteryRate
    source.lastAccessedAt = updated.lastAccessedAt
    source.lastPracticedAt = updated.lastPracticedAt
  }
  apply(activeSource.value)
  ;['QA', 'TEACHER'].forEach((mode) => {
    chatMessages[mode].forEach((message) => (message.sources || []).forEach(apply))
  })
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

function formatPercent(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '0%'
  return `${Math.round(number * 100)}%`
}

function subjectProgressDegrees(value) {
  const number = Number(value)
  const normalized = Number.isFinite(number) ? Math.max(0, Math.min(1, number)) : 0
  return `${Math.round(normalized * 360)}deg`
}

function subjectAccent(index) {
  return ['#0f766e', '#2563eb', '#b45309', '#7c3aed', '#be123c', '#15803d'][index % 6]
}

function confidenceRank(value) {
  return { NONE: 0, LOW: 1, MEDIUM: 2, HIGH: 3 }[value || 'NONE'] || 0
}

function confidenceLabel(value) {
  return {
    NONE: '未评估',
    LOW: '低',
    MEDIUM: '中',
    HIGH: '高'
  }[value] || '未评估'
}

function recentPracticeAccuracy(correctCount, wrongCount) {
  const correct = Number(correctCount || 0)
  const wrong = Number(wrongCount || 0)
  const total = correct + wrong
  return total > 0 ? correct / total : null
}

function recentAccuracyComparison(rows) {
  const dailyRows = Array.isArray(rows) ? rows.slice(-14) : []
  const previousWeek = dailyRows.slice(0, 7).reduce((acc, row) => addPracticeCounts(acc, row), { correct: 0, wrong: 0 })
  const currentWeek = dailyRows.slice(7).reduce((acc, row) => addPracticeCounts(acc, row), { correct: 0, wrong: 0 })
  const previousAccuracy = recentPracticeAccuracy(previousWeek.correct, previousWeek.wrong)
  const currentAccuracy = recentPracticeAccuracy(currentWeek.correct, currentWeek.wrong)
  if (previousAccuracy == null || currentAccuracy == null) return '相比上周暂无足够数据'
  const diff = Math.round((currentAccuracy - previousAccuracy) * 100)
  if (diff >= 0) return `相比上周提高 ${diff}%`
  return `相比上周下降 ${Math.abs(diff)}%`
}

function addPracticeCounts(acc, row) {
  acc.correct += Number(row?.correctCount || 0)
  acc.wrong += Number(row?.wrongCount || 0)
  return acc
}

function examPrepProgress(examDateValue) {
  if (!examDateValue) return { rate: 0 }
  const examDate = new Date(`${examDateValue}T00:00:00`)
  if (Number.isNaN(examDate.getTime())) return { rate: 0 }
  const startDate = new Date(examDate)
  startDate.setFullYear(startDate.getFullYear() - 1)
  const now = new Date()
  const total = examDate.getTime() - startDate.getTime()
  const elapsed = now.getTime() - startDate.getTime()
  if (total <= 0) return { rate: 0 }
  return { rate: Math.max(0, Math.min(1, elapsed / total)) }
}

function formatDateTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--'
  return `${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function emptyChartGraphic(text = '暂无数据') {
  return {
    type: 'text',
    left: 'center',
    top: 'middle',
    style: {
      text,
      fill: '#94a3b8',
      fontSize: 13
    }
  }
}

function ensureProfileChart(key, element) {
  if (!element) return null
  const current = profileCharts[key]
  if (current && current.getDom() === element) return current
  if (current) current.dispose()
  profileCharts[key] = echarts.init(element)
  return profileCharts[key]
}

function renderProfileCharts() {
  if (activePage.value !== 'profile') return
  renderSubjectProfileChart()
  renderDistributionProfileChart()
  renderTrendProfileChart()
  renderHeatmapProfileChart()
  renderPressureProfileChart()
}

function renderSubjectProfileChart() {
  const chart = ensureProfileChart('subjects', profileSubjectChartRef.value)
  if (!chart) return
  const names = profileSubjects.value.map((subject) => subject.subjectName)
  const mastery = profileSubjects.value.map((subject) => Math.round(Number(subject.masteryRate || 0) * 100))
  const coverage = profileSubjects.value.map((subject) => Math.round(Number(subject.coverageRate || 0) * 100))
  chart.setOption({
    color: ['#0f766e', '#d97706'],
    tooltip: { trigger: 'axis', valueFormatter: (value) => `${value}%` },
    legend: { top: 0, right: 0 },
    grid: { top: 42, left: 34, right: 16, bottom: 28 },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { color: '#64748b', interval: 0 }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { formatter: '{value}%', color: '#64748b' },
      splitLine: { lineStyle: { color: '#e2e8f0' } }
    },
    series: [
      { name: '掌握率', type: 'bar', barMaxWidth: 26, data: mastery, itemStyle: { borderRadius: [4, 4, 0, 0] } },
      { name: '覆盖率', type: 'bar', barMaxWidth: 26, data: coverage, itemStyle: { borderRadius: [4, 4, 0, 0] } }
    ],
    graphic: names.length ? [] : [emptyChartGraphic()]
  }, true)
}

function renderDistributionProfileChart() {
  const chart = ensureProfileChart('distribution', profileDistributionChartRef.value)
  if (!chart) return
  const distribution = profileDistribution.value || {}
  const data = [
    { name: '未评估', value: distribution.unassessed || 0 },
    { name: '薄弱', value: distribution.weak || 0 },
    { name: '一般', value: distribution.medium || 0 },
    { name: '良好', value: distribution.good || 0 },
    { name: '熟练', value: distribution.mastered || 0 }
  ]
  const total = data.reduce((sum, item) => sum + item.value, 0)
  chart.setOption({
    color: ['#94a3b8', '#dc2626', '#d97706', '#2563eb', '#059669'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, left: 'center' },
    series: [
      {
        name: '知识点',
        type: 'pie',
        radius: ['48%', '70%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        label: { formatter: '{b}\n{d}%' },
        data
      }
    ],
    graphic: total ? [] : [emptyChartGraphic()]
  }, true)
}

function renderTrendProfileChart() {
  const chart = ensureProfileChart('trend', profileTrendChartRef.value)
  if (!chart) return
  const rows = profileActivity.value?.daily || profileTrends.value
  const dates = rows.map((item) => String(item.date || '').slice(5))
  chart.setOption({
    color: ['#0f766e', '#2563eb', '#dc2626', '#7c3aed'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 0 },
    grid: { top: 42, left: 34, right: 18, bottom: 28 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLabel: { color: '#64748b' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#e2e8f0' } }
    },
    series: [
      { name: '练习', type: 'line', smooth: true, data: rows.map((item) => item.practiceCount || 0) },
      { name: '答对', type: 'line', smooth: true, data: rows.map((item) => item.correctCount || 0) },
      { name: '答错', type: 'line', smooth: true, data: rows.map((item) => item.wrongCount || 0) },
      { name: '引用', type: 'line', smooth: true, data: rows.map((item) => item.citationCount || 0) },
      { name: '错题复盘', type: 'line', smooth: true, data: rows.map((item) => item.mistakePracticeCount || 0) }
    ],
    graphic: dates.length ? [] : [emptyChartGraphic()]
  }, true)
}

function renderHeatmapProfileChart() {
  const chart = ensureProfileChart('heatmap', profileHeatmapChartRef.value)
  if (!chart) return
  const rows = profileActivity.value?.daily || []
  const today = toDateInputValue(new Date())
  const data = rows.map((item) => [
    item.date,
    (item.practiceCount || 0) * 2 + (item.mistakePracticeCount || 0) * 2 + (item.citationCount || 0)
  ])
  const values = data.map((item) => item[1])
  chart.setOption({
    tooltip: { formatter: (params) => `${params.value[0]}<br/>活跃度 ${params.value[1]}` },
    visualMap: {
      min: 0,
      max: Math.max(1, ...values),
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: { color: ['#e2e8f0', '#99f6e4', '#0f766e'] }
    },
    calendar: {
      top: 20,
      left: 24,
      right: 24,
      bottom: 56,
      cellSize: ['auto', 22],
      range: rows.length ? [rows[0].date, rows[rows.length - 1].date] : [today, today],
      itemStyle: { borderWidth: 2, borderColor: '#fff' },
      splitLine: { lineStyle: { color: '#e2e8f0' } },
      dayLabel: { color: '#64748b' },
      monthLabel: { color: '#64748b' },
      yearLabel: { show: false }
    },
    series: [{ type: 'heatmap', coordinateSystem: 'calendar', data }],
    graphic: data.length ? [] : [emptyChartGraphic()]
  }, true)
}

function renderPressureProfileChart() {
  const chart = ensureProfileChart('pressure', profilePressureChartRef.value)
  if (!chart) return
  const rows = profilePressureRisk.value?.pressureTrend || []
  const scopeName = selectedPressureSubject.value?.subjectName || '全科'
  chart.setOption({
    color: ['#d97706', '#2563eb', '#dc2626'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 0 },
    grid: { top: 42, left: 42, right: 18, bottom: 28 },
    xAxis: { type: 'category', boundaryGap: false, data: rows.map((item) => String(item.date || '').slice(5)), axisLabel: { color: '#64748b' } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#64748b' }, splitLine: { lineStyle: { color: '#e2e8f0' } } },
    series: [
      { name: `${scopeName}平均风险`, type: 'line', smooth: true, data: rows.map((item) => Math.round(item.averageRiskScore || 0)) },
      { name: `${scopeName}待复习`, type: 'line', smooth: true, data: rows.map((item) => item.dueChunkCount || 0) },
      { name: `${scopeName}高风险`, type: 'line', smooth: true, data: rows.map((item) => item.highRiskChunkCount || 0) }
    ],
    graphic: rows.length ? [] : [emptyChartGraphic()]
  }, true)
}

function resizeProfileCharts() {
  Object.values(profileCharts).forEach((chart) => chart?.resize())
}

function disposeProfileCharts() {
  Object.keys(profileCharts).forEach((key) => {
    profileCharts[key]?.dispose()
    profileCharts[key] = null
  })
}

function openTeacherForWeakChunk(chunk) {
  activePage.value = 'knowledge'
  knowledgeModule.value = 'chat'
  setChatMode('TEACHER')
  teacherState.requirement = displayFileName({ originalName: chunk.fileName })
  chatForm.question = teacherState.requirement
}

async function openWeakChunkInEditor(chunk) {
  if (!chunk?.fileId) return
  await run(async () => {
    const file = await fileApi.get(chunk.fileId)
    const folder = folders.value.find((item) => item.id === file.folderId)
    if (folder) {
      activeFolder.value = folder
      files.value = await fileApi.list(folder.id)
    }
    cancelFileNameEdit()
    activeFile.value = { ...file }
    activeSource.value = null
    openKnowledgeModule('editor')
    await nextTick()
    setEditorContent(activeFile.value.extractedText, Math.max(0, (chunk.pageNumber || 1) - 1))
  })
}

function originalFileExtension(file) {
  const name = file?.originalName || file?.fileName || ''
  const match = name.match(/(\.[^.\\/\s]+)$/u)
  return match?.[1] || ''
}

function fileNameWithPreservedExtension(file, displayName) {
  const trimmed = displayName.trim()
  if (!trimmed) return file?.originalName || ''
  if (/\.[^.\\/\s]+$/u.test(trimmed)) return trimmed
  return `${trimmed}${originalFileExtension(file)}`
}

function startFileNameEdit() {
  if (!activeFile.value) return
  editingFileName.value = true
  editingFileNameValue.value = displayFileName(activeFile.value)
  nextTick(() => {
    fileNameInput.value?.focus()
    fileNameInput.value?.select()
  })
}

function cancelFileNameEdit() {
  editingFileName.value = false
  editingFileNameValue.value = ''
}

async function saveFileName() {
  if (!activeFile.value || !editingFileName.value || savingFileName.value) return
  const originalName = fileNameWithPreservedExtension(activeFile.value, editingFileNameValue.value)
  if (!originalName || originalName === activeFile.value.originalName) {
    cancelFileNameEdit()
    return
  }
  syncEditorContent()
  savingFileName.value = true
  await run(async () => {
    const saved = await fileApi.update(activeFile.value.id, {
      originalName,
      extractedText: activeFile.value.extractedText,
      tag: activeFile.value.tag
    })
    files.value = files.value.map((file) => (file.id === saved.id ? saved : file))
    activeFile.value = { ...saved }
    cancelFileNameEdit()
    await nextTick()
    setEditorContent(activeFile.value.extractedText, activeFilePageIndex.value)
  })
  savingFileName.value = false
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
    cancelFileNameEdit()
    await nextTick()
    setEditorContent(activeFile.value.extractedText, Math.max(0, (source.pageNumber || 1) - 1))
    const excerptPageIndex = findExcerptPageIndex(source.excerpt)
    if (excerptPageIndex >= 0 && excerptPageIndex !== activeFilePageIndex.value) {
      activeFilePageIndex.value = excerptPageIndex
      setEditorPage(excerptPageIndex)
    }
    await nextTick()
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
  const selection = window.getSelection()
  const rangeInfo = rangeFromTextOffsets(editor, match.start, match.end)
  if (rangeInfo && selection) {
    selection.removeAllRanges()
    selection.addRange(rangeInfo.range)
    scrollEditorSelectionIntoView(editor, rangeInfo.targetElement)
  }
}

function scrollEditorSelectionIntoView(editor, targetElement) {
  const editorRect = editor.getBoundingClientRect()
  const targetRect = targetElement.getBoundingClientRect()
  const offset = targetRect.top - editorRect.top + editor.scrollTop
  editor.scrollTop = Math.max(0, offset - editor.clientHeight / 2 + targetRect.height / 2)
}

function findExcerptPageIndex(excerpt) {
  if (!excerpt) return -1
  for (let index = 0; index < activeFilePages.value.length; index += 1) {
    const pageText = editorPagePlainText(activeFilePages.value[index])
    if (findExcerptRange(pageText, excerpt)) {
      return index
    }
  }
  return -1
}

function editorPagePlainText(pageHtml = '') {
  const template = document.createElement('template')
  template.innerHTML = renderEditorHtml(pageHtml)
  return template.content.textContent || ''
}

function rangeFromTextOffsets(root, startOffset, endOffset) {
  const range = document.createRange()
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  let cursor = 0
  let startNode = null
  let startNodeOffset = 0
  let endNode = null
  let endNodeOffset = 0
  let node = walker.nextNode()
  while (node) {
    const textLength = node.textContent.length
    const nodeStart = cursor
    const nodeEnd = cursor + textLength
    if (!startNode && startOffset >= nodeStart && startOffset <= nodeEnd) {
      startNode = node
      startNodeOffset = Math.max(0, startOffset - nodeStart)
    }
    if (startNode && endOffset >= nodeStart && endOffset <= nodeEnd) {
      endNode = node
      endNodeOffset = Math.max(0, endOffset - nodeStart)
      break
    }
    cursor = nodeEnd
    node = walker.nextNode()
  }
  if (!startNode || !endNode) return null
  range.setStart(startNode, startNodeOffset)
  range.setEnd(endNode, endNodeOffset)
  return { range, targetElement: startNode.parentElement || root }
}

function findExcerptRange(text, excerpt) {
  const cleanedExcerpt = excerpt.replace(/^(?:\.{3}|…)+/, '').replace(/(?:\.{3}|…)+$/, '').trim()
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
  if (normalizedIndex < 0) return findLooseNormalizedRange(text, needleText)
  const start = indexMap[normalizedIndex]
  const end = indexMap[Math.min(normalizedIndex + needle.length - 1, indexMap.length - 1)] + 1
  return { start, end }
}

function findLooseNormalizedRange(text, needleText) {
  const normalizeLoose = (value) => {
    let normalizedText = ''
    const normalizedIndexMap = []
    for (let index = 0; index < value.length; index += 1) {
      const character = value[index]
      if (/[\s.,，。:：;；!?！？、()[\]（）【】"'“”‘’·\-—→<>《》]/u.test(character)) continue
      normalizedText += character.toLowerCase()
      normalizedIndexMap.push(index)
    }
    return { normalizedText, normalizedIndexMap }
  }
  const haystack = normalizeLoose(text)
  const needle = normalizeLoose(needleText).normalizedText
  if (!needle) return null
  const index = haystack.normalizedText.indexOf(needle)
  if (index < 0) return null
  return {
    start: haystack.normalizedIndexMap[index],
    end: haystack.normalizedIndexMap[Math.min(index + needle.length - 1, haystack.normalizedIndexMap.length - 1)] + 1
  }
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

function loadAiSettingPresets() {
  try {
    const raw = localStorage.getItem(aiPresetStorageKey)
    const presets = raw ? JSON.parse(raw) : []
    return Array.isArray(presets)
      ? presets.map(normalizeAiPreset).filter(Boolean).sort((a, b) => b.updatedAt - a.updatedAt)
      : []
  } catch {
    return []
  }
}

function normalizeAiPreset(preset) {
  if (!preset?.name || !preset?.settings) return null
  return {
    id: preset.id || crypto.randomUUID(),
    name: String(preset.name).trim().slice(0, 60),
    settings: normalizeAiSettings(preset.settings),
    updatedAt: Number(preset.updatedAt || Date.now())
  }
}

function persistAiSettingPresets() {
  localStorage.setItem(aiPresetStorageKey, JSON.stringify(aiSettingPresets.value))
}

function mergeAiSettingPresets(remotePresets = [], localPresets = aiSettingPresets.value) {
  const merged = new Map()
  ;[...localPresets, ...remotePresets].forEach((preset) => {
    const normalized = normalizeAiPreset(preset)
    if (!normalized) return
    const key = normalized.name.trim().toLowerCase()
    const current = merged.get(key)
    if (!current || normalized.updatedAt >= current.updatedAt) {
      merged.set(key, normalized)
    }
  })
  return [...merged.values()].sort((a, b) => b.updatedAt - a.updatedAt)
}

async function persistRemoteAiSettingPresets() {
  if (!session.value) return
  const saved = await aiSettingsApi.savePresets(aiSettingPresets.value)
  aiSettingPresets.value = mergeAiSettingPresets(saved, aiSettingPresets.value)
  persistAiSettingPresets()
}

function currentAiSettingsSnapshot() {
  return normalizeAiSettings({ ...aiSettings })
}

async function saveAiPreset() {
  const name = aiPresetName.value.trim()
  if (!name) return
  const now = Date.now()
  const existing = aiSettingPresets.value.find((preset) => preset.name === name)
  const saved = {
    id: existing?.id || crypto.randomUUID(),
    name,
    settings: currentAiSettingsSnapshot(),
    updatedAt: now
  }
  aiSettingPresets.value = [saved, ...aiSettingPresets.value.filter((preset) => preset.id !== saved.id)]
  selectedAiPresetId.value = saved.id
  aiPresetName.value = name
  persistAiSettingPresets()
  await run(persistRemoteAiSettingPresets)
}

function applyAiPreset() {
  const preset = aiSettingPresets.value.find((item) => item.id === selectedAiPresetId.value)
  if (!preset) return
  Object.assign(aiSettings, normalizeAiSettings(preset.settings))
  aiPresetName.value = preset.name
}

function syncSelectedAiPresetName() {
  const preset = aiSettingPresets.value.find((item) => item.id === selectedAiPresetId.value)
  aiPresetName.value = preset?.name || ''
}

async function deleteAiPreset() {
  if (!selectedAiPresetId.value) return
  aiSettingPresets.value = aiSettingPresets.value.filter((preset) => preset.id !== selectedAiPresetId.value)
  selectedAiPresetId.value = ''
  aiPresetName.value = ''
  persistAiSettingPresets()
  await run(persistRemoteAiSettingPresets)
}

async function loadRemoteAiSettings() {
  await run(async () => {
    const [remote, remotePresets] = await Promise.all([
      aiSettingsApi.get(),
      aiSettingsApi.getPresets()
    ])
    Object.assign(aiSettings, normalizeAiSettings({ ...aiSettings, ...remote }))
    aiSettingPresets.value = mergeAiSettingPresets(remotePresets)
    localStorage.setItem('smart_exam_ai_settings', JSON.stringify({ ...aiSettings }))
    persistAiSettingPresets()
    if (JSON.stringify(aiSettingPresets.value) !== JSON.stringify(remotePresets || [])) {
      await persistRemoteAiSettingPresets()
    }
  })
}

async function loadKnowledgeProfile() {
  if (!session.value || !studyProfile.value?.onboarded) return
  profileLoading.value = true
  await run(async () => {
    const [overview, subjects, weakChunks, trends, distribution, activity, fourteenDayActivity, risk] = await Promise.all([
      knowledgeProfileApi.overview(),
      knowledgeProfileApi.subjects(),
      knowledgeProfileApi.weakChunks(),
      knowledgeProfileApi.trends({ days: profileTrendDays.value }),
      knowledgeProfileApi.distribution(),
      knowledgeProfileApi.activity({ days: profileTrendDays.value }),
      knowledgeProfileApi.activity({ days: 14 }),
      knowledgeProfileApi.risk({ days: profileTrendDays.value })
    ])
    profileOverview.value = overview
    profileSubjects.value = subjects
    profileWeakChunks.value = weakChunks
    profileTrends.value = trends
    profileDistribution.value = distribution
    profileActivity.value = activity
    profileFourteenDayActivity.value = fourteenDayActivity
    profileRisk.value = risk
    const pressureSubjectStillExists = profilePressureSubjectId.value === 'all'
      || subjects.some((subject) => String(subject.subjectFolderId) === String(profilePressureSubjectId.value))
    if (!pressureSubjectStillExists) {
      profilePressureSubjectId.value = 'all'
    }
    profilePressureRisk.value = profilePressureSubjectId.value === 'all'
      ? risk
      : await knowledgeProfileApi.risk({ days: profileTrendDays.value, folderId: profilePressureSubjectId.value })
    await nextTick()
    renderProfileCharts()
  })
  profileLoading.value = false
}

function profileDiagnosisCacheKey(days = profileTrendDays.value) {
  const userKey = session.value?.userId || session.value?.username || 'anonymous'
  return `smart_exam_profile_diagnosis_v2_${userKey}_${toDateInputValue(new Date())}_${days}`
}

function profileDiagnosisAiCacheKey() {
  const userKey = session.value?.userId || session.value?.username || 'anonymous'
  return `smart_exam_profile_diagnosis_ai_v1_${userKey}_${toDateInputValue(new Date())}`
}

function hasTodayProfileDiagnosisAi() {
  return localStorage.getItem(profileDiagnosisAiCacheKey()) === 'true'
}

function markTodayProfileDiagnosisAi() {
  localStorage.setItem(profileDiagnosisAiCacheKey(), 'true')
}

function readCachedProfileDiagnosis(days = profileTrendDays.value) {
  const raw = localStorage.getItem(profileDiagnosisCacheKey(days))
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    localStorage.removeItem(profileDiagnosisCacheKey(days))
    return null
  }
}

function cacheProfileDiagnosis(diagnosis, days = profileTrendDays.value) {
  localStorage.setItem(profileDiagnosisCacheKey(days), JSON.stringify(diagnosis))
}

function ensureDailyProfileDiagnosis() {
  if (!session.value || !studyProfile.value?.onboarded) return
  const cached = readCachedProfileDiagnosis()
  if (cached) {
    profileDiagnosis.value = cached
    return
  }
  loadProfileDiagnosis({ force: true })
}

async function loadProfileDiagnosis({ force = false, refreshAi = false } = {}) {
  if (!session.value || !studyProfile.value?.onboarded || profileDiagnosisLoading.value) return
  const days = profileTrendDays.value
  const cached = force ? null : readCachedProfileDiagnosis(days)
  if (cached) {
    profileDiagnosis.value = cached
    return
  }
  const requestId = ++profileDiagnosisRequestId
  profileDiagnosisLoading.value = true
  try {
    const useAi = refreshAi || !hasTodayProfileDiagnosisAi()
    const diagnosis = await knowledgeProfileApi.diagnosis({ days, ai: useAi })
    if (requestId !== profileDiagnosisRequestId) return
    profileDiagnosis.value = diagnosis
    cacheProfileDiagnosis(diagnosis, days)
    if (useAi) {
      markTodayProfileDiagnosisAi()
    }
  } catch (err) {
    if (requestId === profileDiagnosisRequestId) {
      error.value = err.message
    }
  } finally {
    if (requestId === profileDiagnosisRequestId) {
      profileDiagnosisLoading.value = false
    }
  }
}

async function refreshProfileDiagnosis() {
  await loadProfileDiagnosis({ force: true, refreshAi: true })
}

async function refreshProfileRecommendations() {
  await loadKnowledgeProfile()
  await loadProfileDiagnosis({ force: true, refreshAi: true })
}

async function changeProfilePressureSubject() {
  const folderId = profilePressureSubjectId.value === 'all' ? null : profilePressureSubjectId.value
  profilePressureRisk.value = await knowledgeProfileApi.risk({ days: profileTrendDays.value, folderId })
  await nextTick()
  renderPressureProfileChart()
}

async function changeProfileTrendDays(days) {
  if (profileTrendDays.value === days) return
  profileTrendDays.value = days
  profileDiagnosisRequestId += 1
  profileDiagnosisLoading.value = false
  profileDiagnosis.value = readCachedProfileDiagnosis(days)
  await loadKnowledgeProfile()
  ensureDailyProfileDiagnosis()
}

function openTeacherForSuggestion(suggestion) {
  activePage.value = 'knowledge'
  knowledgeModule.value = 'chat'
  setChatMode('TEACHER')
  teacherState.requirement = suggestion.title || suggestion.fileName || ''
  chatForm.question = teacherState.requirement
}

async function addSuggestionToPlan(suggestion) {
  const payload = suggestion?.planPayload
  if (!payload) return
  await run(async () => {
    await studyPlanApi.createFromProfileSuggestion({
      title: payload.title,
      subject: payload.subject,
      description: payload.description,
      itemType: payload.itemType || 'REVIEW',
      startDate: payload.startDate,
      startTime: payload.startTime,
      endTime: payload.endTime,
      location: '',
      priority: payload.priority || 'MEDIUM',
      status: payload.status || 'TODO'
    })
    await loadStudyPlan()
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
  const chatModel = settings.chatModel === 'deepseek-chat'
    ? 'deepseek-v4-flash'
    : settings.chatModel || settings.model || defaultAiSettings.chatModel
  return {
    ...defaultAiSettings,
    ...settings,
    chatModel,
    chatEndpoint: settings.chatEndpoint || settings.endpoint || defaultAiSettings.chatEndpoint,
    chatApiKey: settings.chatApiKey || settings.apiKey || defaultAiSettings.chatApiKey,
    embeddingDimensions: Number(settings.embeddingDimensions || defaultAiSettings.embeddingDimensions)
  }
}

function logout() {
  clearSession()
  session.value = null
  activePage.value = 'home'
  studyProfile.value = null
  profileOverview.value = null
  profileSubjects.value = []
  profileWeakChunks.value = []
  profileTrends.value = []
  profileDistribution.value = null
  profileActivity.value = null
  profileFourteenDayActivity.value = null
  profileRisk.value = null
  profilePressureRisk.value = null
  profileDiagnosis.value = null
  profileDiagnosisLoading.value = false
  profileDiagnosisRequestId += 1
  profilePressureSubjectId.value = 'all'
  disposeProfileCharts()
  folders.value = []
  files.value = []
  activeFolder.value = null
  activeFile.value = null
  activeSource.value = null
  studyPlanItems.value = []
  homePlanItems.value = []
  planDraftItems.value = []
  planSessionByWeek.value = {}
  planModule.value = ''
  knowledgeModule.value = ''
  planWeekStart.value = startOfWeekIso(new Date())
  resetPlanForm(toDateInputValue(new Date()))
  planAiMessages.value = initialPlanAiMessages()
  planAiInput.value = ''
  planLastOperations.value = []
  planPendingOperations.value = []
  planUndoStack.value = []
  savedExamDate.value = ''
  examDate.value = ''
  folderChatHistories.value = []
  activeConversationIds.QA = null
  activeConversationIds.TEACHER = null
  historyPanelOpen.value = false
  mistakes.value = []
  mistakeStatuses.value = []
  mistakeSubjectTags.value = []
  activeMistake.value = null
  browseSubjectFilterIds.value = []
  browseStatusFilterKeys.value = []
  resetMistakeForm()
  closePractice()
  mistakeModule.value = ''
  subjectTagCreatorOpen.value = false
  Object.values(questionPreviewUrls.value).forEach((url) => URL.revokeObjectURL(url))
  Object.values(solutionPreviewUrls.value).forEach((url) => URL.revokeObjectURL(url))
  Object.values(attachmentPreviewUrls.value).forEach((url) => URL.revokeObjectURL(url))
  questionPreviewUrls.value = {}
  solutionPreviewUrls.value = {}
  attachmentPreviewUrls.value = {}
  enlargedAttachment.value = null
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

  return { Bold, BookOpenCheck, Bot, CalendarDays, CalendarPlus, ChevronRight, ClipboardCopy, Clock, Eye, EyeOff, FileText, Folder, FolderOpen, FolderPlus, Image, KeyRound, Library, LoaderCircle, LogIn, LogOut, Italic, List, ListOrdered, MessageSquare, MoveRight, NotebookPen, Palette, Pencil, RefreshCw, RotateCcw, Save, ScanText, Search, Send, Settings, Sparkles, Table2, Tag, Timer, Trash2, Underline, Undo2, Upload, UserCog, aiSettingsApi, authApi, chatApi, clearSession, fileApi, folderApi, getSession, getToken, knowledgeProfileApi, mistakeApi, setSession, studyPlanApi, studyProfileApi, session, authMode, authForm, folderForm, editFolderForm, folders, files, activeFolder, activeFile, activePage, studyProfile, onboardingForm, personalSettingsForm, uploadTag, fileInput, editorElement, activeFilePages, activeFilePageIndex, editorTextColor, loading, homeClockNow, homeClockTimerId, chatLoading, noteLoading, error, chatMessages, activeConversationIds, folderChatHistories, historyPanelOpen, activeSource, activeChunkDetail, movingFile, editingFolder, editingFileName, editingFileNameValue, savingFileName, fileNameInput, moveFileTargetId, rootFolderCollapsed, collapsedFolderIds, studyPlanItems, homePlanItems, planDraftItems, planSessionByWeek, planModule, knowledgeModule, planWeekStart, savedExamDate, examDate, examDateInput, profileOverview, profileSubjects, profileWeakChunks, profileTrends, profileDistribution, profileActivity, profileFourteenDayActivity, profileRisk, profilePressureRisk, profileDiagnosis, profileTrendDays, profilePressureSubjectId, profileLoading, profileDiagnosisLoading, profileDiagnosisRequestId, profileSubjectChartRef, profileDistributionChartRef, profileTrendChartRef, profileHeatmapChartRef, profilePressureChartRef, profileCharts, editingPlanItem, planAiMessages, planAiInput, planAiLoading, planGenerateLoading, planSaveLoading, planUndoLoading, planLastOperations, planPendingOperations, planUndoStack, planForm, mistakes, mistakeStatuses, mistakeSubjectTags, activeMistake, editingMistake, mistakeQuestionAttachmentFile, mistakeSolutionFile, questionImageItems, solutionImageItems, enlargedAttachment, recognitionFile, recognitionText, recognitionLoading, newMistakeStatusName, newMistakeSubjectTagName, subjectTagCreatorOpen, mistakeForm, mistakeChunkQuery, mistakeChunkCandidates, mistakeChunkSearchLoading, mistakeChunkSubjectFolderId, mistakeChunkFileId, mistakeChunkFiles, practiceForm, practiceQuestions, practiceIndex, practiceStarted, practiceFinished, practiceResults, practiceRemainingSeconds, practiceTimerId, showBrowseSolution, browseSolutionVisibility, editingStatusMistakeId, browseSubjectFilterIds, browseStatusFilterKeys, solutionPreviewUrls, questionPreviewUrls, attachmentPreviewUrls, mistakeModule, defaultAiSettings, aiPresetStorageKey, chatForm, teacherState, aiSettings, aiSettingPresets, selectedAiPresetId, aiPresetName, settingsSaved, maxFolderDepth, chatHistoryRetentionMs, navItems, pageMeta, knowledgeModuleMeta, currentPageMeta, pageTitle, pageDescription, todayIso, examCountdownDays, canSubmitOnboarding, canSavePersonalSettings, subjectFolders, selectedPressureSubject, profileMetricCards, profileDiagnosisSummary, profileDiagnosisMessage, profileDiagnosisInsufficient, profileDiagnosisItems, profileSuggestions, mistakeSubjectOptions, messages, currentChatHasMessages, chatInputDisabled, canSubmitChat, chatPlaceholder, pendingChatText, emptyChatTitle, emptyChatDescription, visibleFolders, folderTree, folderPath, fileMoveTargetOptions, canSubmitFileMove, currentFolderName, canCreateFolder, folderNamePlaceholder, createFolderHint, emptyFolderTitle, emptyFolderDescription, mistakeStatusOptions, unmasteredStatusOptions, filteredMistakes, practiceCurrentQuestion, practiceClock, planWeekDays, planWeekEnd, planWeekLabel, studyPlanItemsByDate, todaysHomePlanItems, currentHomeTask, currentHomeTaskState, planDraftItemsByDate, planDraftDirty, planDraftStats, planStats, groupPlanItemsByDate, toDateInputValue, parseDateInput, addDays, startOfWeekIso, formatShortDate, isToday, normalizeTimeValue, planTypeLabel, planPriorityLabel, planStatusLabel, timeToMinutes, examDateStorageKey, loadExamDate, saveExamDate, clearExamDate, openExamDatePicker, folderChatHistoryKey, legacyChatHistoryKey, loadChatHistory, loadFolderHistories, loadLegacyHistories, saveCurrentChatHistory, startNewConversation, openConversation, deleteChatHistory, normalizeHistoryItem, dedupeHistories, historyTitle, chatModeLabel, formatHistoryTime, setActivePage, openKnowledgeModule, submitAuth, initializeSessionData, loadStudyProfile, syncPersonalSettingsForm, addPersonalSubject, removePersonalSubject, savePersonalSettings, syncOnboardingSubjects, submitOnboarding, loadStudyPlan, syncHomePlanItemsFromCurrentWeek, clonePlanItems, clonePlanOperations, currentPlanSessionKey, persistPlanSession, restorePlanSession, syncPlanDraftFromReal, resetPlanDraft, pushPlanUndoSnapshot, undoStudyPlanChange, restoreStudyPlanSnapshot, shiftPlanWeek, goCurrentPlanWeek, openNewStudyPlan, openPlanModule, openPlannerAiPage, editStudyPlan, resetPlanForm, studyPlanPayloadFromForm, studyPlanPayloadFromItem, saveStudyPlanItem, deleteStudyPlanItem, toggleStudyPlanDone, compactPlanMessages, displayPlanAiMessage, cleanPlanAssistantContent, planAiBasePayload, initialPlanAiMessages, clearPlanAiChat, sendPlanAiMessage, generateStudyPlanFromAi, updateStudyPlanDraftFromAi, operationForApply, savePlanAiDraft, loadMistakeData, selectedMistakeStatus, resetMistakeForm, editMistake, openMistakeModule, backToMistakeMenu, saveMistake, setUnmasteredStatus, searchMistakeChunks, handleMistakeChunkSubjectChange, loadMistakeChunkFiles, syncMistakeSubjectTagFromFolder, subjectFolderForMistake, isMistakeChunkLinked, addMistakeLinkedChunk, removeMistakeLinkedChunk, openChunkDetail, onImageSelect, removeImageItem, clearImageItems, enlargeAttachment, enlargeSavedAttachment, displayNameWithoutExtension, createMistakeStatus, createSubjectTag, deleteSubjectTag, toggleIdInArray, removeIdFromArray, recognizeMistakeFile, copyRecognitionText, deleteMistakeStatus, savedImageItems, newImageItems, retainedAttachmentIds, setMistakeStatus, statusKeyForMistake, openMistakeStatusEditor, changeMistakeStatusFromSelect, isBrowseSolutionVisible, toggleBrowseSolution, setAllBrowseSolutions, deleteMistake, startPractice, finishPractice, stopPracticeTimer, closePractice, nextPracticeQuestion, practiceResultFor, recordPracticeResult, refreshAttachmentPreviews, loadSavedAttachmentPreview, loadAttachmentPreview, revokeQuestionPreview, revokeSolutionPreview, loadFolders, createFolder, selectRoot, openEditFolder, cancelEditFolder, saveFolderEdit, deleteFolder, collectFolderIds, hasFolderChildren, toggleRootFolder, toggleFolderCollapse, folderOptionLabel, openMoveFile, cancelMoveFile, moveFile, useCurrentFolderAsKnowledgeBase, selectFolder, selectFile, openFileInEditor, setChatMode, handleKnowledgeModeChange, setEditorContent, clearEditorContent, syncEditorContent, setEditorPage, renderCurrentEditorPage, goFilePage, clampPageIndex, paginateEditorContent, formatEditor, setEditorTextColor, insertTable, isHtmlContent, plainTextToEditorHtml, renderEditorHtml, renderMathInHtml, renderRichText, splitMathSegments, findNextMathStart, renderMarkdownInline, renderMathExpression, escapeHtml, uploadFile, saveFileText, toggleKnowledge, deleteFile, ask, requestTeacherQuestion, nextTeacherQuestion, feedbackTeacherMessage, addTeacherMessageToMistake, feedbackActiveSource, updateSourceStats, createNoteFromConversation, showSource, messageParts, sourceLabel, displayFileName, formatPercent, subjectProgressDegrees, subjectAccent, confidenceRank, confidenceLabel, recentPracticeAccuracy, recentAccuracyComparison, addPracticeCounts, examPrepProgress, formatDateTime, emptyChartGraphic, ensureProfileChart, renderProfileCharts, renderSubjectProfileChart, renderDistributionProfileChart, renderTrendProfileChart, renderHeatmapProfileChart, renderPressureProfileChart, resizeProfileCharts, disposeProfileCharts, openTeacherForWeakChunk, openWeakChunkInEditor, originalFileExtension, fileNameWithPreservedExtension, startFileNameEdit, cancelFileNameEdit, saveFileName, openSourceFile, focusExcerpt, scrollEditorSelectionIntoView, findExcerptPageIndex, editorPagePlainText, rangeFromTextOffsets, findExcerptRange, findNormalizedRange, findLooseNormalizedRange, clearChatMessages, loadAiSettings, loadAiSettingPresets, normalizeAiPreset, persistAiSettingPresets, mergeAiSettingPresets, persistRemoteAiSettingPresets, currentAiSettingsSnapshot, saveAiPreset, applyAiPreset, syncSelectedAiPresetName, deleteAiPreset, loadRemoteAiSettings, loadKnowledgeProfile, profileDiagnosisCacheKey, profileDiagnosisAiCacheKey, hasTodayProfileDiagnosisAi, markTodayProfileDiagnosisAi, readCachedProfileDiagnosis, cacheProfileDiagnosis, ensureDailyProfileDiagnosis, loadProfileDiagnosis, refreshProfileDiagnosis, refreshProfileRecommendations, changeProfilePressureSubject, changeProfileTrendDays, openTeacherForSuggestion, addSuggestionToPlan, saveAiSettings, resetAiSettings, normalizeAiSettings, logout, run, tagLabel }
}
