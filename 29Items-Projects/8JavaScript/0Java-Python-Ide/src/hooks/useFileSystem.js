import { useState, useEffect } from 'react'

const STORAGE_KEY = 'ide-files'

const defaultFiles = {
  'src/Main.java': {
    content: `public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}`,
    type: 'file'
  },
  'src/utils/StringHelper.java': {
    content: `package utils;

public class StringHelper {
    public static String reverse(String input) {
        return new StringBuilder(input).reverse().toString();
    }
}`,
    type: 'file'
  },
  'README.md': {
    content: `# My Java Project

A simple Java project created in the browser IDE.

## Getting Started

Edit the files in the \`src\` folder to begin.
`,
    type: 'file'
  }
}

export function useFileSystem() {
  const [files, setFiles] = useState(() => {
    // Load from localStorage on mount
    try {
      const saved = localStorage.getItem(STORAGE_KEY)
      return saved ? JSON.parse(saved) : defaultFiles
    } catch (e) {
      console.error('Failed to load files from localStorage:', e)
      return defaultFiles
    }
  })

  // Save to localStorage whenever files change
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(files))
    } catch (e) {
      console.error('Failed to save files to localStorage:', e)
      if (e.name === 'QuotaExceededError') {
        alert('Storage full! Please delete some files or export them.')
      }
    }
  }, [files])

  const createFile = (path, content = '') => {
    setFiles(prev => ({
      ...prev,
      [path]: { content, type: 'file' }
    }))
  }

  const updateFile = (path, content) => {
    setFiles(prev => ({
      ...prev,
      [path]: { ...prev[path], content }
    }))
  }

  const deleteFile = (path) => {
    setFiles(prev => {
      const newFiles = { ...prev }
      delete newFiles[path]
      return newFiles
    })
  }

  const renameFile = (oldPath, newPath) => {
    setFiles(prev => {
      const newFiles = { ...prev }
      newFiles[newPath] = newFiles[oldPath]
      delete newFiles[oldPath]
      return newFiles
    })
  }

  return {
    files,
    createFile,
    updateFile,
    deleteFile,
    renameFile
  }
}
