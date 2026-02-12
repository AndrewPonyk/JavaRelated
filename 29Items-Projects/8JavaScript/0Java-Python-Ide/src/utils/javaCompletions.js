import javaSe11Classes from '../data/javaSe11Classes.json'

const JAVA_KEYWORDS = [
  'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char',
  'class', 'const', 'continue', 'default', 'do', 'double', 'else', 'enum',
  'extends', 'final', 'finally', 'float', 'for', 'goto', 'if', 'implements',
  'import', 'instanceof', 'int', 'interface', 'long', 'native', 'new', 'package',
  'private', 'protected', 'public', 'return', 'short', 'static', 'strictfp',
  'super', 'switch', 'synchronized', 'this', 'throw', 'throws', 'transient',
  'try', 'var', 'void', 'volatile', 'while', 'true', 'false', 'null',
  'record', 'sealed', 'permits', 'non-sealed', 'yield'
]

const COMMON_SNIPPETS = [
  {
    label: 'main',
    insertText: 'public static void main(String[] args) {\n\t$0\n}',
    documentation: 'Main method'
  },
  {
    label: 'sout',
    insertText: 'System.out.println($0);',
    documentation: 'Print to standard output'
  },
  {
    label: 'serr',
    insertText: 'System.err.println($0);',
    documentation: 'Print to standard error'
  },
  {
    label: 'fori',
    insertText: 'for (int ${1:i} = 0; ${1:i} < ${2:length}; ${1:i}++) {\n\t$0\n}',
    documentation: 'For loop with index'
  },
  {
    label: 'foreach',
    insertText: 'for (${1:Type} ${2:item} : ${3:collection}) {\n\t$0\n}',
    documentation: 'Enhanced for loop'
  },
  {
    label: 'if',
    insertText: 'if (${1:condition}) {\n\t$0\n}',
    documentation: 'If statement'
  },
  {
    label: 'ifelse',
    insertText: 'if (${1:condition}) {\n\t$2\n} else {\n\t$0\n}',
    documentation: 'If-else statement'
  },
  {
    label: 'try',
    insertText: 'try {\n\t$1\n} catch (${2:Exception} ${3:e}) {\n\t$0\n}',
    documentation: 'Try-catch block'
  },
  {
    label: 'tryf',
    insertText: 'try {\n\t$1\n} catch (${2:Exception} ${3:e}) {\n\t$4\n} finally {\n\t$0\n}',
    documentation: 'Try-catch-finally block'
  },
  {
    label: 'tryr',
    insertText: 'try (${1:Resource} ${2:res} = ${3:new Resource()}) {\n\t$0\n}',
    documentation: 'Try with resources'
  },
  {
    label: 'while',
    insertText: 'while (${1:condition}) {\n\t$0\n}',
    documentation: 'While loop'
  },
  {
    label: 'dowhile',
    insertText: 'do {\n\t$0\n} while (${1:condition});',
    documentation: 'Do-while loop'
  },
  {
    label: 'switch',
    insertText: 'switch (${1:key}) {\n\tcase ${2:value}:\n\t\t$0\n\t\tbreak;\n\tdefault:\n\t\tbreak;\n}',
    documentation: 'Switch statement'
  },
  {
    label: 'class',
    insertText: 'public class ${1:ClassName} {\n\t$0\n}',
    documentation: 'Class declaration'
  },
  {
    label: 'interface',
    insertText: 'public interface ${1:InterfaceName} {\n\t$0\n}',
    documentation: 'Interface declaration'
  },
  {
    label: 'enum',
    insertText: 'public enum ${1:EnumName} {\n\t$0\n}',
    documentation: 'Enum declaration'
  },
  {
    label: 'method',
    insertText: '${1:public} ${2:void} ${3:methodName}(${4}) {\n\t$0\n}',
    documentation: 'Method declaration'
  },
  {
    label: 'ctor',
    insertText: 'public ${1:ClassName}(${2}) {\n\t$0\n}',
    documentation: 'Constructor'
  }
]

export function registerJavaCompletions(monaco) {
  monaco.languages.registerCompletionItemProvider('java', {
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position)
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn
      }

      const suggestions = []

      // Add keywords
      JAVA_KEYWORDS.forEach(keyword => {
        suggestions.push({
          label: keyword,
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: keyword,
          range: range,
          detail: 'keyword'
        })
      })

      // Add snippets
      COMMON_SNIPPETS.forEach(snippet => {
        suggestions.push({
          label: snippet.label,
          kind: monaco.languages.CompletionItemKind.Snippet,
          insertText: snippet.insertText,
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: snippet.documentation,
          range: range,
          detail: 'snippet'
        })
      })

      // Add Java SE 11 classes
      javaSe11Classes.classes.forEach(cls => {
        suggestions.push({
          label: cls.name,
          kind: monaco.languages.CompletionItemKind.Class,
          insertText: cls.name,
          range: range,
          detail: cls.package,
          documentation: `${cls.package}.${cls.name}`
        })
      })

      return { suggestions }
    }
  })
}
