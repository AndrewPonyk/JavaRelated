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
// |su:1) VS Code calls this when extension activates (entry point)
function activate(context) {
    console.log('SU Comments extension is now active!');
    // |su:2) Create data provider and tree view for sidebar panel
    const provider = new SuCommentsProvider_1.SuCommentsProvider();
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
                    const range = new vscode.Range(element.lineNumber, 0, element.lineNumber, doc.lineAt(element.lineNumber).text.length);
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
    // |su:6) Register all disposables - VS Code cleans these up on deactivate
    context.subscriptions.push(treeView, refreshCommand, collapseAllCommand, expandAllCommand, navigateCommand, setStatusClearCommand, setStatusNotClearCommand, setStatusComplexCommand, setStatusHackCommand, setStatusBadCodeCommand, setStatusUntouchedCommand);
    provider.refresh();
}
exports.activate = activate;
function deactivate() { }
exports.deactivate = deactivate;
//# sourceMappingURL=extension.js.map