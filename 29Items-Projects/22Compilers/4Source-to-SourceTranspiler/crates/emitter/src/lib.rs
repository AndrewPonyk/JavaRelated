use transpiler_core::{Expression, Program, Statement};
use transpiler_sourcemap::{SourceMap, SourceMapSegment};

#[derive(Debug, Clone)]
pub struct EmitOptions {
    pub source_file: String,
    pub output_file: String,
    pub source_maps: bool,
}

impl Default for EmitOptions {
    fn default() -> Self {
        Self {
            source_file: "input.tsl".to_string(),
            output_file: "output.js".to_string(),
            source_maps: true,
        }
    }
}

#[derive(Debug, Clone)]
pub struct EmitResult {
    pub javascript: String,
    pub source_map: Option<serde_json::Value>,
}

#[derive(Debug, Clone, Default)]
pub struct JavaScriptEmitter {
    options: EmitOptions,
}

impl JavaScriptEmitter {
    pub fn new(options: EmitOptions) -> Self {
        Self { options }
    }

    pub fn emit_program(&self, program: &Program) -> EmitResult {
        let mut javascript = String::new();

        for statement in &program.body {
            javascript.push_str(&emit_statement(statement));
            javascript.push('\n');
        }

        let source_map = self.options.source_maps.then(|| {
            let mut map = SourceMap::empty(self.options.output_file.clone());
            map.sources.push(self.options.source_file.clone());
            map.sources_content.push(String::new());
            for (line, statement) in program.body.iter().enumerate() {
                if !matches!(statement, Statement::Empty) {
                    map.segments.push(SourceMapSegment {
                        generated_line: line as u32,
                        generated_column: 0,
                        source_index: 0,
                        original_line: line as u32,
                        original_column: 0,
                        name: None,
                    });
                }
            }
            map.mappings = ";".repeat(program.body.len().saturating_sub(1));
            map.to_json().unwrap_or_else(|_| serde_json::json!({ "version": 3 }))
        });

        EmitResult {
            javascript,
            source_map,
        }
    }
}

fn emit_statement(statement: &Statement) -> String {
    match statement {
        Statement::Variable {
            mutable,
            name,
            value,
            ..
        } => {
            let keyword = if *mutable { "let" } else { "const" };
            format!("{keyword} {name} = {};", emit_expression(value))
        }
        Statement::Function {
            name, params, body, ..
        } => {
            let params = params
                .iter()
                .map(|param| param.name.clone())
                .collect::<Vec<_>>()
                .join(", ");
            let body = body
                .iter()
                .map(emit_statement)
                .filter(|line| !line.is_empty())
                .map(|line| format!("  {line}"))
                .collect::<Vec<_>>()
                .join("\n");

            if body.is_empty() {
                format!("function {name}({params}) {{}}")
            } else {
                format!("function {name}({params}) {{\n{body}\n}}")
            }
        }
        Statement::Return(Some(expression)) => format!("return {};", emit_expression(expression)),
        Statement::Return(None) => "return;".to_string(),
        Statement::Expression(expression) => format!("{};", emit_expression(expression)),
        Statement::Empty => String::new(),
    }
}

fn emit_expression(expression: &Expression) -> String {
    match expression {
        Expression::Number(value) => emit_number(*value),
        Expression::String(value) => {
            serde_json::to_string(value).unwrap_or_else(|_| "\"\"".to_string())
        }
        Expression::Boolean(value) => value.to_string(),
        Expression::Identifier(name) => name.clone(),
        Expression::Binary { op, left, right } => {
            format!(
                "({} {} {})",
                emit_expression(left),
                op.as_js(),
                emit_expression(right)
            )
        }
        Expression::Call { callee, args } => {
            let args = args
                .iter()
                .map(emit_expression)
                .collect::<Vec<_>>()
                .join(", ");
            format!("{callee}({args})")
        }
        Expression::MacroCall { name, args } => {
            let args = args
                .iter()
                .map(emit_expression)
                .collect::<Vec<_>>()
                .join(", ");
            format!("{name}({args})")
        }
    }
}

fn emit_number(value: f64) -> String {
    if value.fract() == 0.0 {
        format!("{}", value as i64)
    } else {
        value.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use transpiler_core::{Expression, Program, Statement};

    #[test]
    fn emits_basic_let_statement() {
        let program = Program {
            body: vec![Statement::Variable {
                mutable: true,
                name: "answer".to_string(),
                type_annotation: None,
                value: Expression::Number(42.0),
            }],
        };

        let result = JavaScriptEmitter::default().emit_program(&program);

        assert_eq!(result.javascript, "let answer = 42;\n");
        assert!(result.source_map.is_some());
    }
}
