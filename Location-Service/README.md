# Location Service

A Spring Boot application that:
1. Serves a single-page HTML/JS UI.
2. Receives browser Geolocation coordinates (latitude + longitude).
3. Performs **reverse geocoding** via OpenStreetMap Nominatim (free, no API key).
4. Persists the full record to **PostgreSQL**.

---

## Architecture

```
Browser (index.html)
        │  click "Detect My Location"
        │  navigator.geolocation.getCurrentPosition()
        ▼
  POST /api/location  { latitude, longitude, accuracy }
        │
        ▼
  LocationController
        │
        ▼
  LocationService
        │  calls Nominatim reverse-geocoding
        ▼
  GeocodingService ──► https://nominatim.openstreetmap.org/reverse
        │
        ▼
  UserLocationRepository
        │
        ▼
  PostgreSQL  →  user_locations table
        │
        ▼
  LocationResponse (JSON)  ──► Browser displays address + map link
```

---

## Prerequisites

| Tool | Minimum version |
|------|----------------|
| Java | 17             |
| Maven | 3.8           |
| PostgreSQL | 14      |

---

## Setup

### 1. Create the database

```sql
CREATE DATABASE location_db;
```

### 2. Configure credentials

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/location_db
spring.datasource.username=postgres
spring.datasource.password=<your-password>
```

### 3. Run the app

```bash
mvn spring-boot:run
```

### 4. Open the UI

Visit [http://localhost:8080](http://localhost:8080) and click **Detect My Location**.

---

## API Reference

### POST /api/location

Save a location capture.

**Request body**
```json
{
  "latitude":  28.6139,
  "longitude": 77.2090,
  "accuracy":  15.0
}
```

**Response (201 Created)**
```json
{
  "id":        1,
  "latitude":  28.6139,
  "longitude": 77.2090,
  "accuracy":  15.0,
  "address":   "Connaught Place, New Delhi, Delhi 110001, India",
  "city":      "New Delhi",
  "country":   "India",
  "createdAt": "2026-10-25T10:30:00"
}
```

### GET /api/location

Returns all saved records, most-recent first.

---

## Verify in PostgreSQL

```sql
SELECT id, latitude, longitude, city, country, created_at
FROM user_locations
ORDER BY created_at DESC;
```

---

## Production checklist

- [ ] Add Spring Security to protect `/api/location` with authentication
- [ ] Associate records with an authenticated user rather than a bare IP
- [ ] Switch to HTTPS
- [ ] Add rate limiting (e.g. Bucket4j or a gateway)
- [ ] Define a data-retention policy and add a deletion endpoint
- [ ] Consider PostGIS if you need geographic queries (nearest shop, etc.)
- [ ] Replace Nominatim with Google Maps Geocoding API for higher accuracy
