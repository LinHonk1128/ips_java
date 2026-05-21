<template>
<div v-if="enlargedAttachment" class="image-lightbox" role="dialog" aria-label="图片预览" @click.self="enlargedAttachment = null">
            <div class="image-lightbox-panel">
              <div class="image-lightbox-head">
                <strong>{{ enlargedAttachment.displayName || enlargedAttachment.file?.name || '图片预览' }}</strong>
                <button class="icon-btn mini" type="button" title="关闭" @click="enlargedAttachment = null">×</button>
              </div>
              <img :src="enlargedAttachment.previewUrl || enlargedAttachment.url" alt="放大的附件图片" />
            </div>
          </div>
          <div v-if="activeChunkDetail" class="chunk-detail-modal" role="dialog" aria-label="知识片段详情" @click.self="activeChunkDetail = null">
            <article class="chunk-detail-panel">
              <div class="source-popover-head">
                <strong>{{ displayFileName({ originalName: activeChunkDetail.fileName }) }} · 第 {{ activeChunkDetail.pageNumber || 1 }} 页</strong>
                <button class="icon-btn mini" type="button" title="关闭" @click="activeChunkDetail = null">×</button>
              </div>
              <p>{{ activeChunkDetail.excerpt }}</p>
              <div class="source-stats">
                <span>掌握度 {{ formatPercent(activeChunkDetail.masteryRate) }}</span>
                <span>引用 {{ activeChunkDetail.citeCount || 0 }}</span>
                <span>对 {{ activeChunkDetail.correctHitCount || 0 }} / 错 {{ activeChunkDetail.wrongHitCount || 0 }}</span>
              </div>
            </article>
          </div>
</template>
<script setup>
import { useAppContext } from '../../composables/appContext'

const { activeChunkDetail, enlargedAttachment, displayFileName, formatPercent } = useAppContext()
</script>
