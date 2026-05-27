<template>
<section class="page-panel settings-page">
          <div class="settings-layout">
            <form class="settings-form" @submit.prevent="saveAiSettings">
              <div class="section-head">
                <h3>AI 角色与提示词</h3>
                <p>这些设置会应用到知识问答和定制练题，适合按你的复习习惯调整回答风格。</p>
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
              <p>{{ aiSettings.systemPrompt || defaultAiSettings.systemPrompt }}</p>
            </aside>
          </div>
        </section>
</template>
<script setup>
import { useAppContext } from '../composables/appContext'

const { Bot, RotateCcw, Save, Trash2, defaultAiSettings, aiSettings, aiSettingPresets, selectedAiPresetId, aiPresetName, settingsSaved, saveAiPreset, applyAiPreset, syncSelectedAiPresetName, deleteAiPreset, saveAiSettings, resetAiSettings } = useAppContext()
</script>
