import { useState } from 'react'
import FileTree from './components/FileTree'
import TabBar from './components/TabBar'
import Editor from './components/Editor'
import { useFileSystem } from './hooks/useFileSystem'

function App() {
  const { files, createFile, updateFile, deleteFile, renameFile } = useFileSystem()
  const [openFiles, setOpenFiles] = useState([])
  const [activeFile, setActiveFile] = useState(null)
  const [showConsole, setShowConsole] = useState(false)
  const [consoleOutput, setConsoleOutput] = useState([])

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

  const handleRunFile = async () => {
    if (activeFile) {
      // Clear previous output
      setConsoleOutput([])
      setShowConsole(true)
      setConsoleOutput(prev => [...prev, `Running: ${activeFile}`])
      setConsoleOutput(prev => [...prev, `Compiling ${activeFile}...`])
      
      const content = files[activeFile]?.content || ''
      const fileName = activeFile.split('/').pop()
      const isPython = fileName.endsWith('.py')
      const className = fileName.replace(/\.(java|py)$/, '')
      
      const endpoint = isPython ? 'http://localhost:8080/api/execute-python' : 'http://localhost:8080/api/execute'
      
      try {
        const response = await fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            className: isPython ? fileName : className,
            code: content
          })
        })
        
        const result = await response.json()
        
        if (result.success) {
          setConsoleOutput(prev => [...prev, `Output:`])
          result.output?.split('\n').forEach(line => {
            if (line) setConsoleOutput(prev => [...prev, line])
          })
        } else {
          setConsoleOutput(prev => [...prev, `Error:`])
          result.error?.split('\n').forEach(line => {
            if (line) setConsoleOutput(prev => [...prev, line])
          })
        }
      } catch (err) {
        setConsoleOutput(prev => [...prev, `Error: Failed to connect to backend - ${err.message}`])
      }
    } else {
      alert('No file selected. Please select a file to run.')
    }
  }

  const handleClearConsole = () => {
    setConsoleOutput([])
  }

  const handleCloseConsole = () => {
    setShowConsole(false)
  }

  return (
    <div className="ide-container">
      <header className="ide-header">
        <span className="ide-title">Java/Python/JS IDE</span>
        <button
          className="run-button"
          onClick={handleRunFile}
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
      {showConsole && (
        <div className="console-panel">
          <div className="console-header">
            <span className="console-title">Console Output</span>
            <div className="console-actions">
              <button
                className="console-clear-btn"
                onClick={handleClearConsole}
                title="Clear Console"
              >
                🗑 Clear
              </button>
              <button
                className="console-close-btn"
                onClick={handleCloseConsole}
                title="Close Panel"
              >
                ✕
              </button>
            </div>
          </div>
          <div className="console-content">
            {consoleOutput.map((line, index) => (
              <div key={index} className="console-line">
                {line}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export default App
