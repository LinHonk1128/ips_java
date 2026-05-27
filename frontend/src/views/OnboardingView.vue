<template>
<section class="auth-panel onboarding-panel" aria-label="学习信息初始化">
      <div class="auth-copy">
        <img class="brand-mark" :src="brandLogo" alt="智能考研系统标识" />
        <h1>设置学习画像基础信息</h1>
        <p>先确定考研日期和一级学科文件夹，后续资料、问答、错题和画像都会按这些学科聚合。</p>
      </div>
      <form class="auth-card onboarding-card" @submit.prevent="submitOnboarding">
        <label>
          考研日期
          <input v-model="onboardingForm.examDate" required type="date" :min="todayIso" />
        </label>
        <label>
          学科数量
          <input v-model.number="onboardingForm.subjectCount" type="number" min="1" max="12" @change="syncOnboardingSubjects" />
        </label>
        <div class="onboarding-subjects">
          <label v-for="(_, index) in onboardingForm.subjects" :key="index">
            学科 {{ index + 1 }}
            <input v-model="onboardingForm.subjects[index]" required maxlength="120" placeholder="如：数学" />
          </label>
        </div>
        <button class="primary-btn" type="submit" :disabled="loading || !canSubmitOnboarding">
          <FolderPlus :size="18" />
          创建学科文件夹
        </button>
        <p v-if="error" class="error-text">{{ error }}</p>
      </form>
    </section>
</template>
<script setup>
import brandLogo from '../assets/brand-logo.svg'
import { useAppContext } from '../composables/appContext'

const { FolderPlus, onboardingForm, loading, error, examDate, todayIso, canSubmitOnboarding, syncOnboardingSubjects, submitOnboarding } = useAppContext()
</script>
