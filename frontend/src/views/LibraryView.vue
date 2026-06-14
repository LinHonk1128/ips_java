<!-- [SEARCH:VIEW_LIBRARY] 资料库页面，负责目录树、文件列表、移动和知识库启停操作。 -->
<template>
<section class="page-panel library-page">
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
</template>
<script setup>
import { useAppContext } from '../composables/appContext'

const { ChevronRight, FileText, Folder, FolderPlus, MessageSquare, MoveRight, Pencil, Save, Trash2, Upload, folderForm, editFolderForm, files, activeFolder, activeFile, loading, movingFile, editingFolder, moveFileTargetId, visibleFolders, folderPath, fileMoveTargetOptions, canSubmitFileMove, canCreateFolder, folderNamePlaceholder, createFolderHint, emptyFolderTitle, emptyFolderDescription, openKnowledgeModule, createFolder, selectRoot, openEditFolder, cancelEditFolder, saveFolderEdit, deleteFolder, folderOptionLabel, openMoveFile, cancelMoveFile, moveFile, useCurrentFolderAsKnowledgeBase, selectFolder, selectFile, openFileInEditor, toggleKnowledge, deleteFile, displayFileName, tagLabel } = useAppContext()
</script>
