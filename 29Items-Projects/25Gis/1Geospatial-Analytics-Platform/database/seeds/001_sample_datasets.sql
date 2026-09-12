INSERT INTO datasets (name, description, source_type, metadata_json)
VALUES
    (
        'Kyiv Land Use Baseline',
        'Sample vector dataset for local development.',
        'vector',
        '{"srid": 4326, "source": "seed"}'::jsonb
    )
ON CONFLICT DO NOTHING;

WITH sample_dataset AS (
    SELECT id FROM datasets WHERE name = 'Kyiv Land Use Baseline' LIMIT 1
)
INSERT INTO dataset_features (dataset_id, properties, geom)
SELECT
    sample_dataset.id,
    '{"land_use": "urban", "name": "Central business district"}'::jsonb,
    ST_SetSRID(ST_GeomFromText('POINT(30.5234 50.4501)'), 4326)
FROM sample_dataset
UNION ALL
SELECT
    sample_dataset.id,
    '{"land_use": "water", "name": "Dnipro river sample"}'::jsonb,
    ST_SetSRID(ST_GeomFromText('POINT(30.5680 50.4590)'), 4326)
FROM sample_dataset
ON CONFLICT DO NOTHING;

WITH sample_dataset AS (
    SELECT id FROM datasets WHERE name = 'Kyiv Land Use Baseline' LIMIT 1
)
INSERT INTO layers (dataset_id, name, layer_type, style, is_public)
SELECT sample_dataset.id, 'Kyiv baseline WMS', 'wms', 'default', true
FROM sample_dataset
ON CONFLICT DO NOTHING;
