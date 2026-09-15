# Team CS

## Team Introduction

Team CS is responsible for designing and implementing the **Item System** for the Space Invaders IC-PBL project. Our goal is to add a flexible, extensible item system (pickups, effects, and inventory-related state) that integrates cleanly with the existing gameplay loop and with the systems owned by other teams (Currency, HUD, Visual Effects, Sound).

### Member Roles

| Member | GitHub | Role |
| --- | --- | --- |
| KimHyunChan | [whitson1117](https://github.com/whitson1117) | Team lead / Cross-team integration (Currency, HUD hooks) |
| HanJaeHyuk | [doha1208](https://github.com/doha1208) | Git management (branching strategy, PR review & merge coordination) |
| SonDongYeol | [playlistSDY](https://github.com/playlistSDY) | Item data model & architecture |
| Choseonggil | [ancho040220](https://github.com/ancho040220) | Item spawn & drop logic |
| Choi Jian | [choichoi10](https://github.com/choichoi10) | Item effect / power-up application logic |
| Choigeonhee | [choigeonhee2025](https://github.com/choigeonhee2025) | Inventory management |
| Kim JiHo | [GTMBB](https://github.com/GTMBB) | Item rarity / tier system |
| ChoiGiMin | [zPHf25N](https://github.com/zPHf25N) | Save/load & persistence integration |
| KimHanGyeol | [han31415920512](https://github.com/han31415920512) | Testing & QA |

> Roles above are a proposed draft — update to match the team's actual agreement before submitting.

## Team Requirements

Team CS owns the **Item System** requirement: items that spawn during gameplay, can be collected by the player, and modify gameplay state (player stats, score, currency, or ship behavior) when used or applied.

## Detailed Requirements

1. **Item Drop System** — Destroying the red (special) ship guarantees an item drop. Destroying a regular alien drops an item probabilistically. Drop probability values are to be finalized in consultation with the Level Design team.
2. **Item Pickup & Storage** — A dropped item is collected when the player's ship touches it. Passive items apply their effect immediately on pickup. Active items are stored and only applied when the player presses a dedicated "use item" key (Mario Kart item-box style).
3. **Active/Passive Item Classification** — Define a data structure that classifies each item as either Active (stored, triggered by key press) or Passive (instant effect on pickup), so other systems can query an item's category without depending on Item System internals.
4. **Life Item** — Grants an extra life. Whether it is stored for later use or applied instantly depends on the player's current max-life cap; exact policy to be decided with the Level Design team.
5. **Shield Item** — On use/pickup, negates exactly one incoming hit within a 10-second window; the effect ends when that window expires or after it absorbs one hit, whichever comes first.
6. **Rapid Fire Item** — Increases the player's firing rate by 50% for the duration of the current level.
7. **Bullet Speed Item** — Increases projectile speed by 5–10% for the duration of the current level.
8. **Freeze Item** — On use, disables all enemy movement for 5 seconds.
9. **Two-player Value Adjustment** — Provide a mechanism to apply separate (adjustable) numeric values for item effects specifically in two-player mode, based on requirements proposed by the Two-player Mode team.

## Dependencies on Other Teams

1. **Visual Effect System** — Item drop appearance and pickup/activation visuals need to be agreed upon with the Visual Effect (graphics) team.
2. **Level Design System** — Item drop probabilities, and the exact rules for the Life and Shield items, need to be finalized jointly with the Level Design team.
3. **Two-player Mode** — Separate numeric tuning of item effects for two-player mode depends on requirements proposed by the Two-player Mode team.
