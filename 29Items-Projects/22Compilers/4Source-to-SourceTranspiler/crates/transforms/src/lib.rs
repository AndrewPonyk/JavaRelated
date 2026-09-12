use transpiler_core::{BinaryOperator, Expression, Program, Statement};

pub trait TransformPass {
    fn name(&self) -> &'static str;
    fn run(&self, program: Program) -> Program;
}

#[derive(Default)]
pub struct TransformPipeline {
    passes: Vec<Box<dyn TransformPass + Send + Sync>>,
}

impl TransformPipeline {
    pub fn optimized() -> Self {
        Self {
            passes: vec![Box::new(ConstantFoldPass), Box::new(DeadCodePass)],
        }
    }

    pub fn run(&self, mut program: Program) -> Program {
        for pass in &self.passes {
            program = pass.run(program);
        }
        program
    }

    pub fn pass_names(&self) -> Vec<&'static str> {
        self.passes.iter().map(|pass| pass.name()).collect()
    }
}

pub struct ConstantFoldPass;

impl TransformPass for ConstantFoldPass {
    fn name(&self) -> &'static str {
        "constant-fold"
    }

    fn run(&self, mut program: Program) -> Program {
        program.body = program.body.into_iter().map(fold_statement).collect();
        program
    }
}

fn fold_statement(statement: Statement) -> Statement {
    match statement {
        Statement::Variable {
            mutable,
            name,
            type_annotation,
            value,
        } => Statement::Variable {
            mutable,
            name,
            type_annotation,
            value: fold_expression(value),
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
            body: body.into_iter().map(fold_statement).collect(),
        },
        Statement::Return(value) => Statement::Return(value.map(fold_expression)),
        Statement::Expression(expression) => Statement::Expression(fold_expression(expression)),
        Statement::Empty => Statement::Empty,
    }
}

fn fold_expression(expression: Expression) -> Expression {
    match expression {
        Expression::Binary { op, left, right } => {
            let left = fold_expression(*left);
            let right = fold_expression(*right);

            match (op, left, right) {
                (BinaryOperator::Add, Expression::Number(a), Expression::Number(b)) => {
                    Expression::Number(a + b)
                }
                (BinaryOperator::Subtract, Expression::Number(a), Expression::Number(b)) => {
                    Expression::Number(a - b)
                }
                (BinaryOperator::Multiply, Expression::Number(a), Expression::Number(b)) => {
                    Expression::Number(a * b)
                }
                (BinaryOperator::Divide, Expression::Number(a), Expression::Number(b))
                    if b != 0.0 =>
                {
                    Expression::Number(a / b)
                }
                (op, left, right) => Expression::Binary {
                    op,
                    left: Box::new(left),
                    right: Box::new(right),
                },
            }
        }
        Expression::Call { callee, args } => Expression::Call {
            callee,
            args: args.into_iter().map(fold_expression).collect(),
        },
        Expression::MacroCall { name, args } => Expression::MacroCall {
            name,
            args: args.into_iter().map(fold_expression).collect(),
        },
        other => other,
    }
}

pub struct DeadCodePass;

impl TransformPass for DeadCodePass {
    fn name(&self) -> &'static str {
        "dead-code"
    }

    fn run(&self, mut program: Program) -> Program {
        program.body = trim_after_return(program.body);
        program
    }
}

fn trim_after_return(statements: Vec<Statement>) -> Vec<Statement> {
    let mut result = Vec::new();

    for statement in statements {
        let is_return = matches!(statement, Statement::Return(_));
        let statement = match statement {
            Statement::Function {
                name,
                params,
                return_type,
                body,
            } => Statement::Function {
                name,
                params,
                return_type,
                body: trim_after_return(body),
            },
            other => other,
        };
        result.push(statement);

        if is_return {
            break;
        }
    }

    result
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn optimized_pipeline_has_deterministic_pass_order() {
        assert_eq!(
            TransformPipeline::optimized().pass_names(),
            vec!["constant-fold", "dead-code"]
        );
    }

    #[test]
    fn folds_arithmetic_constants() {
        let program = Program {
            body: vec![Statement::Expression(Expression::Binary {
                op: BinaryOperator::Multiply,
                left: Box::new(Expression::Number(6.0)),
                right: Box::new(Expression::Number(7.0)),
            })],
        };

        let transformed = TransformPipeline::optimized().run(program);

        assert_eq!(
            transformed.body,
            vec![Statement::Expression(Expression::Number(42.0))]
        );
    }
}
