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
| `owid-co2-latest` | ~215 | CC-BY-4.0 | https://github.com/owid/co2-data/raw/master/owid-co2-data.csv |
| `wikipedia-pageviews` | 1 000 | CC-BY-SA-3.0 | https://wikimedia.org/api/rest_v1/metrics/pageviews/top/en.wikipedia/all-access/{Y}/{M}/{D} |
| `osm-airports` | ~1 400 | ODbL-1.0 | https://overpass-api.de/api/interpreter (aeroway=aerodrome, iata=*) |
| `wikidata-city-sitelinks` | ~1 500 | CC0 | https://query.wikidata.org/sparql (Q515, P1082>100k, schema:isPartOf en.wikipedia) |
| `openalex-institutions` | ~400 | CC0 | https://api.openalex.org/institutions?per-page=200&sort=cited_by_count:desc |
| `coingecko-crypto` | 100 | CoinGecko free-tier | https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100 |
| `github-top-repos` | 100 | CC-BY-4.0 | https://api.github.com/search/repositories?q=stars:>100000&sort=stars&order=desc |
| `huggingface-top-models` | 100 | HF public API | https://huggingface.co/api/models?sort=downloads&direction=-1&limit=100 |
| `nasa-apod` | 30 | Public domain | https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY&count=30 |
| `retro-computers` | 40 | CC-BY-SA-4.0 | curated JSON in src/main/resources/data/retro-computers.json |
| `pypi-top-packages` | 200 | CC0-1.0 | https://hugovk.github.io/top-pypi-packages/top-pypi-packages.min.json |

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
