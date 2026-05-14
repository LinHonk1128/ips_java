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
            @click="setActivePage(item.key)"
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
            <ChevronRight
              class="folder-toggle"
              :class="{ open: !rootFolderCollapsed }"
              :size="15"
              role="button"
              tabindex="0"
              @click.stop="toggleRootFolder"
              @keydown.enter.stop.prevent="toggleRootFolder"
              @keydown.space.stop.prevent="toggleRootFolder"
            />
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
            <ChevronRight
              v-if="hasFolderChildren(folder.id)"
              class="folder-toggle"
              :class="{ open: !collapsedFolderIds.has(folder.id) }"
              :size="15"
              role="button"
              tabindex="0"
              @click.stop="toggleFolderCollapse(folder)"
              @keydown.enter.stop.prevent="toggleFolderCollapse(folder)"
              @keydown.space.stop.prevent="toggleFolderCollapse(folder)"
            />
            <span v-else class="folder-toggle spacer"></span>
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

        <section v-if="activePage === 'knowledge' && !knowledgeModule" class="page-panel knowledge-page">
          <div class="mistake-module-landing">
            <div class="mistake-module-menu knowledge-module-menu">
              <button class="mistake-module-card" type="button" @click="openKnowledgeModule('library')">
                <Library :size="34" />
                <strong>我的资料</strong>
                <span>管理科目文件夹、资料文件和知识库收录状态。</span>
              </button>
              <button class="mistake-module-card" type="button" @click="openKnowledgeModule('chat')">
                <MessageSquare :size="34" />
                <strong>知识问答</strong>
                <span>选择文件夹作为知识范围，进行资料溯源答疑或教师式追问。</span>
              </button>
              <button class="mistake-module-card" type="button" @click="openKnowledgeModule('editor')">
                <ScanText :size="34" />
                <strong>上传编辑</strong>
                <span>上传 PDF、图片或文档，校正文本后保存为可检索知识。</span>
              </button>
            </div>
          </div>
        </section>

        <section v-else-if="activePage === 'library' || (activePage === 'knowledge' && knowledgeModule === 'library')" class="page-panel library-page">
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
            <button class="secondary-btn" :disabled="!activeFolder" @click="openKnowledgeModule('editor')">
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
              @dblclick="openFileInEditor(file)"
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

        <section v-else-if="activePage === 'planner'" class="page-panel planner-page">
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
                  <p>{{ aiSettings.chatModel }} · {{ aiSettings.chatApiKey ? '已连接模型' : '未配置 Key' }}</p>
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

        <section v-else-if="activePage === 'chat' || (activePage === 'knowledge' && knowledgeModule === 'chat')" class="page-panel chat-page">
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
              <div class="chat-options">
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
              {{ chatLoading ? '生成中' : '发送' }}
            </button>
          </form>
        </section>

        <section v-else-if="activePage === 'editor' || (activePage === 'knowledge' && knowledgeModule === 'editor')" class="page-panel editor-page">
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
                <input
                  v-if="editingFileName"
                  ref="fileNameInput"
                  v-model="editingFileNameValue"
                  class="file-name-edit"
                  maxlength="180"
                  @keydown.enter.prevent="saveFileName"
                  @keydown.esc.prevent="cancelFileNameEdit"
                  @blur="saveFileName"
                />
                <strong v-else title="双击修改文件名" @dblclick="startFileNameEdit">{{ displayFileName(activeFile) }}</strong>
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

        <section v-else-if="activePage === 'mistakes'" class="page-panel mistakes-page">
          <div v-if="!mistakeModule" class="mistake-module-landing">
            <div class="mistake-module-menu">
              <button
                class="mistake-module-card"
                :class="{ active: subjectTagCreatorOpen }"
                type="button"
                @click="subjectTagCreatorOpen = !subjectTagCreatorOpen"
              >
                <Tag :size="34" />
                <strong>新增标签</strong>
                <span>创建科目标签和未掌握状态，用于筛选错题和复盘归因。</span>
              </button>
              <button class="mistake-module-card" type="button" @click="openMistakeModule('upload')">
                <Upload :size="34" />
                <strong>上传错题</strong>
                <span>录入题目、识别 PDF / 图片，并保存文字或图片解析。</span>
              </button>
              <button class="mistake-module-card" type="button" @click="openMistakeModule('practice')">
                <BookOpenCheck :size="34" />
                <strong>刷错题</strong>
                <span>从未掌握错题中随机抽题，可开启倒计时练习。</span>
              </button>
              <button class="mistake-module-card" type="button" @click="openMistakeModule('browse')">
                <Eye :size="34" />
                <strong>浏览错题</strong>
                <span>查看、修改题目和解析，隐藏答案进行自测。</span>
              </button>
            </div>
            <div v-if="subjectTagCreatorOpen" class="mistake-management-panel">
              <div class="mistake-management-group">
                <strong>科目标签</strong>
                <form class="status-create" @submit.prevent="createSubjectTag">
                  <input v-model="newMistakeSubjectTagName" maxlength="60" placeholder="新增科目标签，如：操作系统" />
                  <button class="primary-btn compact" type="submit" :disabled="loading || !newMistakeSubjectTagName.trim()">新增</button>
                </form>
                <div class="status-list">
                  <div v-for="tag in mistakeSubjectTags" :key="tag.id" class="status-row">
                    <span>{{ tag.name }}</span>
                    <button class="icon-btn mini danger" type="button" title="删除标签" @click="deleteSubjectTag(tag)">
                      <Trash2 :size="14" />
                    </button>
                  </div>
                  <div v-if="mistakeSubjectTags.length === 0" class="empty-note">创建标签后，可在上传、刷题和浏览时快速筛选错题。</div>
                </div>
              </div>
              <div class="mistake-management-group">
                <strong>未掌握状态</strong>
                <form class="status-create" @submit.prevent="createMistakeStatus">
                  <input v-model="newMistakeStatusName" maxlength="60" placeholder="新增未掌握状态，如：算错了" />
                  <button class="primary-btn compact" type="submit" :disabled="loading || !newMistakeStatusName.trim()">新增</button>
                </form>
                <div class="status-list">
                  <div v-for="status in mistakeStatuses.filter((item) => !item.mastered)" :key="status.id" class="status-row">
                    <span>{{ status.name }}</span>
                    <button class="icon-btn mini danger" type="button" title="删除状态" @click="deleteMistakeStatus(status)">
                      <Trash2 :size="14" />
                    </button>
                  </div>
                  <div v-if="mistakeStatuses.filter((item) => !item.mastered).length === 0" class="empty-note">可以把未掌握细分为“算错了”“概念混淆”等状态。</div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="mistakeModule === 'upload'" class="mistake-grid">
            <form class="mistake-form" @submit.prevent="saveMistake">
              <div class="section-head split">
                <div>
                  <h3>{{ editingMistake ? '修改错题' : '上传错题' }}</h3>
                  <p>题目可直接输入，也可保存图片或 PDF 附件；解析支持文本和图片附件。</p>
                </div>
                <div class="mistake-section-actions">
                  <button v-if="editingMistake" class="secondary-btn slim" type="button" @click="resetMistakeForm">取消修改</button>
                  <button class="secondary-btn slim" type="button" @click="backToMistakeMenu">返回</button>
                </div>
              </div>

              <label>
                题目
                <textarea v-model="mistakeForm.questionText" class="mistake-textarea" placeholder="输入题目；需要识别 PDF / 图片时，可使用右侧识别工具复制文本" />
              </label>
              <label>
                题目图片
                <input ref="mistakeQuestionAttachmentFile" type="file" accept=".png,.jpg,.jpeg,.webp" multiple @change="onImageSelect('question')" />
              </label>
              <div class="image-attachment-grid">
                <article v-for="item in questionImageItems" :key="item.id" class="image-attachment-item">
                  <img :src="item.previewUrl" alt="题目图片缩略图" @dblclick="enlargeAttachment(item)" />
                  <input v-model="item.displayName" maxlength="180" placeholder="图片名称" />
                  <button class="icon-btn mini danger" type="button" title="移除图片" @click="removeImageItem('question', item.id)">
                    <Trash2 :size="14" />
                  </button>
                </article>
                <div v-if="questionImageItems.length === 0" class="empty-note">可一次选择多张图片，上传后会在这里显示缩略图，双击可放大。</div>
              </div>
              <label>
                题目解析
                <textarea v-model="mistakeForm.solutionText" class="mistake-textarea" placeholder="输入解析文字，也可以上传图片或文件作为解析附件" />
              </label>
              <label>
                解析图片
                <input ref="mistakeSolutionFile" type="file" accept=".png,.jpg,.jpeg,.webp" multiple @change="onImageSelect('solution')" />
              </label>
              <div class="image-attachment-grid">
                <article v-for="item in solutionImageItems" :key="item.id" class="image-attachment-item">
                  <img :src="item.previewUrl" alt="解析图片缩略图" @dblclick="enlargeAttachment(item)" />
                  <input v-model="item.displayName" maxlength="180" placeholder="图片名称" />
                  <button class="icon-btn mini danger" type="button" title="移除图片" @click="removeImageItem('solution', item.id)">
                    <Trash2 :size="14" />
                  </button>
                </article>
                <div v-if="solutionImageItems.length === 0" class="empty-note">解析也支持多张图片，双击缩略图可放大查看。</div>
              </div>

              <div class="mastery-radio" aria-label="错题状态">
                <button
                  class="status-pill"
                  :class="{ active: mistakeForm.statusKey === 'mastered' }"
                  type="button"
                  @click="mistakeForm.statusKey = 'mastered'"
                >
                  完全掌握
                </button>
                <button
                  class="status-pill"
                  :class="{ active: mistakeForm.statusKey !== 'mastered' }"
                  type="button"
                  @click="setUnmasteredStatus"
                >
                  未掌握
                </button>
              </div>

              <div v-if="mistakeForm.statusKey !== 'mastered'" class="mistake-status-picker" aria-label="未掌握原因">
                <button
                  v-for="option in unmasteredStatusOptions"
                  :key="option.key"
                  class="status-pill"
                  :class="{ active: mistakeForm.statusKey === option.key }"
                  type="button"
                  @click="mistakeForm.statusKey = option.key"
                >
                  {{ option.label }}
                </button>
                <span v-if="unmasteredStatusOptions.length === 0" class="empty-inline">请先在右侧新增未掌握原因。</span>
              </div>

              <div class="mistake-label-block">
                <strong>科目</strong>
                <div class="mistake-status-picker" aria-label="科目标签">
                  <button
                    v-for="tag in mistakeSubjectTags"
                    :key="tag.id"
                    class="status-pill"
                    :class="{ active: mistakeForm.subjectTagIds.includes(tag.id) }"
                    type="button"
                    @click="toggleIdInArray(mistakeForm.subjectTagIds, tag.id)"
                  >
                    {{ tag.name }}
                  </button>
                  <span v-if="mistakeSubjectTags.length === 0" class="empty-inline">暂无科目标签，请返回入口新增标签。</span>
                </div>
              </div>

              <button class="primary-btn compact" type="submit" :disabled="loading">
                <Save :size="17" />
                {{ editingMistake ? '保存修改' : '加入错题集' }}
              </button>
            </form>

            <aside class="mistake-side">
              <form class="status-create" @submit.prevent="createMistakeStatus">
                <input v-model="newMistakeStatusName" maxlength="60" placeholder="新增未掌握状态，如：算错了" />
                <button class="primary-btn compact" type="submit" :disabled="loading || !newMistakeStatusName.trim()">新增</button>
              </form>
              <div class="status-list">
                <div v-for="status in mistakeStatuses.filter((item) => !item.mastered)" :key="status.id" class="status-row">
                  <span>{{ status.name }}</span>
                  <button class="icon-btn mini danger" type="button" title="删除状态" @click="deleteMistakeStatus(status)">
                    <Trash2 :size="14" />
                  </button>
                </div>
                <div v-if="mistakeStatuses.filter((item) => !item.mastered).length === 0" class="empty-note">可以把未掌握细分为“算错了”“概念混淆”等状态。</div>
              </div>
              <div class="recognition-tool">
                <div class="section-head">
                  <h3>PDF / 图片识别</h3>
                  <p>识别结果只作为临时文本工具，可复制后粘贴到题目或解析。</p>
                </div>
                <input ref="recognitionFile" type="file" accept=".pdf,.png,.jpg,.jpeg,.webp" />
                <button class="secondary-btn slim" type="button" :disabled="loading || recognitionLoading" @click="recognizeMistakeFile">
                  <ScanText :size="16" />
                  {{ recognitionLoading ? '识别中' : '开始识别' }}
                </button>
                <textarea v-model="recognitionText" class="recognition-output" readonly placeholder="识别出的文本会显示在这里" />
                <button class="secondary-btn slim" type="button" :disabled="!recognitionText" @click="copyRecognitionText">
                  <ClipboardCopy :size="16" />
                  复制文本
                </button>
              </div>
            </aside>
          </div>

          <div v-if="mistakeModule === 'practice'" class="practice-panel">
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
                    v-for="tag in mistakeSubjectTags"
                    :key="tag.id"
                    class="status-pill"
                    :class="{ active: practiceForm.subjectTagIds.includes(tag.id) }"
                    type="button"
                    @click="toggleIdInArray(practiceForm.subjectTagIds, tag.id)"
                  >
                    {{ tag.name }}
                  </button>
                  <span v-if="mistakeSubjectTags.length === 0" class="empty-inline">暂无科目标签</span>
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
              </div>
            </div>
            <div v-else class="empty-note">暂无未掌握错题，先上传或把错题标记为未掌握。</div>
          </div>

          <div v-if="mistakeModule === 'browse'" class="mistake-browser">
            <div class="section-head split">
              <div>
                <h3>浏览错题</h3>
                <p>可修改题目、解析和状态，也可以隐藏解析进行自测。</p>
              </div>
              <div class="mistake-section-actions">
                <button class="secondary-btn slim" type="button" @click="setAllBrowseSolutions(!showBrowseSolution)">
                  <EyeOff v-if="showBrowseSolution" :size="16" />
                  <Eye v-else :size="16" />
                  {{ showBrowseSolution ? '隐藏解析' : '显示解析' }}
                </button>
                <button class="secondary-btn slim" type="button" @click="backToMistakeMenu">返回</button>
              </div>
            </div>

            <div class="browser-filter-bar">
              <strong>科目筛选</strong>
              <div class="mistake-status-picker">
                <button
                  v-for="tag in mistakeSubjectTags"
                  :key="tag.id"
                  class="status-pill"
                  :class="{ active: browseSubjectFilterIds.includes(tag.id) }"
                  type="button"
                  @click="toggleIdInArray(browseSubjectFilterIds, tag.id)"
                >
                  {{ tag.name }}
                </button>
                <button v-if="browseSubjectFilterIds.length" class="secondary-btn slim" type="button" @click="browseSubjectFilterIds = []">清空筛选</button>
                <span v-if="mistakeSubjectTags.length === 0" class="empty-inline">暂无科目标签</span>
              </div>
            </div>

            <div class="mistake-list">
              <article
                v-for="mistake in filteredMistakes"
                :key="mistake.id"
                class="mistake-card"
                :class="{ selected: activeMistake?.id === mistake.id }"
                @click="activeMistake = mistake"
              >
                <div class="mistake-card-head">
                  <div class="mistake-card-meta">
                    <select
                      v-if="editingStatusMistakeId === mistake.id"
                      class="status-select compact"
                      :data-status-editor-id="mistake.id"
                      :value="statusKeyForMistake(mistake)"
                      aria-label="修改错题状态"
                      @click.stop
                      @dblclick.stop
                      @change="changeMistakeStatusFromSelect(mistake, $event)"
                      @blur="editingStatusMistakeId = null"
                    >
                      <option v-for="option in mistakeStatusOptions" :key="option.key" :value="option.key">
                        {{ option.label }}
                      </option>
                    </select>
                    <span
                      v-else
                      class="status-pill compact editable"
                      :class="{ mastered: mistake.mastered }"
                      title="双击修改状态"
                      @dblclick.stop="openMistakeStatusEditor(mistake)"
                    >
                      {{ mistake.statusName }}
                    </span>
                  </div>
                  <div v-if="mistake.subjectTags?.length" class="subject-tag-strip">
                    <span v-for="tag in mistake.subjectTags" :key="tag.id">{{ tag.name }}</span>
                  </div>
                  <div class="mistake-card-actions">
                    <button class="secondary-btn slim" type="button" @click.stop="toggleBrowseSolution(mistake)">
                      <EyeOff v-if="isBrowseSolutionVisible(mistake)" :size="16" />
                      <Eye v-else :size="16" />
                      {{ isBrowseSolutionVisible(mistake) ? '隐藏解析' : '显示解析' }}
                    </button>
                  </div>
                </div>
                <div class="mistake-question" :class="{ 'with-solution': isBrowseSolutionVisible(mistake) }">
                  <div v-if="mistake.questionText" v-html="renderRichText(mistake.questionText)"></div>
                  <div v-if="mistake.questionAttachments?.length" class="saved-image-strip">
                    <figure
                      v-for="attachment in mistake.questionAttachments"
                      :key="attachment.id || attachment.displayName"
                      @dblclick="enlargeSavedAttachment(attachment)"
                    >
                      <img v-if="attachment.id && attachmentPreviewUrls[attachment.id]" :src="attachmentPreviewUrls[attachment.id]" alt="题目图片" />
                      <img v-else-if="!attachment.id && questionPreviewUrls[mistake.id]" :src="questionPreviewUrls[mistake.id]" alt="题目图片" />
                      <figcaption>{{ attachment.displayName || attachment.originalName }}</figcaption>
                    </figure>
                  </div>
                  <img v-if="questionPreviewUrls[mistake.id] && !mistake.questionAttachments?.length" :src="questionPreviewUrls[mistake.id]" alt="题目附件" />
                  <span v-else-if="mistake.hasQuestionFile && !mistake.questionAttachments?.length">
                    <Image :size="15" />
                    {{ mistake.questionOriginalName || '题目附件' }}
                  </span>
                </div>
                <div v-if="isBrowseSolutionVisible(mistake)" class="mistake-solution">
                  <div class="mistake-solution-head">
                    <strong>解析</strong>
                  </div>
                  <div class="mistake-solution-body">
                    <div v-if="mistake.solutionText" v-html="renderRichText(mistake.solutionText)"></div>
                    <div v-if="mistake.solutionAttachments?.length" class="saved-image-strip">
                      <figure
                        v-for="attachment in mistake.solutionAttachments"
                        :key="attachment.id || attachment.displayName"
                        @dblclick="enlargeSavedAttachment(attachment)"
                      >
                        <img v-if="attachment.id && attachmentPreviewUrls[attachment.id]" :src="attachmentPreviewUrls[attachment.id]" alt="解析图片" />
                        <img v-else-if="!attachment.id && solutionPreviewUrls[mistake.id]" :src="solutionPreviewUrls[mistake.id]" alt="解析图片" />
                        <figcaption>{{ attachment.displayName || attachment.originalName }}</figcaption>
                      </figure>
                    </div>
                    <img v-if="solutionPreviewUrls[mistake.id] && !mistake.solutionAttachments?.length" :src="solutionPreviewUrls[mistake.id]" alt="解析图片" />
                    <span v-else-if="mistake.hasSolutionFile && !mistake.solutionAttachments?.length">
                      <Image :size="15" />
                      {{ mistake.solutionOriginalName || '解析附件' }}
                    </span>
                    <span v-if="!mistake.solutionText && !mistake.hasSolutionFile">暂无解析</span>
                  </div>
                </div>
                <div class="mistake-actions">
                  <button class="secondary-btn slim" type="button" @click.stop="editMistake(mistake)">
                    <Pencil :size="15" />
                    修改
                  </button>
                  <button class="secondary-btn slim danger-text" type="button" @click.stop="deleteMistake(mistake)">
                    <Trash2 :size="15" />
                    删除
                  </button>
                </div>
              </article>
              <div v-if="mistakes.length === 0" class="empty-state">
                <BookOpenCheck :size="30" />
                <strong>还没有错题</strong>
                <span>上传题目和解析后，就能按掌握状态刷题复习。</span>
              </div>
              <div v-else-if="filteredMistakes.length === 0" class="empty-note">当前科目筛选下没有错题。</div>
            </div>
          </div>

          <div v-if="enlargedAttachment" class="image-lightbox" role="dialog" aria-label="图片预览" @click.self="enlargedAttachment = null">
            <div class="image-lightbox-panel">
              <div class="image-lightbox-head">
                <strong>{{ enlargedAttachment.displayName || enlargedAttachment.file?.name || '图片预览' }}</strong>
                <button class="icon-btn mini" type="button" title="关闭" @click="enlargedAttachment = null">×</button>
              </div>
              <img :src="enlargedAttachment.previewUrl || enlargedAttachment.url" alt="放大的附件图片" />
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

              <div class="settings-presets">
                <label>
                  设置预设
                  <select v-model="selectedAiPresetId" @change="syncSelectedAiPresetName">
                    <option value="">选择已保存的设置</option>
                    <option v-for="preset in aiSettingPresets" :key="preset.id" :value="preset.id">
                      {{ preset.name }}
                    </option>
                  </select>
                </label>
                <button class="secondary-btn compact" type="button" :disabled="!selectedAiPresetId" @click="applyAiPreset">
                  <RotateCcw :size="16" />
                  恢复
                </button>
                <button class="icon-btn mini danger" type="button" title="删除预设" :disabled="!selectedAiPresetId" @click="deleteAiPreset">
                  <Trash2 :size="15" />
                </button>
                <label>
                  预设名称
                  <input v-model="aiPresetName" maxlength="60" placeholder="例如：专业课答疑 - Qwen" />
                </label>
                <button class="primary-btn compact" type="button" :disabled="!aiPresetName.trim()" @click="saveAiPreset">
                  <Save :size="16" />
                  保存为预设
                </button>
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
                    <option value="deepseek-v4-flash">deepseek-v4-flash</option>
                    <option value="deepseek-v4-pro">deepseek-v4-pro</option>
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
  Send,
  Settings,
  Sparkles,
  Table2,
  Tag,
  Timer,
  Trash2,
  Underline,
  Undo2,
  Upload
} from 'lucide-vue-next'
import { aiSettingsApi, authApi, chatApi, clearSession, fileApi, folderApi, getSession, getToken, mistakeApi, setSession, studyPlanApi } from './api/client'

