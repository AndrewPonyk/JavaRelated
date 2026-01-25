"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.SuCommentsProvider = void 0;
const vscode = __importStar(require("vscode"));
const fs = __importStar(require("fs"));
const path = __importStar(require("path"));
// |su:11) TreeDataProvider - VS Code interface for tree views
class SuCommentsProvider {
    constructor() {
        // |su:12) Event emitter - fires when tree needs refresh
        this._onDidChangeTreeData = new vscode.EventEmitter();
        this.onDidChangeTreeData = this._onDidChangeTreeData.event;
        this.suComments = [];
        this.scanWorkspace();
    }
    refresh() {
        this.scanWorkspace();
        this._onDidChangeTreeData.fire();
    }
    collapseAll() {
        vscode.commands.executeCommand('workbench.actions.treeView.suComments.explorer.collapseAll');
    }
    expandAll() {
        vscode.commands.executeCommand('workbench.actions.treeView.suComments.explorer.expandAll');
    }
    // |su:13) Required by TreeDataProvider - returns tree item for display
    getTreeItem(element) {
        return element;
    }
    // |su:14) Required by TreeDataProvider - returns children (flat list here)
    getChildren(element) {
        if (!element) {
            return Promise.resolve(this.getGroupedComments());
        }
        else {
            return Promise.resolve([]);
        }
    }
    // |su:15) Builds flat list of comment items for tree view
    getGroupedComments() {
        const grouped = new Map();
        this.suComments.forEach(comment => {
            if (!grouped.has(comment.number)) {
                grouped.set(comment.number, []);
            }
            grouped.get(comment.number)?.push(comment);
        });
        const items = [];
        const sortedNumbers = Array.from(grouped.keys()).sort((a, b) => a - b);
        sortedNumbers.forEach(number => {
            const comments = grouped.get(number) || [];
            const parentItem = new SuCommentItem(`|su:${number}`, vscode.TreeItemCollapsibleState.Expanded, comments[0]);
            comments.forEach(comment => {
                const childItem = new SuCommentItem(`|su:${comment.number}: ${comment.text}`, vscode.TreeItemCollapsibleState.None, comment);
                childItem.command = {
                    command: 'suComments.navigateTo',
                    title: 'Go to comment',
                    arguments: [comment]
                };
                items.push(childItem);
            });
        });
        return items;
    }
    // |su:20) Main scanning logic - finds all |su:n comments in workspace
    scanWorkspace() {
        this.suComments = [];
        const workspaceFolders = vscode.workspace.workspaceFolders;
        if (!workspaceFolders) {
            return;
        }
        // |su:21) Regex pattern: matches |su:N) or |su:N followed by text
        const suRegex = /\|su:(\d+)(\)|\s)(.*)/gm;
        workspaceFolders.forEach(folder => {
            const files = this.getAllFiles(folder.uri.fsPath);
            files.forEach(filePath => {
                if (this.isTextFile(filePath)) {
                    try {
                        const content = fs.readFileSync(filePath, 'utf-8');
                        const lines = content.split('\n');
                        lines.forEach((line, index) => {
                            const matches = [...line.matchAll(suRegex)];
                            matches.forEach(match => {
                                const number = parseInt(match[1], 10);
                                let text = match[3]?.trim() || '';
                                // |su:22) Status detection - looks for markers like ++, --c, --h
                                let status = 'untouched';
                                let statusSymbol = '';
                                if (text.includes(' ++')) {
                                    status = 'clear';
                                    statusSymbol = '++';
                                    text = text.replace(/\s*\+\+\s*/g, ' ').trim();
                                }
                                else if (text.includes(' --n')) {
                                    status = 'not-clear';
                                    statusSymbol = '--n';
                                    text = text.replace(/\s*--n\s*/g, ' ').trim();
                                }
                                else if (text.includes(' --c')) {
                                    status = 'complex';
                                    statusSymbol = '--c';
                                    text = text.replace(/\s*--c\s*/g, ' ').trim();
                                }
                                else if (text.includes(' --h')) {
                                    status = 'hack';
                                    statusSymbol = '--h';
                                    text = text.replace(/\s*--h\s*/g, ' ').trim();
                                }
                                else if (text.includes(' --b')) {
                                    status = 'bad-code';
                                    statusSymbol = '--b';
                                    text = text.replace(/\s*--b\s*/g, ' ').trim();
                                }
                                else if (text.includes(' --u')) {
                                    status = 'untouched';
                                    statusSymbol = '--u';
                                    text = text.replace(/\s*--u\s*/g, ' ').trim();
                                }
                                this.suComments.push({
                                    id: `${filePath}:${index}:${number}`,
                                    number,
                                    text,
                                    uri: vscode.Uri.file(filePath),
                                    lineNumber: index,
                                    status,
                                    statusSymbol
                                });
                            });
                        });
                    }
                    catch (error) {
                        console.error(`Error reading file ${filePath}:`, error);
                    }
                }
            });
        });
    }
    // |su:23) Recursively gets all files in directory (excludes node_modules, .git)
    getAllFiles(dirPath, fileList = []) {
        const excludedDirs = ['node_modules', '.git'];
        const files = fs.readdirSync(dirPath);
        files.forEach(file => {
            if (excludedDirs.includes(file)) {
                return;
            }
            const filePath = path.join(dirPath, file);
            const stat = fs.statSync(filePath);
            if (stat.isDirectory()) {
                this.getAllFiles(filePath, fileList);
            }
            else {
                fileList.push(filePath);
            }
        });
        return fileList;
    }
    // |su:24) File filter - only scan text-based files
    isTextFile(filePath) {
        const textExtensions = ['.txt', '.js', '.ts', '.jsx', '.tsx', '.html', '.css', '.scss', '.json', '.md', '.py', '.java', '.cpp', '.c', '.h', '.cs', '.go', '.rb', '.php', '.xml', '.yaml', '.yml'];
        const ext = path.extname(filePath).toLowerCase();
        return textExtensions.includes(ext);
    }
    // |su:30) Modifies source file to update status marker (context menu action)
    async updateCommentStatus(comment, newStatus, statusSymbol) {
        try {
            const fileContent = await vscode.workspace.fs.readFile(comment.uri);
            const textContent = new TextDecoder().decode(fileContent);
            const lines = textContent.split('\n');
            let targetLine = lines[comment.lineNumber];
            // Remove existing status, add new one
            let updatedLine = targetLine.replace(/\s*(\+\+|--n|--c|--h|--b|--u)\s*$/, '');
            if (statusSymbol) {
                updatedLine = updatedLine.trimEnd() + ' ' + statusSymbol;
            }
            lines[comment.lineNumber] = updatedLine;
            const newContent = lines.join('\n');
            await vscode.workspace.fs.writeFile(comment.uri, new TextEncoder().encode(newContent));
            this.refresh();
            vscode.window.showInformationMessage(`Updated SU comment status to ${newStatus}`);
        }
        catch (error) {
            console.error('Error updating comment status:', error);
            vscode.window.showErrorMessage(`Failed to update comment status: ${error}`);
        }
    }
}
exports.SuCommentsProvider = SuCommentsProvider;
// |su:40) Tree item class - how each comment appears in sidebar
class SuCommentItem extends vscode.TreeItem {
    constructor(label, collapsibleState, comment) {
        super(label, collapsibleState);
        this.label = label;
        this.collapsibleState = collapsibleState;
        this.comment = comment;
        this.tooltip = `${this.comment.uri.fsPath}:${this.comment.lineNumber + 1}\nStatus: ${this.comment.status}`;
        this.description = `${path.basename(this.comment.uri.fsPath)} [L${this.comment.lineNumber + 1}]`;
        this.setIconByStatus();
        this.contextValue = `suComment`;
    }
    // |su:41) Icon assignment based on status - uses VS Code theme icons
    setIconByStatus() {
        switch (this.comment.status) {
            case 'clear':
                this.iconPath = new vscode.ThemeIcon('pass', new vscode.ThemeColor('testing.iconPassed'));
                break;
            case 'not-clear':
                this.iconPath = new vscode.ThemeIcon('question', new vscode.ThemeColor('editorWarning.foreground'));
                break;
            case 'complex':
                this.iconPath = new vscode.ThemeIcon('warning', new vscode.ThemeColor('editorError.foreground'));
                break;
            case 'hack':
                this.iconPath = new vscode.ThemeIcon('zap', new vscode.ThemeColor('terminal.ansiYellow'));
                break;
            case 'bad-code':
                this.iconPath = new vscode.ThemeIcon('stop', new vscode.ThemeColor('errorForeground'));
                break;
            case 'untouched':
            default:
                this.iconPath = new vscode.ThemeIcon('circle-outline', new vscode.ThemeColor('foreground'));
                break;
        }
    }
}
//# sourceMappingURL=SuCommentsProvider.js.map