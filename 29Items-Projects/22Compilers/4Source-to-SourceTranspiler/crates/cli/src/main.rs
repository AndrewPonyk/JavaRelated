use anyhow::{Context, Result};
use transpiler_emitter::JavaScriptEmitter;
use transpiler_macros::MacroExpansionContext;
use transpiler_transforms::TransformPipeline;

fn main() -> Result<()> {
    let source_path = std::env::args()
        .nth(1)
        .context("usage: transpiler-cli <source-file>")?;
    let source = std::fs::read_to_string(&source_path)
        .with_context(|| format!("failed to read source file: {source_path}"))?;

    let program = transpiler_parser::parse_program(&source)?;
    let mut macros = MacroExpansionContext::default();
    let program = macros.expand_program(program)?;
    let program = TransformPipeline::optimized().run(program);
    let emitted = JavaScriptEmitter::default().emit_program(&program);

    print!("{}", emitted.javascript);
    Ok(())
}