const session = ref(getSession())
const authMode = ref('login')
const authForm = reactive({ username: '', password: '', displayName: '' })
const folderForm = reactive({ name: '', description: '' })
const editFolderForm = reactive({ name: '', description: '' })
const folders = ref([])
const files = ref([])
const activeFolder = ref(null)
const activeFile = ref(null)
const activePage = ref('knowledge')
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
const editingFileName = ref(false)
const editingFileNameValue = ref('')
const savingFileName = ref(false)
const fileNameInput = ref(null)
const moveFileTargetId = ref('')
const rootFolderCollapsed = ref(false)
const collapsedFolderIds = ref(new Set())
const studyPlanItems = ref([])
const planDraftItems = ref([])
const planSessionByWeek = ref({})
const planModule = ref('')
const knowledgeModule = ref('')
const planWeekStart = ref(startOfWeekIso(new Date()))
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
  subjectTagIds: []
})
const practiceForm = reactive({ count: 5, timed: false, minutes: 20, subjectTagIds: [] })
const practiceQuestions = ref([])
const practiceIndex = ref(0)
const practiceStarted = ref(false)
const practiceFinished = ref(false)
const practiceRemainingSeconds = ref(0)
const practiceTimerId = ref(null)
const showBrowseSolution = ref(true)
const browseSolutionVisibility = ref({})
const editingStatusMistakeId = ref(null)
const browseSubjectFilterIds = ref([])
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
const aiSettings = reactive(loadAiSettings())
const aiSettingPresets = ref(loadAiSettingPresets())
const selectedAiPresetId = ref('')
const aiPresetName = ref('')
const settingsSaved = ref(false)
const maxFolderDepth = 3
const chatHistoryRetentionMs = 24 * 60 * 60 * 1000

