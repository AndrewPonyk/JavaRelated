use std::path::Path;
use tokio::fs::File;
use tokio::io::{stdin, BufReader};

/// Helper to get an async buffered reader from either a file or stdin
pub async fn get_reader<'a>(
    input_path: &'a Path,
) -> std::io::Result<Box<dyn tokio::io::AsyncBufRead + Unpin + Send + 'a>> {
    if input_path.to_string_lossy() == "-" {
        // Read from stdin
        let reader = BufReader::new(stdin());
        Ok(Box::new(reader))
    } else {
        // Read from file
        let file = File::open(input_path).await?;
        let reader = BufReader::new(file);
        Ok(Box::new(reader))
    }
}
