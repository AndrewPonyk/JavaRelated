use transpiler_core::{Expression, Statement};

#[test]
fn parses_typed_function() {
    let program = transpiler_parser::parse_program(
        r#"
        function add(a: number, b: number): number {
            return a + b;
        }
        const answer: number = add(40, 2);
        "#,
    )
    .expect("valid program");

    assert_eq!(program.body.len(), 2);
    assert!(matches!(program.body[0], Statement::Function { .. }));
}

#[test]
fn parses_macro_call_expression() {
    let program = transpiler_parser::parse_program("let answer = identity!(42);")
        .expect("valid macro call");

    let Statement::Variable { value, .. } = &program.body[0] else {
        panic!("expected variable statement");
    };

    assert!(matches!(value, Expression::MacroCall { .. }));
}

#[test]
fn reports_invalid_syntax_with_span() {
    let error = transpiler_parser::parse_program("let = ;").expect_err("invalid syntax");

    assert!(error.span().is_some());
}