const navItems = [
  { key: 'knowledge', label: '我的知识库', icon: Library },
  { key: 'planner', label: '学习规划', icon: CalendarDays },
  { key: 'mistakes', label: '错题集', icon: BookOpenCheck },
  { key: 'settings', label: 'AI 设置', icon: Settings }
]

const pageMeta = {
  knowledge: {
    title: '我的知识库',
    description: '集中管理资料、上传编辑和知识问答。'
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
const messages = computed(() => chatMessages[chatForm.mode])
const currentChatHasMessages = computed(() => messages.value.length > 0)
const chatInputDisabled = computed(() => chatLoading.value || (chatForm.useKnowledgeBase && !activeFolder.value))
const canSubmitChat = computed(() => !loading.value && !chatInputDisabled.value && chatForm.question.trim().length > 0)
const chatPlaceholder = computed(() => chatForm.useKnowledgeBase
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
  if (browseSubjectFilterIds.value.length === 0) return mistakes.value
  return mistakes.value.filter((mistake) => {
    const ids = new Set((mistake.subjectTags || []).map((tag) => tag.id))
    return browseSubjectFilterIds.value.some((id) => ids.has(id))
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
}

function openKnowledgeModule(module) {
  activePage.value = 'knowledge'
  knowledgeModule.value = module
}

onMounted(() => {
  if (session.value) {
    loadFolders()
    loadRemoteAiSettings()
    loadMistakeData()
    loadStudyPlan()
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
    await loadMistakeData()
    await loadStudyPlan()
  })
}

async function loadStudyPlan() {
  await run(async () => {
    studyPlanItems.value = await studyPlanApi.list(planWeekStart.value, planWeekEnd.value)
    restorePlanSession()
  })
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
    questionImageFiles: questionImageItems.value.map((item) => item.file),
    questionImageNames: questionImageItems.value.map((item) => item.displayName),
    solutionText: mistakeForm.solutionText,
    solutionImageFiles: solutionImageItems.value.map((item) => item.file),
    solutionImageNames: solutionImageItems.value.map((item) => item.displayName),
    mastered: status.mastered,
    statusId: status.statusId,
    subjectTagIds: mistakeForm.subjectTagIds
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
  if (item?.previewUrl) URL.revokeObjectURL(item.previewUrl)
  target.value = target.value.filter((entry) => entry.id !== id)
}

function clearImageItems(kind) {
  const target = kind === 'question' ? questionImageItems : solutionImageItems
  target.value.forEach((item) => URL.revokeObjectURL(item.previewUrl))
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
  })
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
  practiceRemainingSeconds.value = 0
}

function nextPracticeQuestion(delta) {
  if (!practiceQuestions.value.length) return
  practiceIndex.value = Math.min(practiceQuestions.value.length - 1, Math.max(0, practiceIndex.value + delta))
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

function currentAiSettingsSnapshot() {
  return normalizeAiSettings({ ...aiSettings })
}

function saveAiPreset() {
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

function deleteAiPreset() {
  if (!selectedAiPresetId.value) return
  aiSettingPresets.value = aiSettingPresets.value.filter((preset) => preset.id !== selectedAiPresetId.value)
  selectedAiPresetId.value = ''
  aiPresetName.value = ''
  persistAiSettingPresets()
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
  activePage.value = 'knowledge'
  folders.value = []
  files.value = []
  activeFolder.value = null
  activeFile.value = null
  activeSource.value = null
  studyPlanItems.value = []
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
  folderChatHistories.value = []
  activeConversationIds.QA = null
  activeConversationIds.TEACHER = null
  historyPanelOpen.value = false
  mistakes.value = []
  mistakeStatuses.value = []
  mistakeSubjectTags.value = []
  activeMistake.value = null
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
</script>
