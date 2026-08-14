# Catalog

The canonical list of datasets shipped with (or planned for) this module.
This file is hand-maintained and matches the `BUNDLED` array in
`ManifestLoader.java`. When you add a manifest, update both.

## Shipped

| id | rows | licence | source |
|----|-----:|---------|--------|
| `geonames-cities15000` | ~26 000 | CC-BY-4.0 | https://download.geonames.org/export/dump/cities15000.zip |
| `geonames-country-info` | ~250 | CC-BY-4.0 | https://download.geonames.org/export/dump/countryInfo.txt |
| `natural-earth-countries` | ~258 | Public domain | https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson |
| `wikidata-cities` | ~2 200 | CC0 | https://query.wikidata.org/sparql (P31=Q515, P1082>100k) |
| `noaa-ghcnd-stations` | ~132 500 | Public domain | https://www.ncei.noaa.gov/pub/data/ghcn/daily/ghcnd-stations.txt |
| `wikidata-countries` | ~217 | CC0 | https://query.wikidata.org/sparql (Q3624078 or Q6256, with P297/P298/P299/P901/P2082) |
| `worldbank-indicators` | ~213 | CC-BY-4.0 | https://api.worldbank.org/v2/country/all/indicator/{code}?format=json (8 indicators) |
| `usgs-earthquakes` | ~11 000 | Public domain | https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_month.geojson |

## Planned (see ROADMAP.md for detail)

- `geonames-alternate-names-v2`
- `natural-earth-admin1`
- `natural-earth-rivers-lakes`
- `wikidata-cities-with-labels-mul` (~2 k rows × ~10 languages — enlarges wikidata-cities with multilingual labels)
- `wikidata-countries` (~200 rows — country-level cross-reference table)
- `wikidata-full` (deferred until dump strategy is settled; SPARQL cap is ~10 M triples per query)
- `us-census-acs5`
- `noaa-ghcnd-normals`
- `owid-country-indicators`
- `openalex-works`
- `crossref-works`
- `overture-places`
- `overture-buildings`
- `osm-places`
- `worldbank-indicators`
- `usgs-earthquakes`
