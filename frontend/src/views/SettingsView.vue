<template>
<section class="page-panel settings-page">
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
</template>
<script setup>
import { useAppContext } from '../composables/appContext'

const { Bot, KeyRound, RotateCcw, Save, Trash2, defaultAiSettings, aiSettings, aiSettingPresets, selectedAiPresetId, aiPresetName, settingsSaved, saveAiPreset, applyAiPreset, syncSelectedAiPresetName, deleteAiPreset, saveAiSettings, resetAiSettings } = useAppContext()
</script>
