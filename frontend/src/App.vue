<template>
  <main class="app-shell">
    <AuthView v-if="!session" />
    <OnboardingView v-else-if="studyProfile && !studyProfile.onboarded" />
    <WorkspaceLayout v-else />
  </main>
</template>

<script setup>
import { provide } from 'vue'
import WorkspaceLayout from './components/layout/WorkspaceLayout.vue'
import { appContextKey } from './composables/appContext'
import { useSmartExamApp } from './composables/useSmartExamApp'
import AuthView from './views/AuthView.vue'
import OnboardingView from './views/OnboardingView.vue'

const app = useSmartExamApp()
provide(appContextKey, app)

const { session, studyProfile } = app
</script>
