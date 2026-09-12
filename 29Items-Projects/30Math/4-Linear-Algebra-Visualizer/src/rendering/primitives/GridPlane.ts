import * as THREE from 'three';

/**
 * Grid / axes line geometry for the 2D plane (drawn in the z = 0 plane,
 * viewed through an orthographic camera).
 */

export function createGrid(
  halfExtent = 20,
  step = 1,
  color: THREE.ColorRepresentation = 0x334155,
  opacity = 1,
): THREE.LineSegments {
  const positions: number[] = [];
  for (let i = -halfExtent; i <= halfExtent; i += step) {
    // Vertical line x = i, horizontal line y = i.
    positions.push(i, -halfExtent, 0, i, halfExtent, 0);
    positions.push(-halfExtent, i, 0, halfExtent, i, 0);
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  const material = new THREE.LineBasicMaterial({
    color,
    transparent: opacity < 1,
    opacity,
  });
  return new THREE.LineSegments(geometry, material);
}

/** Emphasized x/y axes drawn over the static grid. */
export function createAxes(
  halfExtent = 20,
  color: THREE.ColorRepresentation = 0x64748b,
): THREE.LineSegments {
  const positions = [
    -halfExtent,
    0,
    0.001,
    halfExtent,
    0,
    0.001,
    0,
    -halfExtent,
    0.001,
    0,
    halfExtent,
    0.001,
  ];
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  return new THREE.LineSegments(geometry, new THREE.LineBasicMaterial({ color }));
}
