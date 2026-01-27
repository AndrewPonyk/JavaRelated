import * as vscode from 'vscode';
import { SuCommentsProvider } from './SuCommentsProvider';

// |su:1) VS Code calls this when extension activates (entry point)
export function activate(context: vscode.ExtensionContext) {
    console.log('SU Comments extension is now active!');

    // |su:2) Create data provider and tree view for sidebar panel
    const provider = new SuCommentsProvider();
    const treeView = vscode.window.createTreeView('suComments.explorer', {
        treeDataProvider: provider,
        showCollapseAll: true
    });

    // |su:3) Register commands - these map to package.json "commands" section
    const refreshCommand = vscode.commands.registerCommand('suComments.refresh', () => {
        provider.refresh();
    });

    const collapseAllCommand = vscode.commands.registerCommand('suComments.collapseAll', () => {
        provider.collapseAll();
    });

    const expandAllCommand = vscode.commands.registerCommand('suComments.expandAll', () => {
        provider.expandAll();
    });

    // |su:4) Navigation - opens file and highlights the comment line
    const navigateCommand = vscode.commands.registerCommand('suComments.navigateTo', (element) => {
        if (element && element.uri) {
            vscode.workspace.openTextDocument(element.uri).then(doc => {
                vscode.window.showTextDocument(doc).then(editor => {
                    const range = new vscode.Range(
                        element.lineNumber,
                        0,
                        element.lineNumber,
                        doc.lineAt(element.lineNumber).text.length
                    );
                    editor.revealRange(range, vscode.TextEditorRevealType.InCenter);
                    editor.selection = new vscode.Selection(range.start, range.end);
                });
            });
        }
    });

    // |su:5) Context menu commands - change status markers in source files
    const setStatusClearCommand = vscode.commands.registerCommand('suComments.setStatusClear', (element) => {
        if (element && element.comment && element.comment.uri) {
            provider.updateCommentStatus(element.comment, 'clear', '++');
        }
    });

    const setStatusNotClearCommand = vscode.commands.registerCommand('suComments.setStatusNotClear', (element) => {
        if (element && element.comment && element.comment.uri) {
            provider.updateCommentStatus(element.comment, 'not-clear', '--n');
        }
    });

    const setStatusComplexCommand = vscode.commands.registerCommand('suComments.setStatusComplex', (element) => {
        if (element && element.comment && element.comment.uri) {
            provider.updateCommentStatus(element.comment, 'complex', '--c');
        }
    });

    const setStatusHackCommand = vscode.commands.registerCommand('suComments.setStatusHack', (element) => {
        if (element && element.comment && element.comment.uri) {
            provider.updateCommentStatus(element.comment, 'hack', '--h');
        }
    });

    const setStatusBadCodeCommand = vscode.commands.registerCommand('suComments.setStatusBadCode', (element) => {
        if (element && element.comment && element.comment.uri) {
            provider.updateCommentStatus(element.comment, 'bad-code', '--b');
        }
    });

    const setStatusUntouchedCommand = vscode.commands.registerCommand('suComments.setStatusUntouched', (element) => {
        if (element && element.comment && element.comment.uri) {
            provider.updateCommentStatus(element.comment, 'untouched', '--u');
        }
    });

    // |su:7) Copy all comments as markdown to clipboard
    const copyAllAsMarkdownCommand = vscode.commands.registerCommand('suComments.copyAllAsMarkdown', () => {
        const markdown = provider.getAllCommentsAsMarkdown();
        if (markdown) {
            vscode.env.clipboard.writeText(markdown);
            vscode.window.showInformationMessage('All SU comments copied as markdown!');
        } else {
            vscode.window.showInformationMessage('No SU comments found to copy.');
        }
    });

    // |su:6) Register all disposables - VS Code cleans these up on deactivate
    context.subscriptions.push(
        treeView,
        refreshCommand,
        collapseAllCommand,
        expandAllCommand,
        navigateCommand,
        setStatusClearCommand,
        setStatusNotClearCommand,
        setStatusComplexCommand,
        setStatusHackCommand,
        setStatusBadCodeCommand,
        setStatusUntouchedCommand,
        copyAllAsMarkdownCommand
    );

    provider.refresh();
}

export function deactivate() {}
