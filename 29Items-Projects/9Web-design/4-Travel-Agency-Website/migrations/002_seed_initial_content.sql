INSERT INTO tours
  (slug, title, region, region_label, duration_days, price_cents, difficulty, featured, summary, image_url)
VALUES
  (
    'amalfi-rail-coast',
    'Amalfi Rail and Coast',
    'europe',
    'Europe',
    8,
    340000,
    'easy',
    TRUE,
    'Rome, Naples, and Amalfi with private transfers and coastal day trips.',
    'https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=900&q=80'
  ),
  (
    'kyoto-culture-route',
    'Kyoto Culture Route',
    'asia',
    'Asia',
    9,
    420000,
    'moderate',
    TRUE,
    'Temples, ryokan stays, tea experiences, and guided neighborhood walks.',
    'https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?auto=format&fit=crop&w=900&q=80'
  ),
  (
    'patagonia-trek',
    'Patagonia Trek',
    'americas',
    'Americas',
    12,
    580000,
    'active',
    TRUE,
    'A guided active itinerary through glaciers, lakes, and mountain lodges.',
    'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=900&q=80'
  ),
  (
    'morocco-desert-cities',
    'Morocco Desert and Cities',
    'africa',
    'Africa',
    10,
    310000,
    'moderate',
    FALSE,
    'Marrakesh, Fes, Atlas villages, and a Sahara camp experience.',
    'https://images.unsplash.com/photo-1518548419970-58e3b4079ab2?auto=format&fit=crop&w=900&q=80'
  ),
  (
    'iceland-ring-road',
    'Iceland Ring Road',
    'europe',
    'Europe',
    7,
    390000,
    'easy',
    FALSE,
    'Waterfalls, black sand beaches, glacier lagoons, and thermal pools.',
    'https://images.unsplash.com/photo-1504829857797-ddff29c27927?auto=format&fit=crop&w=900&q=80'
  ),
  (
    'thailand-island-reset',
    'Thailand Island Reset',
    'asia',
    'Asia',
    6,
    240000,
    'easy',
    FALSE,
    'A relaxed beach itinerary with food tours and optional snorkeling days.',
    'https://images.unsplash.com/photo-1508009603885-50cf7c579365?auto=format&fit=crop&w=900&q=80'
  )
ON CONFLICT (slug) DO UPDATE SET
  title = EXCLUDED.title,
  region = EXCLUDED.region,
  region_label = EXCLUDED.region_label,
  duration_days = EXCLUDED.duration_days,
  price_cents = EXCLUDED.price_cents,
  difficulty = EXCLUDED.difficulty,
  featured = EXCLUDED.featured,
  summary = EXCLUDED.summary,
  image_url = EXCLUDED.image_url,
  updated_at = NOW();

INSERT INTO destinations
  (slug, name, region, latitude, longitude, description, image_url)
VALUES
  (
    'amalfi-coast',
    'Amalfi Coast',
    'Europe',
    40.633300,
    14.602900,
    'Coastal villages, food experiences, and rail-connected city stays.',
    'https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=900&q=80'
  ),
  (
    'kyoto',
    'Kyoto',
    'Asia',
    35.011600,
    135.768100,
    'Cultural routes, gardens, temples, and traditional inns.',
    'https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?auto=format&fit=crop&w=900&q=80'
  ),
  (
    'patagonia',
    'Patagonia',
    'Americas',
    -49.331500,
    -72.886300,
    'Active trekking routes with glacier and mountain lodge access.',
    'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=900&q=80'
  ),
  (
    'marrakesh',
    'Marrakesh',
    'Africa',
    31.629500,
    -7.981100,
    'Historic medinas, desert extensions, and Atlas Mountain day trips.',
    'https://images.unsplash.com/photo-1518548419970-58e3b4079ab2?auto=format&fit=crop&w=900&q=80'
  )
ON CONFLICT (slug) DO UPDATE SET
  name = EXCLUDED.name,
  region = EXCLUDED.region,
  latitude = EXCLUDED.latitude,
  longitude = EXCLUDED.longitude,
  description = EXCLUDED.description,
  image_url = EXCLUDED.image_url,
  updated_at = NOW();
