const API_BASE = import.meta.env.VITE_API_BASE || '/api'

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

export const authApi = {
  register: (payload) => api('/auth/register', { method: 'POST', body: JSON.stringify(payload) }),
  login: (payload) => api('/auth/login', { method: 'POST', body: JSON.stringify(payload) })
}

export const folderApi = {
  list: () => api('/folders'),
  create: (payload) => api('/folders', { method: 'POST', body: JSON.stringify(payload) })
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
  delete: (fileId) => api(`/files/${fileId}`, { method: 'DELETE' })
}

export const chatApi = {
  ask: (payload) => api('/chat', { method: 'POST', body: JSON.stringify(payload) })
}
