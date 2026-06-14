<!-- [SEARCH:VIEW_MISTAKES] 错题模块路由容器，根据子模块状态切换录入、浏览和复习页面。 -->
<template>
  <section class="page-panel mistakes-page">
    <MistakeModuleLanding v-if="!mistakeModule" />
    <MistakeUploadPanel v-if="mistakeModule === 'upload'" />
    <MistakePracticePanel v-if="mistakeModule === 'practice'" />
    <MistakeBrowsePanel v-if="mistakeModule === 'browse'" />
    <MistakeOverlays />
  </section>
</template>
<script setup>
import MistakeBrowsePanel from '../components/mistakes/MistakeBrowsePanel.vue'
import MistakeModuleLanding from '../components/mistakes/MistakeModuleLanding.vue'
import MistakeOverlays from '../components/mistakes/MistakeOverlays.vue'
import MistakePracticePanel from '../components/mistakes/MistakePracticePanel.vue'
import MistakeUploadPanel from '../components/mistakes/MistakeUploadPanel.vue'
import { useAppContext } from '../composables/appContext'

const { mistakeModule } = useAppContext()
</script>
