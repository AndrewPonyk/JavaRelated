import { useState } from 'react'

function sortChildren(children) {
  return Object.values(children).sort((a, b) => {
    if (a.type !== b.type) {
      return a.type === 'folder' ? -1 : 1
    }
    return a.name.localeCompare(b.name)
  })
}

function getFileIcon(name) {
  if (name.endsWith('.java')) return '☕'
  if (name.endsWith('.md')) return '📝'
  if (name.endsWith('.json')) return '📋'
  if (name.endsWith('.xml')) return '📄'
  if (name.endsWith('.txt')) return '📄'
  return '📄'
}

function FileTreeNode({ node, onFileSelect, onDelete, onRename, activeFile, level }) {
  const [expanded, setExpanded] = useState(true)

  const handleClick = () => {
    if (node.type === 'folder') {
      setExpanded(!expanded)
    } else {
      onFileSelect(node.path)
    }
  }

  const handleContextMenu = (e) => {
    e.preventDefault()
    if (node.type === 'file') {
      const action = prompt(`Actions for ${node.name}:\n1. Rename\n2. Delete\n\nEnter 1 or 2:`)
      if (action === '1') {
        const newName = prompt('New file path:', node.path)
        if (newName && newName !== node.path) {
          onRename(node.path, newName)
        }
      } else if (action === '2') {
        if (confirm(`Delete ${node.name}?`)) {
          onDelete(node.path)
        }
      }
    }
  }

  const isActive = node.path === activeFile

  return (
    <div className="tree-node">
      <div
        className={`tree-node-label ${isActive ? 'active' : ''}`}
        style={{ paddingLeft: `${level * 16 + 8}px` }}
        onClick={handleClick}
        onContextMenu={handleContextMenu}
      >
        <span className="tree-node-icon">
          {node.type === 'folder' ? (expanded ? '📂' : '📁') : getFileIcon(node.name)}
        </span>
        <span className="tree-node-name">{node.name}</span>
      </div>
      {node.type === 'folder' && expanded && node.children && (
        <div className="tree-node-children">
          {sortChildren(node.children).map(child => (
            <FileTreeNode
              key={child.path || child.name}
              node={child}
              onFileSelect={onFileSelect}
              onDelete={onDelete}
              onRename={onRename}
              activeFile={activeFile}
              level={level + 1}
            />
          ))}
        </div>
      )}
    </div>
  )
}

export default FileTreeNode
