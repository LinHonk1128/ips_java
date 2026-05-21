function normalizeApiBase(value) {
  const base = (value || '/api').trim()
  if (/^https?:\/\//i.test(base)) {
    return base.replace(/\/+$/, '')
  }
  return `/${base.replace(/^\/+|\/+$/g, '')}`
}

const API_BASE = normalizeApiBase(import.meta.env.VITE_API_BASE)

export function getToken() {
  return localStorage.getItem('smart_exam_token')
}

export function setSession(session) {
  localStorage.setItem('smart_exam_token', session.token)
  localStorage.setItem('smart_exam_user', JSON.stringify(session))
}

export function getSession() {
  const raw = localStorage.getItem('smart_exam_user')
  return raw ? JSON.parse(raw) : null
}

export function clearSession() {
  localStorage.removeItem('smart_exam_token')
  localStorage.removeItem('smart_exam_user')
}

export async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  const text = await response.text()
  const data = text ? JSON.parse(text) : null
  if (!response.ok) {
    throw new Error(data?.message || '请求失败')
  }
  return data
}

function dispatchSseEvent(rawEvent, handlers) {
  const lines = rawEvent.split(/\r?\n/)
  let eventName = 'message'
  const dataLines = []
  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }
  const data = dataLines.join('\n')
  if (!data) return null
  if (eventName === 'delta') {
    handlers.onDelta?.(data)
    return null
  }
  if (eventName === 'done') {
    return JSON.parse(data)
  }
  return null
}

