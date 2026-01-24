import * as vscode from 'vscode';
import { SuCommentsProvider } from './SuCommentsProvider';


// |su:1 Entry point for the extension
export function activate(context: vscode.ExtensionContext) {
    console.log('SU Comments extension is now active!');

    const provider = new SuCommentsProvider();
    const treeView = vscode.window.createTreeView('suComments.explorer', {
        treeDataProvider: provider,
        showCollapseAll: true
    });

    // Register the refresh command
    const refreshCommand = vscode.commands.registerCommand('suComments.refresh', () => {
        provider.refresh();
    });

    // Register the collapse all command
    const collapseAllCommand = vscode.commands.registerCommand('suComments.collapseAll', () => {
        provider.collapseAll();
    });

    // Register the expand all command
    const expandAllCommand = vscode.commands.registerCommand('suComments.expandAll', () => {
        provider.expandAll();
    });

    // Register the navigation command
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

    context.subscriptions.push(treeView, refreshCommand, collapseAllCommand, expandAllCommand, navigateCommand);

    // Refresh initially
    provider.refresh();
}

export function deactivate() {}