use image_processor::geometry::{crop_rgba, expected_rgba_len, validate_rgba};

#[test]
fn validates_rgba_dimensions() {
    assert_eq!(expected_rgba_len(2, 3).unwrap(), 24);
    assert!(validate_rgba(&[0; 24], 2, 3).is_ok());
    assert!(validate_rgba(&[0; 23], 2, 3).is_err());
}

#[test]
fn crops_rows_without_mixing_pixels() {
    // Four distinguishable pixels in a 2×2 image.
    let pixels = [1, 0, 0, 255, 2, 0, 0, 255, 3, 0, 0, 255, 4, 0, 0, 255];
    let cropped = crop_rgba(&pixels, 2, 2, 1, 0, 1, 2).unwrap();
    assert_eq!(cropped, [2, 0, 0, 255, 4, 0, 0, 255]);
}

#[test]
fn rejects_out_of_bounds_crop() {
    assert!(crop_rgba(&[0; 16], 2, 2, 1, 1, 2, 1).is_err());
}
