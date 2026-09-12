use transpiler_emitter::JavaScriptEmitter;
use transpiler_macros::MacroExpansionContext;
use transpiler_transforms::TransformPipeline;

#[test]
fn compiles_parser_macro_transform_emitter_pipeline() {
    let program = transpiler_parser::parse_program(
        r#"
        function add(a: number, b: number): number {
            return a + b;
        }
        const answer: number = identity!(40 + 2);
        "#,
    )
    .expect("parse");
    let mut macros = MacroExpansionContext::default();
    let expanded = macros.expand_program(program).expect("macro expansion");
    let transformed = TransformPipeline::optimized().run(expanded);
    let emitted = JavaScriptEmitter::default().emit_program(&transformed);

    assert!(emitted.javascript.contains("function add(a, b)"));
    assert!(emitted.javascript.contains("const answer = 42;"));
    assert!(emitted.source_map.is_some());
}
