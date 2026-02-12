import { useMemo } from 'react'
import FileTreeNode from './FileTreeNode'

function buildTree(files) {
  const root = { name: 'root', children: {}, type: 'folder' }

  Object.keys(files).forEach(path => {
    const parts = path.split('/')
    let current = root

    parts.forEach((part, index) => {
      if (index === parts.length - 1) {
        // It's a file
        current.children[part] = {
          name: part,
          path: path,
          type: 'file'
        }
      } else {
        // It's a folder
        if (!current.children[part]) {
          current.children[part] = {
            name: part,
            path: parts.slice(0, index + 1).join('/'),
            children: {},
            type: 'folder'
          }
        }
        current = current.children[part]
      }
    })
  })

  return root
}

function sortChildren(children) {
  return Object.values(children).sort((a, b) => {
    // Folders first, then files
    if (a.type !== b.type) {
      return a.type === 'folder' ? -1 : 1
    }
    return a.name.localeCompare(b.name)
  })
}

function FileTree({ files, onFileSelect, onDelete, onRename, activeFile }) {
  const tree = useMemo(() => buildTree(files), [files])

  return (
    <div className="file-tree">
      {sortChildren(tree.children).map(node => (
        <FileTreeNode
          key={node.path || node.name}
          node={node}
          onFileSelect={onFileSelect}
          onDelete={onDelete}
          onRename={onRename}
          activeFile={activeFile}
          level={0}
        />
      ))}
    </div>
  )
}

export default FileTree
