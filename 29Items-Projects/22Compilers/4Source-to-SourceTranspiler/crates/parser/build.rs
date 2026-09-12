fn main() {
    lalrpop::process_root().expect("failed to generate parser from LALRPOP grammar");
}
