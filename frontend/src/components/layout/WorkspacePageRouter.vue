<template>
  <HomeView v-if="activePage === 'home'" />
  <KnowledgeLandingView v-else-if="activePage === 'knowledge' && !knowledgeModule" />
  <LibraryView v-else-if="activePage === 'library' || (activePage === 'knowledge' && knowledgeModule === 'library')" />
  <PlannerView v-else-if="activePage === 'planner'" />
  <ChatView v-else-if="activePage === 'chat' || (activePage === 'knowledge' && knowledgeModule === 'chat')" />
  <EditorView v-else-if="activePage === 'editor' || (activePage === 'knowledge' && knowledgeModule === 'editor')" />
  <ProfileView v-else-if="activePage === 'profile'" />
  <MistakesView v-else-if="activePage === 'mistakes'" />
  <PersonalSettingsView v-else-if="activePage === 'personal'" />
  <SettingsView v-else-if="activePage === 'settings'" />
</template>
<script setup>
import ChatView from '../../views/ChatView.vue'
import EditorView from '../../views/EditorView.vue'
import HomeView from '../../views/HomeView.vue'
import KnowledgeLandingView from '../../views/KnowledgeLandingView.vue'
import LibraryView from '../../views/LibraryView.vue'
import MistakesView from '../../views/MistakesView.vue'
import PersonalSettingsView from '../../views/PersonalSettingsView.vue'
import PlannerView from '../../views/PlannerView.vue'
import ProfileView from '../../views/ProfileView.vue'
import SettingsView from '../../views/SettingsView.vue'
import { useAppContext } from '../../composables/appContext'

const { activePage, knowledgeModule } = useAppContext()
</script>
