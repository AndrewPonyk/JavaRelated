use crate::error::ImageError;

pub fn expected_rgba_len(width: u32, height: u32) -> Result<usize, ImageError> {
    if width == 0 || height == 0 {
        return Err(ImageError::EmptyDimensions);
    }

    let pixels = width
        .checked_mul(height)
        .ok_or(ImageError::DimensionOverflow)?;
    let bytes = pixels.checked_mul(4).ok_or(ImageError::DimensionOverflow)?;
    usize::try_from(bytes).map_err(|_| ImageError::DimensionOverflow)
}

pub fn validate_rgba(pixels: &[u8], width: u32, height: u32) -> Result<(), ImageError> {
    let expected = expected_rgba_len(width, height)?;
    if pixels.len() != expected {
        return Err(ImageError::InvalidBufferLength {
            expected,
            actual: pixels.len(),
        });
    }
    Ok(())
}

pub fn crop_rgba(
    pixels: &[u8],
    source_width: u32,
    source_height: u32,
    x: u32,
    y: u32,
    width: u32,
    height: u32,
) -> Result<Vec<u8>, ImageError> {
    validate_rgba(pixels, source_width, source_height)?;
    if width == 0 || height == 0 {
        return Err(ImageError::InvalidCrop);
    }

    let right = x.checked_add(width).ok_or(ImageError::CropOutOfBounds)?;
    let bottom = y.checked_add(height).ok_or(ImageError::CropOutOfBounds)?;
    if right > source_width || bottom > source_height {
        return Err(ImageError::CropOutOfBounds);
    }

    let output_len = expected_rgba_len(width, height)?;
    let mut output = Vec::with_capacity(output_len);
    let source_stride = usize::try_from(source_width)
        .map_err(|_| ImageError::DimensionOverflow)?
        .checked_mul(4)
        .ok_or(ImageError::DimensionOverflow)?;
    let crop_stride = usize::try_from(width)
        .map_err(|_| ImageError::DimensionOverflow)?
        .checked_mul(4)
        .ok_or(ImageError::DimensionOverflow)?;
    let x_offset = usize::try_from(x)
        .map_err(|_| ImageError::DimensionOverflow)?
        .checked_mul(4)
        .ok_or(ImageError::DimensionOverflow)?;

    for row in y..bottom {
        let row_start = usize::try_from(row)
            .map_err(|_| ImageError::DimensionOverflow)?
            .checked_mul(source_stride)
            .and_then(|offset| offset.checked_add(x_offset))
            .ok_or(ImageError::DimensionOverflow)?;
        let row_end = row_start
            .checked_add(crop_stride)
            .ok_or(ImageError::DimensionOverflow)?;
        output.extend_from_slice(&pixels[row_start..row_end]);
    }

    debug_assert_eq!(output.len(), output_len);
    Ok(output)
}
