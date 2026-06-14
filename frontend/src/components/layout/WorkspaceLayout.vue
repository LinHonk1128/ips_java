<!-- [SEARCH:VIEW_WORKSPACE_LAYOUT] 登录后的工作区骨架，提供主导航、目录侧栏和页面出口。 -->
<template>
<section class="workspace">
      <aside class="sidebar">
        <div class="side-top">
          <div class="brand-line">
            <img class="brand-mark small" :src="brandLogo" alt="智能考研系统标识" />
            <div>
              <strong>智能考研</strong>
              <span>{{ session.displayName || session.username }}</span>
            </div>
          </div>
          <button class="icon-btn" title="退出登录" @click="logout"><LogOut :size="18" /></button>
        </div>

        <nav class="page-nav" aria-label="功能页面">
          <button
            v-for="item in navItems"
            :key="item.key"
            :class="{ active: activePage === item.key }"
            @click="setActivePage(item.key)"
          >
            <component :is="item.icon" :size="18" />
            <span>{{ item.label }}</span>
          </button>
        </nav>

        <div class="folder-context">
          <div class="context-head">
            <span>当前文件夹</span>
            <button class="icon-btn mini" title="刷新文件夹" @click="loadFolders"><RefreshCw :size="15" /></button>
          </div>
          <button class="folder-item" :class="{ selected: !activeFolder }" @click="selectRoot">
            <FolderOpen :size="18" />
            <span>我的资料</span>
            <ChevronRight
              class="folder-toggle"
              :class="{ open: !rootFolderCollapsed }"
              :size="15"
              role="button"
              tabindex="0"
              @click.stop="toggleRootFolder"
              @keydown.enter.stop.prevent="toggleRootFolder"
              @keydown.space.stop.prevent="toggleRootFolder"
            />
          </button>
          <button
            v-for="folder in folderTree"
            :key="folder.id"
            class="folder-item"
            :class="{ selected: activeFolder?.id === folder.id, 'subject-root': folder.subjectFolder && !folder.parentId }"
            :style="{ '--folder-indent': `${(folder.depth - 1) * 18}px` }"
            @click="selectFolder(folder)"
          >
            <Folder :size="18" />
            <span>{{ folder.name }}</span>
            <ChevronRight
              v-if="hasFolderChildren(folder.id)"
              class="folder-toggle"
              :class="{ open: !collapsedFolderIds.has(folder.id) }"
              :size="15"
              role="button"
              tabindex="0"
              @click.stop="toggleFolderCollapse(folder)"
              @keydown.enter.stop.prevent="toggleFolderCollapse(folder)"
              @keydown.space.stop.prevent="toggleFolderCollapse(folder)"
            />
            <span v-else class="folder-toggle spacer"></span>
          </button>
          <div v-if="folders.length === 0" class="empty-note">还没有文件夹，请先到“我的资料”页面创建。</div>
        </div>
      </aside>

      <section class="content-area">
        <header class="topbar">
          <div>
            <h2>{{ pageTitle }}</h2>
            <p>{{ pageDescription }}</p>
          </div>
          <div class="folder-chip" :class="{ muted: !activeFolder }">
            <FolderOpen :size="18" />
            <span>{{ currentFolderName }}</span>
          </div>
        </header>

        <p v-if="error" class="error-banner">{{ error }}</p>

                <WorkspacePageRouter />
      </section>
    </section>
</template>
<script setup>
import brandLogo from '../../assets/brand-logo.svg'
import WorkspacePageRouter from './WorkspacePageRouter.vue'
import { useAppContext } from '../../composables/appContext'

const { ChevronRight, Folder, FolderOpen, LogOut, RefreshCw, session, folders, activeFolder, activePage, error, rootFolderCollapsed, collapsedFolderIds, navItems, pageTitle, pageDescription, folderTree, currentFolderName, setActivePage, loadFolders, selectRoot, hasFolderChildren, toggleRootFolder, toggleFolderCollapse, selectFolder, logout } = useAppContext()
</script>
