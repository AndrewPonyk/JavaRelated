use crate::error::ImageError;

pub(crate) fn apply_filter(
    pixels: &mut [u8],
    width: u32,
    height: u32,
    name: &str,
    amount: i32,
) -> Result<(), ImageError> {
    crate::geometry::validate_rgba(pixels, width, height)?;
    match name {
        "grayscale" if amount == 0 => grayscale(pixels),
        "invert" if amount == 0 => invert(pixels),
        "sepia" if amount == 0 => sepia(pixels),
        "brightness" if (-100..=100).contains(&amount) => brightness(pixels, amount),
        "contrast" if (-100..=100).contains(&amount) => contrast(pixels, amount),
        "saturation" if (-100..=100).contains(&amount) => saturation(pixels, amount),
        "blur" if (1..=8).contains(&amount) => blur(pixels, width, height, amount as u32),
        "sharpen" if (1..=100).contains(&amount) => sharpen(pixels, width, height, amount),
        "grayscale" | "invert" | "sepia" | "brightness" | "contrast" | "saturation" | "blur"
        | "sharpen" => {
            return Err(ImageError::InvalidFilterAmount {
                filter: name.to_owned(),
                amount,
            });
        }
        _ => return Err(ImageError::UnsupportedFilter(name.to_owned())),
    }
    Ok(())
}

fn grayscale(pixels: &mut [u8]) {
    for pixel in pixels.chunks_exact_mut(4) {
        let luminance = ((77 * u16::from(pixel[0])
            + 150 * u16::from(pixel[1])
            + 29 * u16::from(pixel[2]))
            >> 8) as u8;
        pixel[0] = luminance;
        pixel[1] = luminance;
        pixel[2] = luminance;
    }
}

fn invert(pixels: &mut [u8]) {
    for pixel in pixels.chunks_exact_mut(4) {
        pixel[0] = 255 - pixel[0];
        pixel[1] = 255 - pixel[1];
        pixel[2] = 255 - pixel[2];
    }
}

fn sepia(pixels: &mut [u8]) {
    for pixel in pixels.chunks_exact_mut(4) {
        let red = u32::from(pixel[0]);
        let green = u32::from(pixel[1]);
        let blue = u32::from(pixel[2]);
        pixel[0] = ((393 * red + 769 * green + 189 * blue) / 1000).min(255) as u8;
        pixel[1] = ((349 * red + 686 * green + 168 * blue) / 1000).min(255) as u8;
        pixel[2] = ((272 * red + 534 * green + 131 * blue) / 1000).min(255) as u8;
    }
}

fn brightness(pixels: &mut [u8], amount: i32) {
    for pixel in pixels.chunks_exact_mut(4) {
        for channel in &mut pixel[..3] {
            *channel = clamp(i32::from(*channel) + amount);
        }
    }
}

fn contrast(pixels: &mut [u8], amount: i32) {
    let factor = 259.0 * (f64::from(amount) + 255.0) / (255.0 * (259.0 - f64::from(amount)));
    for pixel in pixels.chunks_exact_mut(4) {
        for channel in &mut pixel[..3] {
            *channel = clamp((factor * (f64::from(*channel) - 128.0) + 128.0).round() as i32);
        }
    }
}

fn saturation(pixels: &mut [u8], amount: i32) {
    let factor = 1.0 + f64::from(amount) / 100.0;
    for pixel in pixels.chunks_exact_mut(4) {
        let luminance =
            0.299 * f64::from(pixel[0]) + 0.587 * f64::from(pixel[1]) + 0.114 * f64::from(pixel[2]);
        for channel in &mut pixel[..3] {
            *channel =
                clamp((luminance + (f64::from(*channel) - luminance) * factor).round() as i32);
        }
    }
}

fn blur(pixels: &mut [u8], width: u32, height: u32, radius: u32) {
    let source = pixels.to_vec();
    for y in 0..height {
        for x in 0..width {
            let x_start = x.saturating_sub(radius);
            let x_end = x.saturating_add(radius).min(width - 1);
            let y_start = y.saturating_sub(radius);
            let y_end = y.saturating_add(radius).min(height - 1);
            let count = (x_end - x_start + 1) * (y_end - y_start + 1);
            let destination = pixel_offset(width, x, y);
            for channel in 0..3 {
                let mut sum = 0u32;
                for sample_y in y_start..=y_end {
                    for sample_x in x_start..=x_end {
                        sum += u32::from(source[pixel_offset(width, sample_x, sample_y) + channel]);
                    }
                }
                pixels[destination + channel] = (sum / count) as u8;
            }
        }
    }
}

fn sharpen(pixels: &mut [u8], width: u32, height: u32, amount: i32) {
    let source = pixels.to_vec();
    let mut softened = source.clone();
    blur(&mut softened, width, height, 1);
    let strength = f64::from(amount) / 100.0;
    for (index, pixel) in pixels.chunks_exact_mut(4).enumerate() {
        let offset = index * 4;
        for channel in 0..3 {
            let original = f64::from(source[offset + channel]);
            let blurred = f64::from(softened[offset + channel]);
            pixel[channel] = clamp((original + (original - blurred) * strength).round() as i32);
        }
    }
}

fn pixel_offset(width: u32, x: u32, y: u32) -> usize {
    ((y * width + x) * 4) as usize
}

fn clamp(value: i32) -> u8 {
    value.clamp(0, 255) as u8
}

#[cfg(test)]
mod tests {
    use super::*;
    use proptest::prelude::*;

    #[test]
    fn filters_preserve_alpha() {
        let mut pixels = vec![10, 20, 30, 47];
        for (name, amount) in [
            ("grayscale", 0),
            ("invert", 0),
            ("sepia", 0),
            ("brightness", 10),
            ("contrast", 10),
            ("saturation", 10),
            ("blur", 1),
            ("sharpen", 10),
        ] {
            apply_filter(&mut pixels, 1, 1, name, amount).unwrap();
            assert_eq!(pixels[3], 47);
        }
    }

    #[test]
    fn rejects_invalid_filter_configuration() {
        assert!(apply_filter(&mut [0, 0, 0, 255], 1, 1, "magic", 0).is_err());
        assert!(apply_filter(&mut [0, 0, 0, 255], 1, 1, "blur", 0).is_err());
    }

    #[test]
    fn rejects_inconsistent_dimensions_before_filtering() {
        assert!(apply_filter(&mut [0, 0, 0, 255], 0, 1, "invert", 0).is_err());
        assert!(apply_filter(&mut [0, 0, 0, 255], 2, 1, "blur", 1).is_err());
    }

    #[test]
    fn blur_averages_neighbouring_pixels() {
        let mut pixels = vec![0, 0, 0, 255, 255, 0, 0, 255];
        blur(&mut pixels, 2, 1, 1);
        assert_eq!(pixels, vec![127, 0, 0, 255, 127, 0, 0, 255]);
    }

    proptest! {
        #[test]
        fn colour_channels_remain_bounded(red in any::<u8>(), green in any::<u8>(), blue in any::<u8>(), amount in -100i32..101) {
            let mut pixels = vec![red, green, blue, 200];
            apply_filter(&mut pixels, 1, 1, "brightness", amount).unwrap();
            prop_assert_eq!(pixels.len(), 4);
            prop_assert_eq!(pixels[3], 200);
        }
    }
}
