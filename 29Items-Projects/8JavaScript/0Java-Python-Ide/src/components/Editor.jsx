import MonacoEditor from '@monaco-editor/react'
import { useRef } from 'react'
import { registerJavaCompletions } from '../utils/javaCompletions'

function getLanguage(file) {
  if (!file) return 'plaintext'
  if (file.endsWith('.java')) return 'java'
  if (file.endsWith('.py')) return 'python'
  if (file.endsWith('.js')) return 'javascript'
  if (file.endsWith('.md')) return 'markdown'
  if (file.endsWith('.json')) return 'json'
  if (file.endsWith('.xml')) return 'xml'
  if (file.endsWith('.html')) return 'html'
  if (file.endsWith('.css')) return 'css'
  return 'plaintext'
}

function Editor({ file, content, onChange }) {
  const completionRegistered = useRef(false)

  const handleEditorMount = (editor, monaco) => {
    if (!completionRegistered.current) {
      registerJavaCompletions(monaco)
      completionRegistered.current = true
    }
  }

  if (!file) {
    return (
      <div className="editor-placeholder">
        <div className="placeholder-content">
          <span className="placeholder-icon">☕</span>
          <h2>Java Browser IDE</h2>
          <p>Select a file from the explorer to start editing</p>
          <p className="placeholder-hint">or click + to create a new file</p>
        </div>
      </div>
    )
  }

  return (
    <div className="editor-container">
      <MonacoEditor
        height="100%"
        language={getLanguage(file)}
        value={content}
        onChange={(value) => onChange(file, value || '')}
        onMount={handleEditorMount}
        theme="light"
        options={{
          minimap: { enabled: true },
          fontSize: 14,
          lineNumbers: 'on',
          scrollBeyondLastLine: false,
          automaticLayout: true,
          tabSize: 4,
          insertSpaces: true,
          wordWrap: 'off',
          folding: true,
          renderLineHighlight: 'line',
          selectOnLineNumbers: true,
          roundedSelection: false,
          cursorStyle: 'line',
          cursorBlinking: 'smooth',
          smoothScrolling: true,
          contextmenu: true,
          mouseWheelZoom: true,
        }}
      />
    </div>
  )
}

export default Editor
