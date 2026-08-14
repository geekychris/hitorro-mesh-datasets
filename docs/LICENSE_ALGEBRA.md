# License algebra

`LicenseAlgebra.combine(...)` computes the terms of a query result that
touches two or more datasets. It answers *"can this be redistributed, and
under what obligations?"* — not *"is this legal in your jurisdiction?"* That
still needs a lawyer.

## The five capabilities

| Capability | Meaning |
|------------|---------|
| `redistribution` | May we hand the data to a third party? |
| `commercialUse` | May downstream results be sold? |
| `attributionRequired` | Must the consumer preserve source credit? |
| `shareAlike` | Does a derivative inherit this same licence? |
| `modification` | May we transform the source records? |

## Combination rules

- `redistribution`, `commercialUse`, `modification` — **AND** across inputs.
  A single "no" turns the combined answer to "no".
- `attributionRequired` — **OR** across inputs. If any source requires
  credit, the consumer must credit all attributed sources.
- `shareAlike` — **OR** across inputs. A single share-alike licence in the
  mix drags the whole result under share-alike (subject to whether the
  result is a "derivative database" — ODbL specifically).

## Common combinations

| A | B | Result | Why |
|---|---|--------|-----|
| CC0 | CC-BY | Distributable, attribution required | CC-BY drags attribution in |
| CC-BY | CC-BY | Same as above | Both need credit |
| CC-BY | ODbL | Share-alike ⚠️ | ODbL forces share-alike as a database |
| Public domain | anything | Same as the "anything" | Public domain adds no obligation |
| CC-BY-NC | any commercial | **Non-commercial** ⚠️ | NC restriction wins |

## Roadmap

- Wire `LicenseAlgebra.combine` into the driver's query pipeline so every
  response envelope reports the combined result. Consumers get it for free
  instead of having to redo the calculation.
- Add a `WHERE LICENSE ALLOWS 'commercial redistribution'` planner predicate
  so queries can gate their own reachability.
- Expand the licence catalogue: ODC-BY, CDLA-permissive, CDLA-sharing,
  various government-agency licences.
