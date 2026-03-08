<template>
  <div class="document-list card">
    <h2>Processed Documents</h2>
    
    <div class="actions">
      <button @click="fetchDocuments" :disabled="loading" class="btn btn-secondary">
        Refresh List
      </button>
    </div>
    
    <div v-if="loading && documents.length === 0" class="status-msg loading">
      Loading documents...
    </div>
    
    <div v-else-if="error" class="status-msg error">
      {{ error }}
    </div>
    
    <ul v-else class="list-container">
      <li v-for="doc in sortedDocuments" :key="doc.documentId" class="doc-item">
        <div class="doc-info">
          <div class="doc-main">
            <strong>{{ doc.filename }}</strong>
            <span :class="['status-badge', doc.status.toLowerCase()]">
              {{ doc.status }}
            </span>
          </div>
          <div class="doc-meta">
            Uploaded: {{ formatTime(doc.uploadedAt) }}
          </div>
        </div>
        
        <div v-if="doc.textContent && doc.status === 'COMPLETED'" class="doc-results">
          <h4>Extracted Text Excerpt:</h4>
          <p class="text-preview">{{ doc.textContent.substring(0, 150) }}...</p>
          
          <div v-if="doc.entities && doc.entities.length > 0" class="entities">
            <h4>Entities:</h4>
            <div class="entity-tags">
              <span v-for="(entity, index) in doc.entities" :key="index" class="tag">
                {{ entity.Type }}: {{ entity.Text }}
              </span>
            </div>
          </div>
        </div>
        
        <div v-if="doc.errorMsg" class="doc-error">
          Error: {{ doc.errorMsg }}
        </div>
      </li>
      
      <li v-if="documents.length === 0 && !loading" class="empty-state">
        No documents processed yet. Upload a document to get started.
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { api } from '../services/api';

interface Entity {
  Type: string;
  Text: string;
  Score: number;
}

interface DocumentItem {
  documentId: string;
  filename: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  uploadedAt: string;
  textContent?: string;
  entities?: Entity[];
  errorMsg?: string;
}

const documents = ref<DocumentItem[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

const sortedDocuments = computed(() => {
  return [...documents.value].sort((a, b) => {
    return new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime();
  });
});

const formatTime = (isoStr: string) => {
  if (!isoStr) return 'N/A';
  return new Date(isoStr).toLocaleString();
};

const fetchDocuments = async () => {
  try {
    loading.value = true;
    error.value = null;
    
    const response = await api.get('/documents');
    documents.value = response.data;
  } catch (err: any) {
    error.value = 'Failed to load documents. Ensure backend is running.';
    console.error('Fetch error:', err);
  } finally {
    loading.value = false;
  }
};

defineExpose({ fetchDocuments });

onMounted(() => {
  fetchDocuments();
});
</script>

<style scoped>
.card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 6px rgba(0,0,0,0.05);
  padding: 20px;
}
.actions {
  margin-bottom: 15px;
}
.btn {
  padding: 8px 16px;
  border-radius: 4px;
  border: none;
  cursor: pointer;
  font-weight: 500;
}
.btn-secondary {
  background: #e2e8f0;
  color: #4a5568;
}
.btn-secondary:hover {
  background: #cbd5e0;
}
.status-msg { padding: 10px; margin-bottom: 15px; border-radius: 4px; }
.loading { background-color: #ebf8ff; color: #2b6cb0; }
.error { background-color: #fff5f5; color: #c53030; }
.list-container { list-style: none; padding: 0; margin: 0; }
.doc-item { border: 1px solid #e2e8f0; padding: 15px; margin-bottom: 15px; border-radius: 8px; }
.doc-info { margin-bottom: 10px; }
.doc-main { display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px; }
.status-badge { padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 700; }
.status-badge.completed { background-color: #c6f6d5; color: #22543d; }
.status-badge.processing { background-color: #fefcbf; color: #744210; }
.status-badge.pending { background-color: #e2e8f0; color: #4a5568; }
.status-badge.failed { background-color: #fed7d7; color: #742a2a; }
.doc-meta { font-size: 0.85rem; color: #718096; }
.doc-results { background: #f7fafc; padding: 10px; border-radius: 4px; margin-top: 10px; }
.doc-results h4 { margin: 0 0 5px 0; font-size: 0.9rem; color: #4a5568; }
.text-preview { font-size: 0.85rem; color: #4a5568; font-style: italic; margin-bottom: 10px; }
.entity-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.tag { background: #edf2f7; border: 1px solid #e2e8f0; padding: 2px 6px; border-radius: 4px; font-size: 0.75rem; color: #2d3748; }
.doc-error { margin-top: 10px; color: #c53030; font-size: 0.85rem; }
.empty-state { text-align: center; color: #a0aec0; padding: 20px 0; font-style: italic; }
</style>
