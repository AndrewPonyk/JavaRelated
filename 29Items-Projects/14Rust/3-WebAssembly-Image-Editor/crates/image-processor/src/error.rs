use std::fmt::{Display, Formatter};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ImageError {
    EmptyDimensions,
    DimensionOverflow,
    InvalidBufferLength { expected: usize, actual: usize },
    InvalidCrop,
    CropOutOfBounds,
    UnsupportedFilter(String),
    InvalidFilterAmount { filter: String, amount: i32 },
}

impl Display for ImageError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::EmptyDimensions => formatter.write_str("image width and height must be positive"),
            Self::DimensionOverflow => formatter.write_str("image dimensions are too large"),
            Self::InvalidBufferLength { expected, actual } => write!(
                formatter,
                "invalid RGBA buffer length: expected {expected} bytes, received {actual}"
            ),
            Self::InvalidCrop => formatter.write_str("crop width and height must be positive"),
            Self::CropOutOfBounds => formatter.write_str("crop rectangle is outside the image"),
            Self::UnsupportedFilter(name) => write!(formatter, "unsupported filter: {name}"),
            Self::InvalidFilterAmount { filter, amount } => {
                write!(formatter, "invalid amount {amount} for filter: {filter}")
            }
        }
    }
}

impl std::error::Error for ImageError {}
