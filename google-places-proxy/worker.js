/**
 * Rota Certa: Google Places proxy (Cloudflare Worker).
 * Secrets no Cloudflare: GOOGLE_PLACES_API_KEY e ROTA_APP_TOKEN.
 * Configure quotas de faturamento da API e proteja o acesso ao Worker.
 */
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname !== '/destinos') return new Response('Use /destinos?lat=-12.7&lon=-38.3', { status: 404 });
    if (request.method !== 'GET') return new Response('Method not allowed', { status: 405 });
    const auth = request.headers.get('Authorization') || '';
    if (!env.ROTA_APP_TOKEN || auth !== `Bearer ${env.ROTA_APP_TOKEN}`)
      return Response.json({ error: 'Unauthorized' }, { status: 401 });
    if (!env.GOOGLE_PLACES_API_KEY) return Response.json({ error: 'Google Places key missing' }, { status: 503 });
    const lat = Number(url.searchParams.get('lat'));
    const lon = Number(url.searchParams.get('lon'));
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180)
      return Response.json({ error: 'Invalid coordinates' }, { status: 400 });

    // Consultas complementares para obter mais de 20 destinos, se existirem na regiao.
    const searches = ['pontos turisticos para visitar', 'praias parques e natureza', 'museus mirantes e lugares historicos', 'cachoeiras passeios e atracoes'];
    const unique = new Map();
    let failed = 0;
    for (const term of searches) {
      const payload = {
        textQuery: term,
        languageCode: 'pt-BR',
        pageSize: 20,
        locationBias: {
          circle: { center: { latitude: lat, longitude: lon }, radius: 50000.0 }
        }
      };
      const response = await fetch('https://places.googleapis.com/v1/places:searchText', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Goog-Api-Key': env.GOOGLE_PLACES_API_KEY,
          'X-Goog-FieldMask': 'places.id,places.displayName,places.formattedAddress,places.location'
        },
        body: JSON.stringify(payload)
      });
      if (!response.ok) { failed++; continue; }
      const data = await response.json();
      for (const place of data.places || []) {
        if (place.location?.latitude == null || place.location?.longitude == null || !place.displayName?.text) continue;
        const id = place.id || `${place.displayName.text}|${place.location.latitude}`;
        if (!unique.has(id)) unique.set(id, {
          name: place.displayName.text,
          address: place.formattedAddress || '',
          lat: place.location.latitude,
          lon: place.location.longitude
        });
      }
      if (unique.size >= 45) break;
    }
    if (!unique.size && failed === searches.length)
      return Response.json({ error: 'Google Places unavailable; review billing and quotas' }, { status: 502 });
    return Response.json({ places: [...unique.values()].slice(0, 65), provider: 'Google Places' }, {
      headers: { 'Cache-Control': 'no-store' }
    });
  }
};
