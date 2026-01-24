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
exports.deactivate = exports.activate = void 0;
const vscode = __importStar(require("vscode"));
const SuCommentsProvider_1 = require("./SuCommentsProvider");
// |su:1 Entry point for the extension
function activate(context) {
    console.log('SU Comments extension is now active!');
    const provider = new SuCommentsProvider_1.SuCommentsProvider();
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
                    const range = new vscode.Range(element.lineNumber, 0, element.lineNumber, doc.lineAt(element.lineNumber).text.length);
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
exports.activate = activate;
function deactivate() { }
exports.deactivate = deactivate;
//# sourceMappingURL=extension.js.map