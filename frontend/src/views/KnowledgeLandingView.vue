<template>
<section class="page-panel knowledge-page">
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
</template>
<script setup>
import { useAppContext } from '../composables/appContext'

const { Library, MessageSquare, ScanText, openKnowledgeModule } = useAppContext()
</script>
