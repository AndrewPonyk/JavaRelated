<template>
  <div class="upload-container card">
    <h2>Upload Document</h2>
    <div class="upload-area">
      <input 
        type="file" 
        ref="fileInput" 
        @change="handleFileSelect" 
        accept=".pdf,.png,.jpg,.jpeg" 
        class="file-input"
      />
      
      <div v-if="selectedFile" class="file-details">
        <p>Selected: <strong>{{ selectedFile.name }}</strong></p>
        <p>Size: {{ (selectedFile.size / 1024 / 1024).toFixed(2) }} MB</p>
      </div>

      <button 
        @click="uploadFile" 
        :disabled="!selectedFile || uploading"
        class="btn btn-primary"
      >
        {{ uploading ? 'Uploading...' : 'Upload & Process' }}
      </button>
    </div>

    <div v-if="uploadError" class="error-text mt-2">
      {{ uploadError }}
    </div>
    <div v-if="uploadSuccess" class="success-text mt-2">
      File uploaded successfully!
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { api } from '../services/api';
import axios from 'axios';

const emit = defineEmits(['upload-success']);

const fileInput = ref<HTMLInputElement | null>(null);
const selectedFile = ref<File | null>(null);
const uploading = ref(false);
const uploadError = ref('');
const uploadSuccess = ref(false);

const handleFileSelect = (e: Event) => {
  const target = e.target as HTMLInputElement;
  if (target.files && target.files.length > 0) {
    const file = target.files[0];
    
    // 30MB limit
    if (file.size > 30 * 1024 * 1024) {
      uploadError.value = 'File exceeds the maximum limit of 30MB.';
      selectedFile.value = null;
    } else {
      selectedFile.value = file;
      uploadError.value = '';
    }
    uploadSuccess.value = false;
  }
};

const uploadFile = async () => {
  if (!selectedFile.value) return;
  
  try {
    uploading.value = true;
    uploadError.value = '';
    uploadSuccess.value = false;

    // 1. Get presigned URL
    const { data } = await api.post('/documents/upload-url', {
      filename: selectedFile.value.name,
      size: selectedFile.value.size
    });
    
    // 2. Upload to S3 using the URL directly with PUT
    await axios.put(data.uploadUrl, selectedFile.value, {
      headers: {
        'Content-Type': selectedFile.value.type || 'application/octet-stream' // fallback
      }
    });

    uploadSuccess.value = true;
    selectedFile.value = null;
    if (fileInput.value) fileInput.value.value = '';
    
    emit('upload-success');

  } catch (err: any) {
    console.error("Upload error:", err);
    uploadError.value = err.response?.data?.error || err.message || 'Failed to upload document.';
  } finally {
    uploading.value = false;
  }
};
</script>

<style scoped>
.card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 6px rgba(0,0,0,0.05);
  padding: 20px;
}
.upload-area {
  display: flex;
  flex-direction: column;
  gap: 15px;
}
.file-input {
  border: 1px dashed #cbd5e0;
  padding: 20px;
  border-radius: 4px;
  background: #f7fafc;
}
.file-details {
  background: #edf2f7;
  padding: 10px 15px;
  border-radius: 4px;
  font-size: 0.9rem;
}
.file-details p { margin: 5px 0; }
.btn {
  padding: 10px 20px;
  border-radius: 4px;
  border: none;
  cursor: pointer;
  font-weight: 600;
  font-size: 1rem;
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-primary {
  background: #3182ce;
  color: white;
}
.btn-primary:not(:disabled):hover {
  background: #2b6cb0;
}
.error-text { color: #c53030; font-size: 0.9rem; }
.success-text { color: #2f855a; font-size: 0.9rem; font-weight: 500;}
.mt-2 { margin-top: 10px; }
</style>
