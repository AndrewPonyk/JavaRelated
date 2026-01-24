# SU Comments Extension

This VS Code extension helps you manage and navigate SU comments in your codebase.

## Features

- Displays all `|su:n` comments in a dedicated panel in the Explorer sidebar
- Comments are sorted by number
- Shows tooltips with file path and line number
- One-click navigation to comment locations
- Collapsible sections for each SU comment number
- Refresh button to rescan the workspace
- Color coding for different statuses:
  - Clear (++): Green
  - Not clear (--n): Yellow
  - Complex (--c): Red
  - Hack (--h): Purple
  - Bad code (--b): Pink
  - Untouched (--u): Gray (default)

## Usage

1. Open your workspace in VS Code
2. Find the "SU Comments" panel in the Explorer sidebar
3. Click on any comment to navigate to its location in the code
4. Use the refresh button to rescan for new comments

## Comment Format

The extension looks for comments in the format:
- `|su:1 Your comment here`
- `|su:2 Your comment here ++` (clear)
- `|su:3 Your comment here --n` (not clear)
- `|su:4 Your comment here --c` (complex)
- `|su:5 Your comment here --h` (hack)
- `|su:6 Your comment here --b` (bad code)
- `|su:7 Your comment here --u` (untouched)

## Commands

- `suComments.refresh` - Refresh the SU comments list
- `suComments.collapseAll` - Collapse all comment groups
- `suComments.expandAll` - Expand all comment groups
- `suComments.navigateTo` - Navigate to a specific comment location