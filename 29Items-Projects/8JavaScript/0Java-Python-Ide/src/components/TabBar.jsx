function getFileName(path) {
  return path.split('/').pop()
}

function TabBar({ openFiles, activeFile, onTabSelect, onTabClose }) {
  if (openFiles.length === 0) {
    return <div className="tab-bar empty"></div>
  }

  return (
    <div className="tab-bar">
      {openFiles.map(path => (
        <div
          key={path}
          className={`tab ${path === activeFile ? 'active' : ''}`}
          onClick={() => onTabSelect(path)}
        >
          <span className="tab-name">{getFileName(path)}</span>
          <button
            className="tab-close"
            onClick={(e) => {
              e.stopPropagation()
              onTabClose(path)
            }}
          >
            ×
          </button>
        </div>
      ))}
    </div>
  )
}

export default TabBar
