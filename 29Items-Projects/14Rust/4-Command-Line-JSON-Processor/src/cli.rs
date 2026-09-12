use clap::Parser;
use std::path::PathBuf;

#[derive(Parser, Debug)]
#[command(author, version, about, long_about = None)]
pub struct Cli {
    /// Optional input file (reads from stdin if not provided or if "-" is passed)
    #[arg(short, long, value_name = "FILE")]
    pub input: Option<PathBuf>,

    /// Filter expression (jq-like syntax: "." or ".key" or ".key == 1")
    #[arg(short, long, value_name = "EXPR", default_value = ".")]
    pub filter: String,

    /// Turn debugging information on
    #[arg(short, long, action = clap::ArgAction::Count)]
    pub debug: u8,

    /// Run in schema inference mode instead of filtering
    #[arg(short, long)]
    pub schema: bool,

    /// Pretty print output JSON
    #[arg(short, long)]
    pub pretty: bool,
}
