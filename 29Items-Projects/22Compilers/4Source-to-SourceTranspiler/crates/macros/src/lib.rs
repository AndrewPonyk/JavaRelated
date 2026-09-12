use std::collections::HashSet;

use thiserror::Error;
use transpiler_core::{Expression, Program, Statement};

#[derive(Debug, Error)]
pub enum MacroExpansionError {
    #[error("macro expansion exceeded maximum depth of {0}")]
    RecursionLimit(usize),
    #[error("unknown macro '{0}'")]
    UnknownMacro(String),
    #[error("macro '{name}' expected {expected} arguments but received {actual}")]
    Arity {
        name: String,
        expected: usize,
        actual: usize,
    },
}

#[derive(Debug, Clone)]
pub struct MacroExpansionContext {
    pub max_depth: usize,
    expanded: HashSet<String>,
}

impl Default for MacroExpansionContext {
    fn default() -> Self {
        Self {
            max_depth: 32,
            expanded: HashSet::new(),
        }
    }
}

impl MacroExpansionContext {
    pub fn expand_program(&mut self, program: Program) -> Result<Program, MacroExpansionError> {
        Ok(Program {
            body: program
                .body
                .into_iter()
                .map(|statement| self.expand_statement(statement, 0))
                .collect::<Result<Vec<_>, _>>()?,
        })
    }

    pub fn register_expansion(&mut self, name: &str) {
        self.expanded.insert(name.to_string());
    }

    pub fn expanded_macros(&self) -> Vec<String> {
        let mut names = self.expanded.iter().cloned().collect::<Vec<_>>();
        names.sort();
        names
    }

    pub fn is_macro_call(expression: &Expression) -> bool {
        matches!(expression, Expression::MacroCall { .. })
    }

    fn expand_statement(
        &mut self,
        statement: Statement,
        depth: usize,
    ) -> Result<Statement, MacroExpansionError> {
        Ok(match statement {
            Statement::Variable {
                mutable,
                name,
                type_annotation,
                value,
            } => Statement::Variable {
                mutable,
                name,
                type_annotation,
                value: self.expand_expression(value, depth)?,
            },
            Statement::Function {
                name,
                params,
                return_type,
                body,
            } => Statement::Function {
                name,
                params,
                return_type,
                body: body
                    .into_iter()
                    .map(|statement| self.expand_statement(statement, depth))
                    .collect::<Result<Vec<_>, _>>()?,
            },
            Statement::Return(value) => {
                Statement::Return(value.map(|value| self.expand_expression(value, depth)).transpose()?)
            }
            Statement::Expression(expression) => {
                Statement::Expression(self.expand_expression(expression, depth)?)
            }
            Statement::Empty => Statement::Empty,
        })
    }

    fn expand_expression(
        &mut self,
        expression: Expression,
        depth: usize,
    ) -> Result<Expression, MacroExpansionError> {
        if depth > self.max_depth {
            return Err(MacroExpansionError::RecursionLimit(self.max_depth));
        }

        Ok(match expression {
            Expression::Binary { op, left, right } => Expression::Binary {
                op,
                left: Box::new(self.expand_expression(*left, depth)?),
                right: Box::new(self.expand_expression(*right, depth)?),
            },
            Expression::Call { callee, args } => Expression::Call {
                callee,
                args: args
                    .into_iter()
                    .map(|arg| self.expand_expression(arg, depth))
                    .collect::<Result<Vec<_>, _>>()?,
            },
            Expression::MacroCall { name, args } => {
                let args = args
                    .into_iter()
                    .map(|arg| self.expand_expression(arg, depth + 1))
                    .collect::<Result<Vec<_>, _>>()?;
                self.expand_builtin_macro(&name, args)?
            }
            other => other,
        })
    }

    fn expand_builtin_macro(
        &mut self,
        name: &str,
        args: Vec<Expression>,
    ) -> Result<Expression, MacroExpansionError> {
        self.register_expansion(name);

        match name {
            "identity" => {
                expect_arity(name, &args, 1)?;
                Ok(args.into_iter().next().expect("arity checked"))
            }
            "debug" => {
                expect_arity(name, &args, 1)?;
                Ok(Expression::Call {
                    callee: "console.log".to_string(),
                    args,
                })
            }
            "todo" => {
                expect_arity(name, &args, 0)?;
                Ok(Expression::Call {
                    callee: "throwTodo".to_string(),
                    args: Vec::new(),
                })
            }
            other => Err(MacroExpansionError::UnknownMacro(other.to_string())),
        }
    }
}

fn expect_arity(
    name: &str,
    args: &[Expression],
    expected: usize,
) -> Result<(), MacroExpansionError> {
    if args.len() == expected {
        Ok(())
    } else {
        Err(MacroExpansionError::Arity {
            name: name.to_string(),
            expected,
            actual: args.len(),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn expands_identity_macro() {
        let mut context = MacroExpansionContext::default();
        let program = Program {
            body: vec![Statement::Expression(Expression::MacroCall {
                name: "identity".to_string(),
                args: vec![Expression::Number(42.0)],
            })],
        };

        let expanded = context.expand_program(program).expect("macro expansion");

        assert_eq!(
            expanded.body,
            vec![Statement::Expression(Expression::Number(42.0))]
        );
        assert_eq!(context.expanded_macros(), vec!["identity"]);
    }
}
