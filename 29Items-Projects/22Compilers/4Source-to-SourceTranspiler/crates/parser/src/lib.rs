use thiserror::Error;
use transpiler_core::{Program, SourceSpan};

use lalrpop_util::lalrpop_mod;

lalrpop_mod!(pub language);

#[derive(Debug, Error)]
pub enum ParseFailure {
    #[error("{message}")]
    InvalidSyntax {
        message: String,
        span: Option<SourceSpan>,
    },
}

pub fn parse_program(source: &str) -> Result<Program, ParseFailure> {
    language::ProgramParser::new()
        .parse(source)
        .map_err(|error| {
            let message = error.to_string();
            let span = match &error {
                lalrpop_util::ParseError::InvalidToken { location } => Some(SourceSpan {
                    start: *location,
                    end: *location + 1,
                }),
                lalrpop_util::ParseError::UnrecognizedEof { location, .. } => Some(SourceSpan {
                    start: *location,
                    end: *location,
                }),
                lalrpop_util::ParseError::UnrecognizedToken { token, .. } => Some(SourceSpan {
                    start: token.0,
                    end: token.2,
                }),
                lalrpop_util::ParseError::ExtraToken { token } => Some(SourceSpan {
                    start: token.0,
                    end: token.2,
                }),
                lalrpop_util::ParseError::User { .. } => None,
            };

            ParseFailure::InvalidSyntax { message, span }
        })
}

impl ParseFailure {
    pub fn span(&self) -> Option<SourceSpan> {
        match self {
            Self::InvalidSyntax { span, .. } => *span,
        }
    }
}
