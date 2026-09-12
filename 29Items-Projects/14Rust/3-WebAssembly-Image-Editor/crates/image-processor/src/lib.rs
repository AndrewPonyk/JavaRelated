mod error;
mod filters;
pub mod geometry;

use error::ImageError;
use wasm_bindgen::prelude::*;

#[wasm_bindgen]
pub struct ImageProcessor {
    width: u32,
    height: u32,
    pixels: Vec<u8>,
}

#[wasm_bindgen]
impl ImageProcessor {
    #[wasm_bindgen(constructor)]
    pub fn new(width: u32, height: u32, pixels: &[u8]) -> Result<ImageProcessor, JsValue> {
        geometry::validate_rgba(pixels, width, height).map_err(to_js_error)?;
        Ok(Self {
            width,
            height,
            pixels: pixels.to_vec(),
        })
    }

    #[wasm_bindgen(getter)]
    pub fn width(&self) -> u32 {
        self.width
    }

    #[wasm_bindgen(getter)]
    pub fn height(&self) -> u32 {
        self.height
    }

    /// Returns an owned copy so JavaScript never retains a view invalidated by WASM memory growth.
    pub fn pixels(&self) -> Vec<u8> {
        self.pixels.clone()
    }

    pub fn apply_filter(&mut self, name: &str, amount: f64) -> Result<Vec<u8>, JsValue> {
        if !amount.is_finite() || amount.fract() != 0.0 || !(-100.0..=100.0).contains(&amount) {
            return Err(to_js_error(ImageError::InvalidFilterAmount {
                filter: name.to_owned(),
                amount: amount as i32,
            }));
        }
        filters::apply_filter(
            &mut self.pixels,
            self.width,
            self.height,
            name,
            amount as i32,
        )
        .map_err(to_js_error)?;
        Ok(self.pixels())
    }

    pub fn crop(&mut self, x: u32, y: u32, width: u32, height: u32) -> Result<Vec<u8>, JsValue> {
        let cropped =
            geometry::crop_rgba(&self.pixels, self.width, self.height, x, y, width, height)
                .map_err(to_js_error)?;

        self.width = width;
        self.height = height;
        self.pixels = cropped;
        Ok(self.pixels())
    }
}

fn to_js_error(error: ImageError) -> JsValue {
    js_sys::Error::new(&error.to_string()).into()
}
