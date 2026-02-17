import { useState } from 'react'
import FileTree from './components/FileTree'
import TabBar from './components/TabBar'
import Editor from './components/Editor'
import { useFileSystem } from './hooks/useFileSystem'

function App() {
  const { files, createFile, updateFile, deleteFile, renameFile } = useFileSystem()
  const [openFiles, setOpenFiles] = useState([])
  const [activeFile, setActiveFile] = useState(null)

  const handleFileSelect = (path) => {
    if (!openFiles.includes(path)) {
      setOpenFiles([...openFiles, path])
    }
    setActiveFile(path)
  }

  const handleTabClose = (path) => {
    const newOpenFiles = openFiles.filter(f => f !== path)
    setOpenFiles(newOpenFiles)
    if (activeFile === path) {
      setActiveFile(newOpenFiles[newOpenFiles.length - 1] || null)
    }
  }

  const handleFileChange = (path, content) => {
    updateFile(path, content)
  }

  return (
    <div className="ide-container">
      <header className="ide-header">
        <span className="ide-title">Java Browser IDE</span>
        <button
          className="run-button"
          onClick={() => {
            if (activeFile) {
              alert(`Run: ${activeFile}\n\n(Backend not connected yet - dummy action)`);
            } else {
              alert('No file selected. Please select a file to run.');
            }
          }}
          disabled={!activeFile}
        >
          ▶ Run File
        </button>
      </header>
      <main className="ide-main">
        <aside className="ide-sidebar">
          <div className="sidebar-header">
            <span>Explorer</span>
            <button
              className="new-file-btn"
              onClick={() => {
                const name = prompt('File name (e.g., src/MyClass.java):')
                if (name) createFile(name, '')
              }}
              title="New File"
            >
              +
            </button>
          </div>
          <FileTree
            files={files}
            onFileSelect={handleFileSelect}
            onDelete={deleteFile}
            onRename={renameFile}
            activeFile={activeFile}
          />
        </aside>
        <div className="ide-editor-area">
          <TabBar
            openFiles={openFiles}
            activeFile={activeFile}
            onTabSelect={setActiveFile}
            onTabClose={handleTabClose}
          />
          <Editor
            file={activeFile}
            content={activeFile ? files[activeFile]?.content : ''}
            onChange={handleFileChange}
          />
        </div>
      </main>
    </div>
  )
}

export default App