export async function streamApi(path, payload, handlers = {}) {
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream'
  }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload)
  })
  if (!response.ok || !response.body) {
    const text = await response.text()
    const data = text ? JSON.parse(text) : null
    throw new Error(data?.message || '请求失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let donePayload = null
  let reading = true
  while (reading) {
    const { value, done } = await reader.read()
    reading = !done
    buffer += decoder.decode(value || new Uint8Array(), { stream: reading })
    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() || ''
    for (const event of events) {
      donePayload = dispatchSseEvent(event, handlers) || donePayload
    }
  }
  if (buffer.trim()) {
    donePayload = dispatchSseEvent(buffer, handlers) || donePayload
  }
  return donePayload
}

export const authApi = {
  register: (payload) => api('/auth/register', { method: 'POST', body: JSON.stringify(payload) }),
  login: (payload) => api('/auth/login', { method: 'POST', body: JSON.stringify(payload) })
}

export const folderApi = {
  list: () => api('/folders'),
  create: (payload) => api('/folders', { method: 'POST', body: JSON.stringify(payload) }),
  update: (folderId, payload) => api(`/folders/${folderId}`, { method: 'PATCH', body: JSON.stringify(payload) }),
  delete: (folderId) => api(`/folders/${folderId}`, { method: 'DELETE' })
}

export const fileApi = {
  list: (folderId) => api(`/folders/${folderId}/files`),
  get: (fileId) => api(`/files/${fileId}`),
  upload: (folderId, tag, file) => {
    const form = new FormData()
    form.append('tag', tag)
    form.append('file', file)
    return api(`/folders/${folderId}/files`, { method: 'POST', body: form })
  },
  update: (fileId, payload) => api(`/files/${fileId}`, { method: 'PUT', body: JSON.stringify(payload) }),
  updateKnowledgeStatus: (fileId, payload) => api(`/files/${fileId}/knowledge`, { method: 'PATCH', body: JSON.stringify(payload) }),
  move: (fileId, payload) => api(`/files/${fileId}/move`, { method: 'PATCH', body: JSON.stringify(payload) }),
  delete: (fileId) => api(`/files/${fileId}`, { method: 'DELETE' })
}

export const chatApi = {
  ask: (payload) => api('/chat', { method: 'POST', body: JSON.stringify(payload) }),
  askStream: (payload, onDelta) => streamApi('/chat/stream', payload, { onDelta }),
  teacherQuestion: (payload) => api('/chat/teacher/question', { method: 'POST', body: JSON.stringify(payload) }),
  feedbackChunk: (chunkId, payload) => api(`/chat/chunks/${chunkId}/feedback`, { method: 'POST', body: JSON.stringify(payload) }),
  createNote: (payload) => api('/chat/note', { method: 'POST', body: JSON.stringify(payload) })
}

export const studyProfileApi = {
  get: () => api('/study-profile'),
  onboard: (payload) => api('/study-profile/onboarding', { method: 'POST', body: JSON.stringify(payload) }),
  update: (payload) => api('/study-profile', { method: 'PUT', body: JSON.stringify(payload) })
}

export const knowledgeProfileApi = {
  overview: () => api('/knowledge-profile/overview'),
  subjects: () => api('/knowledge-profile/subjects'),
  files: ({ folderId = null } = {}) => {
    const params = new URLSearchParams()
    if (folderId) params.set('folderId', String(folderId))
    const query = params.toString()
    return api(`/knowledge-profile/files${query ? `?${query}` : ''}`)
  },
  weakChunks: () => api('/knowledge-profile/weak-chunks'),
  trends: ({ days = 14 } = {}) => api(`/knowledge-profile/trends?days=${encodeURIComponent(String(days))}`),
  distribution: () => api('/knowledge-profile/distribution'),
  activity: ({ days = 30 } = {}) => api(`/knowledge-profile/activity?days=${encodeURIComponent(String(days))}`),
  risk: ({ days = 30, folderId = null } = {}) => {
    const params = new URLSearchParams({ days: String(days) })
    if (folderId) params.set('folderId', String(folderId))
    return api(`/knowledge-profile/risk?${params.toString()}`)
  },
  diagnosis: ({ days = 30, ai = true } = {}) => api(`/knowledge-profile/diagnosis?days=${encodeURIComponent(String(days))}&ai=${encodeURIComponent(String(ai))}`),
  searchChunks: ({ query = '', folderId = null, fileId = null, limit = 20 } = {}) => {
    const params = new URLSearchParams({ limit: String(limit) })
    if (query) params.set('query', query)
    if (folderId) params.set('folderId', String(folderId))
    if (fileId) params.set('fileId', String(fileId))
    return api(`/knowledge-profile/chunks?${params.toString()}`)
  }
}

export const aiSettingsApi = {
  get: () => api('/ai-settings'),
  save: (payload) => api('/ai-settings', { method: 'PUT', body: JSON.stringify(payload) }),
  getPresets: () => api('/ai-settings/presets'),
  savePresets: (presets) => api('/ai-settings/presets', { method: 'PUT', body: JSON.stringify({ presets }) })
}

export const studyPlanApi = {
  list: (from, to) => {
    const params = new URLSearchParams()
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    const query = params.toString()
    return api(`/study-plan${query ? `?${query}` : ''}`)
  },
  create: (payload) => api('/study-plan', { method: 'POST', body: JSON.stringify(payload) }),
  createFromProfileSuggestion: (payload) => api('/study-plan/profile-suggestion', { method: 'POST', body: JSON.stringify(payload) }),
  update: (itemId, payload) => api(`/study-plan/${itemId}`, { method: 'PUT', body: JSON.stringify(payload) }),
  delete: (itemId) => api(`/study-plan/${itemId}`, { method: 'DELETE' }),
  chat: (payload) => api('/study-plan/ai/chat', { method: 'POST', body: JSON.stringify(payload) }),
  generate: (payload) => api('/study-plan/ai/generate', { method: 'POST', body: JSON.stringify(payload) }),
  apply: (payload) => api('/study-plan/ai/apply', { method: 'POST', body: JSON.stringify(payload) })
}

function appendOptionalFormValue(form, key, value) {
  if (value !== undefined && value !== null && value !== '') {
    form.append(key, value)
  }
}

export const mistakeApi = {
  listStatuses: () => api('/mistake-statuses'),
  createStatus: (payload) => api('/mistake-statuses', { method: 'POST', body: JSON.stringify(payload) }),
  updateStatusLabel: (statusId, payload) => api(`/mistake-statuses/${statusId}`, { method: 'PUT', body: JSON.stringify(payload) }),
  deleteStatus: (statusId) => api(`/mistake-statuses/${statusId}`, { method: 'DELETE' }),
  listSubjectTags: () => api('/mistake-subject-tags'),
  createSubjectTag: (payload) => api('/mistake-subject-tags', { method: 'POST', body: JSON.stringify(payload) }),
  deleteSubjectTag: (tagId) => api(`/mistake-subject-tags/${tagId}`, { method: 'DELETE' }),
  list: (mastered = null) => api(`/mistakes${mastered === null ? '' : `?mastered=${mastered}`}`),
  practice: (count, subjectTagIds = []) => {
    const params = new URLSearchParams({ count: String(count) })
    subjectTagIds.forEach((id) => params.append('subjectTagIds', String(id)))
    return api(`/mistakes/practice?${params.toString()}`)
  },
  create: (payload) => {
    const form = new FormData()
    appendOptionalFormValue(form, 'questionText', payload.questionText)
    appendOptionalFormValue(form, 'questionAttachmentFile', payload.questionAttachmentFile)
    ;(payload.questionImageFiles || []).forEach((file) => appendOptionalFormValue(form, 'questionImageFiles', file))
    ;(payload.questionImageNames || []).forEach((name) => appendOptionalFormValue(form, 'questionImageNames', name))
    ;(payload.retainedQuestionAttachmentIds || []).forEach((id) => appendOptionalFormValue(form, 'retainedQuestionAttachmentIds', id))
    appendOptionalFormValue(form, 'solutionText', payload.solutionText)
    appendOptionalFormValue(form, 'solutionFile', payload.solutionFile)
    ;(payload.solutionImageFiles || []).forEach((file) => appendOptionalFormValue(form, 'solutionImageFiles', file))
    ;(payload.solutionImageNames || []).forEach((name) => appendOptionalFormValue(form, 'solutionImageNames', name))
    ;(payload.retainedSolutionAttachmentIds || []).forEach((id) => appendOptionalFormValue(form, 'retainedSolutionAttachmentIds', id))
    appendOptionalFormValue(form, 'mastered', payload.mastered ? 'true' : 'false')
    appendOptionalFormValue(form, 'statusId', payload.statusId)
    ;(payload.subjectTagIds || []).forEach((id) => appendOptionalFormValue(form, 'subjectTagIds', id))
    ;(payload.chunkIds || []).forEach((id) => appendOptionalFormValue(form, 'chunkIds', id))
    return api('/mistakes', { method: 'POST', body: form })
  },
  update: (mistakeId, payload) => {
    const form = new FormData()
    appendOptionalFormValue(form, 'questionText', payload.questionText)
    appendOptionalFormValue(form, 'questionAttachmentFile', payload.questionAttachmentFile)
    ;(payload.questionImageFiles || []).forEach((file) => appendOptionalFormValue(form, 'questionImageFiles', file))
    ;(payload.questionImageNames || []).forEach((name) => appendOptionalFormValue(form, 'questionImageNames', name))
    ;(payload.retainedQuestionAttachmentIds || []).forEach((id) => appendOptionalFormValue(form, 'retainedQuestionAttachmentIds', id))
    appendOptionalFormValue(form, 'solutionText', payload.solutionText)
    appendOptionalFormValue(form, 'solutionFile', payload.solutionFile)
    ;(payload.solutionImageFiles || []).forEach((file) => appendOptionalFormValue(form, 'solutionImageFiles', file))
    ;(payload.solutionImageNames || []).forEach((name) => appendOptionalFormValue(form, 'solutionImageNames', name))
    ;(payload.retainedSolutionAttachmentIds || []).forEach((id) => appendOptionalFormValue(form, 'retainedSolutionAttachmentIds', id))
    appendOptionalFormValue(form, 'mastered', payload.mastered ? 'true' : 'false')
    appendOptionalFormValue(form, 'statusId', payload.statusId)
    ;(payload.subjectTagIds || []).forEach((id) => appendOptionalFormValue(form, 'subjectTagIds', id))
    ;(payload.chunkIds || []).forEach((id) => appendOptionalFormValue(form, 'chunkIds', id))
    return api(`/mistakes/${mistakeId}`, { method: 'PUT', body: form })
  },
  recognize: (file) => {
    const form = new FormData()
    form.append('file', file)
    return api('/mistakes/recognize', { method: 'POST', body: form })
  },
  updateMistakeStatus: (mistakeId, payload) => api(`/mistakes/${mistakeId}/status`, { method: 'PATCH', body: JSON.stringify(payload) }),
  createFromTeacherQuestion: (payload) => api('/mistakes/from-teacher-question', { method: 'POST', body: JSON.stringify(payload) }),
  recordPracticeResult: (mistakeId, payload) => api(`/mistakes/${mistakeId}/practice-result`, { method: 'POST', body: JSON.stringify(payload) }),
  delete: (mistakeId) => api(`/mistakes/${mistakeId}`, { method: 'DELETE' }),
  attachmentUrl: (attachmentId) => `${API_BASE}/mistake-attachments/${attachmentId}`,
  questionFileUrl: (mistakeId) => `${API_BASE}/mistakes/${mistakeId}/question-file`,
  solutionFileUrl: (mistakeId) => `${API_BASE}/mistakes/${mistakeId}/solution-file`
}
