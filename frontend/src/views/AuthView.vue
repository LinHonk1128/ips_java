<template>
<section class="auth-panel" aria-label="登录">
      <div class="auth-copy">
        <div class="brand-mark">研</div>
        <h1>智能考研系统</h1>
        <p>整理教材、资料和笔记，构建自己的科目知识库，再用大模型做答疑和教师式追问。</p>
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
import { useAppContext } from '../composables/appContext'

const { LogIn, authMode, authForm, loading, error, submitAuth } = useAppContext()
</script>
