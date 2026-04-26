<template>
  <main class="page">
    <section class="toolbar">
      <label>
        <span>文件名</span>
        <input v-model="fileName" placeholder="drawing.dwg" />
      </label>
      <label>
        <span>文件地址</span>
        <input v-model="fileUrl" placeholder="/uploads/drawing.dwg" />
      </label>
      <label>
        <span>大小</span>
        <input v-model.number="fileSize" type="number" min="0" />
      </label>
    </section>

    <FilePreview
      :file-name="fileName"
      :file-url="fileUrl"
      :file-size="fileSize"
      api-base-url="/api"
    />
  </main>
</template>

<script setup>
import { ref } from 'vue'
import FilePreview from './components/FilePreview.vue'

const fileName = ref('drawing.dwg')
const fileUrl = ref('/uploads/drawing.dwg')
const fileSize = ref(0)
</script>

<style>
* {
  box-sizing: border-box;
}

body {
  margin: 0;
  font-family: Arial, "Microsoft YaHei", sans-serif;
  color: #1f2937;
  background: #eef2f7;
}

.page {
  min-height: 100vh;
  padding: 24px;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(160px, 240px) minmax(280px, 1fr) minmax(120px, 160px);
  gap: 12px;
  align-items: end;
  margin-bottom: 16px;
}

.toolbar label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #4b5563;
}

.toolbar input {
  width: 100%;
  height: 36px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 0 10px;
  font-size: 14px;
  color: #111827;
  background: #fff;
}

@media (max-width: 760px) {
  .page {
    padding: 12px;
  }

  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
