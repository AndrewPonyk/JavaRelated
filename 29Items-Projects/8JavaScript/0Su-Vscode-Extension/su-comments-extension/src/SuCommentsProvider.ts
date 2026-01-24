import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';

interface SuComment {
    id: string;
    number: number;
    text: string;
    uri: vscode.Uri;
    lineNumber: number;
    status: string;
    statusSymbol: string;
}

export class SuCommentsProvider implements vscode.TreeDataProvider<SuCommentItem> {
    private _onDidChangeTreeData: vscode.EventEmitter<SuCommentItem | undefined | void> = new vscode.EventEmitter<SuCommentItem | undefined | void>();
    readonly onDidChangeTreeData: vscode.Event<SuCommentItem | undefined | void> = this._onDidChangeTreeData.event;

    private suComments: SuComment[] = [];

    constructor() {
        this.scanWorkspace();
    }

    refresh(): void {
        this.scanWorkspace();
        this._onDidChangeTreeData.fire();
    }

    collapseAll(): void {
        vscode.commands.executeCommand('workbench.actions.treeView.suComments.explorer.collapseAll');
    }

    expandAll(): void {
        vscode.commands.executeCommand('workbench.actions.treeView.suComments.explorer.expandAll');
    }

    getTreeItem(element: SuCommentItem): vscode.TreeItem {
        return element;
    }

    getChildren(element?: SuCommentItem): Thenable<SuCommentItem[]> {
        if (!element) {
            // Root level: group by number
            return Promise.resolve(this.getGroupedComments());
        } else {
            // Child level: show individual comments
            return Promise.resolve([]);
        }
    }

    private getGroupedComments(): SuCommentItem[] {
        // Group comments by number
        const grouped = new Map<number, SuComment[]>();
        
        this.suComments.forEach(comment => {
            if (!grouped.has(comment.number)) {
                grouped.set(comment.number, []);
            }
            grouped.get(comment.number)?.push(comment);
        });

        const items: SuCommentItem[] = [];

        // Sort numbers in ascending order
        const sortedNumbers = Array.from(grouped.keys()).sort((a, b) => a - b);
        
        sortedNumbers.forEach(number => {
            const comments = grouped.get(number) || [];
            
            // Create parent item for the number group
            const parentItem = new SuCommentItem(
                `|su:${number}`,
                vscode.TreeItemCollapsibleState.Expanded,
                comments[0] // Use the first comment to determine status for the group
            );
            
            // Add child items for each comment under this number
            comments.forEach(comment => {
                const childItem = new SuCommentItem(
                    `|su:${comment.number}: ${comment.text}`,  // Show number and text
                    vscode.TreeItemCollapsibleState.None,
                    comment
                );
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

    private scanWorkspace(): void {
        this.suComments = [];
        
        const workspaceFolders = vscode.workspace.workspaceFolders;
        if (!workspaceFolders) {
            return;
        }

        // Regular expression to match SU comments
        // Matches |su:n followed by any text that may contain status symbols anywhere
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
                                let text = match[3]?.trim() || '';  // The full comment text

                                // Extract status symbol if present anywhere in the text
                                let status = 'untouched'; // default status
                                let statusSymbol = '';

                                // Check for status indicators anywhere in the text
                                if (text.includes(' ++')) {
                                    status = 'clear';
                                    statusSymbol = '++';
                                    text = text.replace(/\s*\+\+\s*/g, ' ').trim();
                                } else if (text.includes(' --n')) {
                                    status = 'not-clear';
                                    statusSymbol = '--n';
                                    text = text.replace(/\s*--n\s*/g, ' ').trim();
                                } else if (text.includes(' --c')) {
                                    status = 'complex';
                                    statusSymbol = '--c';
                                    text = text.replace(/\s*--c\s*/g, ' ').trim();
                                } else if (text.includes(' --h')) {
                                    status = 'hack';
                                    statusSymbol = '--h';
                                    text = text.replace(/\s*--h\s*/g, ' ').trim();
                                } else if (text.includes(' --b')) {
                                    status = 'bad-code';
                                    statusSymbol = '--b';
                                    text = text.replace(/\s*--b\s*/g, ' ').trim();
                                } else if (text.includes(' --u')) {
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
                    } catch (error) {
                        console.error(`Error reading file ${filePath}:`, error);
                    }
                }
            });
        });
    }

    private getAllFiles(dirPath: string, fileList: string[] = []): string[] {
        const files = fs.readdirSync(dirPath);
        
        files.forEach(file => {
            const filePath = path.join(dirPath, file);
            const stat = fs.statSync(filePath);
            
            if (stat.isDirectory()) {
                this.getAllFiles(filePath, fileList);
            } else {
                fileList.push(filePath);
            }
        });
        
        return fileList;
    }

    private isTextFile(filePath: string): boolean {
        const textExtensions = ['.txt', '.js', '.ts', '.jsx', '.tsx', '.html', '.css', '.scss', '.json', '.md', '.py', '.java', '.cpp', '.c', '.h', '.cs', '.go', '.rb', '.php', '.xml', '.yaml', '.yml'];
        const ext = path.extname(filePath).toLowerCase();
        return textExtensions.includes(ext);
    }
}

class SuCommentItem extends vscode.TreeItem {
    constructor(
        public readonly label: string,
        public readonly collapsibleState: vscode.TreeItemCollapsibleState,
        public readonly comment: SuComment
    ) {
        super(label, collapsibleState);
        
        // Set tooltip with file path and line number
        this.tooltip = `${this.comment.uri.fsPath}:${this.comment.lineNumber + 1}\nStatus: ${this.comment.status}`;
        
        // Set description to show status
        this.description = `[${this.comment.status}] Line ${this.comment.lineNumber + 1} • ${path.basename(this.comment.uri.fsPath)} ${this.comment.statusSymbol}`;
        
        // Set icon based on status with different colors
        this.setIconByStatus();
        
        // Set context value for different actions based on status
        this.contextValue = `suComment_${this.comment.status}`;
    }
    
    private setIconByStatus(): void {
        // Different icons based on status
        switch(this.comment.status) {
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