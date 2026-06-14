<!-- [SEARCH:VIEW_EDITOR] 资料编辑页面，支持分页编辑、格式化、上传和正文保存。 -->
<template>
<section class="page-panel editor-page">
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
</template>
<script setup>
import { useAppContext } from '../composables/appContext'

const { Bold, FileText, Italic, List, ListOrdered, MoveRight, Palette, Save, ScanText, Table2, Trash2, Underline, Upload, files, activeFolder, activeFile, uploadTag, fileInput, editorElement, activeFilePages, activeFilePageIndex, editorTextColor, loading, movingFile, editingFileName, editingFileNameValue, fileNameInput, moveFileTargetId, fileMoveTargetOptions, canSubmitFileMove, folderOptionLabel, openMoveFile, cancelMoveFile, moveFile, selectFile, syncEditorContent, renderCurrentEditorPage, goFilePage, formatEditor, setEditorTextColor, insertTable, uploadFile, saveFileText, toggleKnowledge, deleteFile, displayFileName, startFileNameEdit, cancelFileNameEdit, saveFileName, tagLabel } = useAppContext()
</script>
