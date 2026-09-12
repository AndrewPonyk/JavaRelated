use transpiler_emitter::JavaScriptEmitter;
use transpiler_macros::MacroExpansionContext;
use transpiler_transforms::TransformPipeline;
use wasm_bindgen::prelude::*;

#[wasm_bindgen]
pub fn transpile(source: &str) -> Result<String, JsValue> {
    let program = transpiler_parser::parse_program(source)
        .map_err(|error| JsValue::from_str(&error.to_string()))?;
    let mut macros = MacroExpansionContext::default();
    let expanded = macros
        .expand_program(program)
        .map_err(|error| JsValue::from_str(&error.to_string()))?;
    let optimized = TransformPipeline::optimized().run(expanded);
    let emitted = JavaScriptEmitter::default().emit_program(&optimized);

    serde_json::to_string(&serde_json::json!({
        "javascript": emitted.javascript,
        "sourceMap": emitted.source_map,
    }))
    .map_err(|error| JsValue::from_str(&error.to_string()))
}
