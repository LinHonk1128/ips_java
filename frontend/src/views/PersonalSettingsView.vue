<!-- [SEARCH:VIEW_PERSONAL_SETTINGS] 个人备考设置页面，维护考试日期和学科列表。 -->
<template>
<section class="page-panel settings-page personal-settings-page">
          <form class="settings-form" @submit.prevent="savePersonalSettings">
            <div class="section-head">
              <h3>个人设置</h3>
              <p>调整考研时间和一级学科文件夹，错题筛选会同步使用这些科目。</p>
            </div>
            <label>
              考研时间
              <input v-model="personalSettingsForm.examDate" required type="date" :min="todayIso" />
            </label>
            <div class="section-head compact">
              <h3>考研科目</h3>
              <p>这里的科目会作为“我的资料”下的一级学科文件夹。</p>
            </div>
            <div class="personal-subject-list">
              <label v-for="(_, index) in personalSettingsForm.subjects" :key="index">
                科目 {{ index + 1 }}
                <div class="personal-subject-row">
                  <input v-model="personalSettingsForm.subjects[index]" required maxlength="120" placeholder="如：高等数学" />
                  <button class="icon-btn mini danger" type="button" title="移除科目" :disabled="personalSettingsForm.subjects.length <= 1" @click="removePersonalSubject(index)">
                    <Trash2 :size="15" />
                  </button>
                </div>
              </label>
            </div>
            <div class="settings-actions">
              <button class="secondary-btn compact" type="button" :disabled="personalSettingsForm.subjects.length >= 12" @click="addPersonalSubject">
                <FolderPlus :size="16" />
                添加科目
              </button>
              <button class="primary-btn compact" type="submit" :disabled="loading || !canSavePersonalSettings">
                <Save :size="17" />
                保存个人设置
              </button>
            </div>
          </form>
        </section>
</template>
<script setup>
import { useAppContext } from '../composables/appContext'

const { FolderPlus, Save, Trash2, personalSettingsForm, loading, examDate, todayIso, canSavePersonalSettings, addPersonalSubject, removePersonalSubject, savePersonalSettings } = useAppContext()
</script>
