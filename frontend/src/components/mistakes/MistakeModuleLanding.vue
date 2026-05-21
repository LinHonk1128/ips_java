<template>
<div class="mistake-module-landing">
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
</template>
<script setup>
import { useAppContext } from '../../composables/appContext'

const { BookOpenCheck, Eye, Tag, Trash2, Upload, loading, mistakeStatuses, mistakeSubjectTags, newMistakeStatusName, newMistakeSubjectTagName, subjectTagCreatorOpen, openMistakeModule, createMistakeStatus, createSubjectTag, deleteSubjectTag, deleteMistakeStatus } = useAppContext()
</script>
