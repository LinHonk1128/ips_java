<!-- [SEARCH:VIEW_MISTAKE_BROWSE] 错题浏览面板，支持筛选、分页、状态修改和原知识片段跳转。 -->
<template>
<div class="mistake-browser">
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
                  v-for="item in mistakeSubjectOptions"
                  :key="item.folder.id"
                  class="status-pill"
                  :class="{ active: browseSubjectFilterIds.includes(item.tag.id) }"
                  type="button"
                  @click="toggleIdInArray(browseSubjectFilterIds, item.tag.id)"
                >
                  {{ item.folder.name }}
                </button>
                <button v-if="browseSubjectFilterIds.length" class="secondary-btn slim" type="button" @click="browseSubjectFilterIds = []">清空筛选</button>
                <span v-if="mistakeSubjectOptions.length === 0" class="empty-inline">暂无学科文件夹</span>
              </div>
            </div>

            <div class="browser-filter-bar">
              <strong>状态筛选</strong>
              <div class="mistake-status-picker">
                <button
                  v-for="option in mistakeStatusOptions"
                  :key="option.key"
                  class="status-pill filter-status-pill"
                  :class="{ active: browseStatusFilterKeys.includes(option.key) }"
                  type="button"
                  @click="toggleIdInArray(browseStatusFilterKeys, option.key)"
                >
                  {{ option.label }}
                </button>
                <button v-if="browseStatusFilterKeys.length" class="secondary-btn slim" type="button" @click="browseStatusFilterKeys = []">清空状态</button>
              </div>
            </div>

            <div class="mistake-list">
              <article
                v-for="mistake in paginatedMistakes"
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
                  <div class="mistake-content-label">
                    <span>题目</span>
                  </div>
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
                <div v-if="mistake.linkedChunks?.length" class="linked-chunks">
                  <strong>关联知识片段</strong>
                  <button v-for="chunk in mistake.linkedChunks" :key="chunk.chunkId" class="linked-chunk-button" type="button" @click.stop="openChunkDetail(chunk)">
                    {{ displayFileName({ originalName: chunk.fileName }) }} · 掌握度 {{ formatPercent(chunk.masteryRate) }}
                  </button>
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
              <div v-else-if="filteredMistakes.length === 0" class="empty-note">当前筛选下没有错题。</div>
              <div v-else class="mistake-pagination">
                <span>{{ browseMistakePageSummary }}</span>
                <div class="pagination-actions">
                  <button class="secondary-btn slim" type="button" :disabled="browseMistakePage <= 1" @click="browseMistakePage = 1">首页</button>
                  <button class="secondary-btn slim" type="button" :disabled="browseMistakePage <= 1" @click="browseMistakePage -= 1">上一页</button>
                  <button
                    v-for="page in browseMistakePageItems"
                    :key="page"
                    class="page-dot"
                    :class="{ active: page === browseMistakePage, ellipsis: typeof page !== 'number' }"
                    type="button"
                    :disabled="typeof page !== 'number'"
                    @click="browseMistakePage = page"
                  >
                    {{ typeof page === 'number' ? page : '...' }}
                  </button>
                  <button class="secondary-btn slim" type="button" :disabled="browseMistakePage >= browseMistakePageCount" @click="browseMistakePage += 1">下一页</button>
                  <button class="secondary-btn slim" type="button" :disabled="browseMistakePage >= browseMistakePageCount" @click="browseMistakePage = browseMistakePageCount">尾页</button>
                </div>
              </div>
            </div>
          </div>
</template>
<script setup>
import { useAppContext } from '../../composables/appContext'

const { BookOpenCheck, Eye, EyeOff, Image, Pencil, Trash2, mistakes, activeMistake, showBrowseSolution, editingStatusMistakeId, browseSubjectFilterIds, browseStatusFilterKeys, browseMistakePage, browseMistakePageCount, browseMistakePageItems, browseMistakePageSummary, paginatedMistakes, solutionPreviewUrls, questionPreviewUrls, attachmentPreviewUrls, mistakeSubjectOptions, mistakeStatusOptions, filteredMistakes, editMistake, backToMistakeMenu, openChunkDetail, enlargeSavedAttachment, toggleIdInArray, statusKeyForMistake, openMistakeStatusEditor, changeMistakeStatusFromSelect, isBrowseSolutionVisible, toggleBrowseSolution, setAllBrowseSolutions, deleteMistake, renderRichText, displayFileName, formatPercent } = useAppContext()
</script>
