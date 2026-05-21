<template>
<div class="mistake-grid">
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
                  <img v-if="item.previewUrl" :src="item.previewUrl" alt="题目图片缩略图" @dblclick="enlargeAttachment(item)" />
                  <div v-else class="image-attachment-placeholder">已保存图片</div>
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
                  <img v-if="item.previewUrl" :src="item.previewUrl" alt="解析图片缩略图" @dblclick="enlargeAttachment(item)" />
                  <div v-else class="image-attachment-placeholder">已保存图片</div>
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
                  v-for="item in mistakeSubjectOptions"
                  :key="item.folder.id"
                  class="status-pill"
                  :class="{ active: mistakeForm.subjectTagIds.includes(item.tag.id) }"
                  type="button"
                  @click="toggleIdInArray(mistakeForm.subjectTagIds, item.tag.id)"
                >
                  {{ item.folder.name }}
                </button>
                  <span v-if="mistakeSubjectOptions.length === 0" class="empty-inline">暂无学科文件夹，请先到个人设置中维护考研科目。</span>
                </div>
              </div>

              <div class="mistake-label-block">
                <strong>关联知识片段</strong>
                <div class="chunk-link-filters">
                  <select v-model="mistakeChunkSubjectFolderId" @change="handleMistakeChunkSubjectChange">
                    <option value="">全部学科</option>
                    <option v-for="folder in subjectFolders" :key="folder.id" :value="folder.id">{{ folder.name }}</option>
                  </select>
                  <select v-model="mistakeChunkFileId" :disabled="mistakeChunkFiles.length === 0">
                    <option value="">全部资料</option>
                    <option v-for="file in mistakeChunkFiles" :key="file.fileId" :value="file.fileId">{{ displayFileName({ originalName: file.fileName }) }}</option>
                  </select>
                </div>
                <div class="chunk-link-search">
                  <input v-model="mistakeChunkQuery" maxlength="120" placeholder="搜索知识片段，如：CPU、无名管道" @keydown.enter.prevent="searchMistakeChunks" />
                  <button class="secondary-btn slim" type="button" :disabled="mistakeChunkSearchLoading" @click="searchMistakeChunks">
                    <Search :size="16" />
                    搜索
                  </button>
                </div>
                <div v-if="mistakeForm.linkedChunks.length" class="linked-chunks selected">
                  <span v-for="chunk in mistakeForm.linkedChunks" :key="chunk.chunkId" class="linked-chunk-pill">
                    <button class="text-link" type="button" @click="openChunkDetail(chunk)">
                      {{ displayFileName({ originalName: chunk.fileName }) }} · 第 {{ chunk.pageNumber || 1 }} 页
                    </button>
                    <button class="icon-btn mini" type="button" title="移除关联" @click="removeMistakeLinkedChunk(chunk.chunkId)">×</button>
                  </span>
                </div>
                <p v-else class="chunk-link-hint">未关联知识片段时，刷题结果不会回写知识画像。</p>
                <div v-if="mistakeChunkCandidates.length" class="chunk-candidate-list">
                  <button
                    v-for="chunk in mistakeChunkCandidates"
                    :key="chunk.chunkId"
                    class="chunk-candidate"
                    type="button"
                    :disabled="isMistakeChunkLinked(chunk.chunkId)"
                    @click="addMistakeLinkedChunk(chunk)"
                  >
                    <strong>{{ displayFileName({ originalName: chunk.fileName }) }} · 第 {{ chunk.pageNumber || 1 }} 页</strong>
                    <span>{{ chunk.excerpt }}</span>
                    <em>掌握度 {{ formatPercent(chunk.masteryRate) }}</em>
                  </button>
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
</template>
<script setup>
import { useAppContext } from '../../composables/appContext'

const { ClipboardCopy, Save, ScanText, Search, Trash2, loading, mistakeStatuses, editingMistake, mistakeQuestionAttachmentFile, mistakeSolutionFile, questionImageItems, solutionImageItems, recognitionFile, recognitionText, recognitionLoading, newMistakeStatusName, mistakeForm, mistakeChunkQuery, mistakeChunkCandidates, mistakeChunkSearchLoading, mistakeChunkSubjectFolderId, mistakeChunkFileId, mistakeChunkFiles, subjectFolders, mistakeSubjectOptions, unmasteredStatusOptions, resetMistakeForm, backToMistakeMenu, saveMistake, setUnmasteredStatus, searchMistakeChunks, handleMistakeChunkSubjectChange, isMistakeChunkLinked, addMistakeLinkedChunk, removeMistakeLinkedChunk, openChunkDetail, onImageSelect, removeImageItem, enlargeAttachment, createMistakeStatus, toggleIdInArray, recognizeMistakeFile, copyRecognitionText, deleteMistakeStatus, displayFileName, formatPercent } = useAppContext()
</script>
