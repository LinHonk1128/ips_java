<!-- [SEARCH:VIEW_AUTH] 登录与注册页面；表单状态和提交逻辑由应用上下文统一管理。 -->
<template>
<section class="auth-panel" aria-label="登录">
      <div class="auth-copy">
        <img class="brand-mark" :src="brandLogo" alt="智能考研系统标识" />
        <h1>智能考研系统</h1>
        <p>私有知识库驱动答疑、练题、复盘。</p>
      </div>
      <form class="auth-card" @submit.prevent="submitAuth">
        <div class="segmented">
          <button type="button" :class="{ active: authMode === 'login' }" @click="authMode = 'login'">登录</button>
          <button type="button" :class="{ active: authMode === 'register' }" @click="authMode = 'register'">注册</button>
        </div>
        <label>
          用户名
          <input v-model="authForm.username" required minlength="3" autocomplete="username" />
        </label>
        <label>
          密码
          <input v-model="authForm.password" required minlength="6" type="password" autocomplete="current-password" />
        </label>
        <label v-if="authMode === 'register'">
          昵称
          <input v-model="authForm.displayName" maxlength="128" />
        </label>
        <button class="primary-btn" type="submit" :disabled="loading">
          <LogIn :size="18" />
          {{ authMode === 'login' ? '进入学习空间' : '创建账号' }}
        </button>
        <p v-if="error" class="error-text">{{ error }}</p>
      </form>
    </section>
</template>
<script setup>
import brandLogo from '../assets/brand-logo.svg'
import { useAppContext } from '../composables/appContext'

const { LogIn, authMode, authForm, loading, error, submitAuth } = useAppContext()
</script>
