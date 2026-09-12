use transpiler_core::{CompileOptions, Program};

#[test]
fn default_compile_options_target_modern_javascript() {
    let options = CompileOptions::default();

    assert_eq!(options.target, "es2022");
    assert!(options.optimize);
    assert!(options.source_maps);
}

#[test]
fn empty_program_is_valid_ast_root() {
    let program = Program::default();

    assert!(program.body.is_empty());
}
