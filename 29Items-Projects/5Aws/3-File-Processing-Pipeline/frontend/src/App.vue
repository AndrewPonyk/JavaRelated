<template>
  <div class="app-container">
    <header class="app-header">
      <h1>Document Digitization Service</h1>
    </header>
    <main class="app-main">
      <UploadDocument @upload-success="refreshDocuments" />
      <DocumentList ref="docListRef" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import UploadDocument from './components/UploadDocument.vue';
import DocumentList from './components/DocumentList.vue';

const docListRef = ref<InstanceType<typeof DocumentList> | null>(null);

const refreshDocuments = () => {
  if (docListRef.value) {
    docListRef.value.fetchDocuments();
  }
};
</script>

<style>
body {
  margin: 0;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  background-color: #f5f7fa;
  color: #333;
}
.app-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
}
.app-header {
  margin-bottom: 30px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e1e4e8;
}
.app-main {
  display: flex;
  flex-direction: column;
  gap: 30px;
}
</style>
