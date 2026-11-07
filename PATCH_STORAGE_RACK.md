# Storage Rack / Storage Chest – analýza a patch (IR 0.21.8, SuSy 0.2.0.1-patch2)

## Model
Každý rack (3×3×2… výška 3 bloky) má master TE. Racky na sobě (master přesně o 3 bloky výš) tvoří „stack“.
Sdílený obsah drží **jen spodní** člen (`bottomTE`), ten se zvětší na `n × kapacita`.
Ostatní členové přes `getBottomTE()` posílají insert/extract/GUI/capability na spodní.

## Nalezené chyby (původní kód)
| # | Kde | Chyba | Důsledek |
|---|-----|-------|----------|
| 1 | `TEMultiTankBase.passValueDown/checkSize` | Prostřední členové nedostanou nový `bottomTE`; nový spodní člen si nastaví `bottomTE` jen v jedné větvi | Po zničení spodního racku ve stacku ≥3 ukazují zbylé racky na **zničený TE** → vložené itemy (ruka, hopper, Crafting Station) **mizí** |
| 2 | `checkSize` | Horní člen si nikdy nevynuluje `isBottom` | Rack pod/mezi existující: obsah starého spodního se „osiřel“ (neviditelný), TESR ukazuje 2 popisky |
| 3 | `TEStorage.onMasterBreak` | Zničení spodního = vysype **celý** sdílený obsah na zem (za rack, offset 2) | Tisíce item entit, despawn po 5 min, často ve zdi |
| 4 | `TEStorageChest.onMasterBreak` | Totéž pro chest (vysype všech 198+ slotů) | dtto |
| 5 | rozdělení stacku | Spodní část se zmenší přes `setSize` → přebytek se vysype, horní část je prázdná | Ztráta / rozházení itemů |
| 6 | klient | `onMasterBreak` jen na serveru → klientské `bottomTE` zůstanou na mrtvém TE | Rack vypadá prázdný, GUI se neotevře |
| 7 | `MultiStackHandler.getStackInSlot` | Vrací a **mutuje interní stack** (i `ItemStack.EMPTY`) | Mod, který stack upraví, nebo uložení při počtu 0 → ztráta typu itemu → **celý obsah zmizí** po reloadu |
| 8 | `MultiStackHandler` NBT | Počet se ukládá do bytu (`Count`) → 256 = 0 = „prázdný“ | Zdroj #7 |
| 9 | `MultiStackHandler.extractItem` | Vydá až `maxStack` (4096×n) najednou, ne max 64 | Mod/hopper může přijmout jen část → zbytek voidnut |
| 10 | `TEStorage.onActivated` (shift-klik) | Při plném inventáři hráče se vytažený stack zahodí do voidu | Ztráta 64 itemů |
| 11 | `ContainerStorageChest` | Sloty postavené pro starou velikost; při přidání/odebrání racku s otevřeným GUI | Crash / zápis do špatných slotů |
| 12 | `TileEntityMultiBlockBase.getMaster` | Hledá master v 3×3×3 bez kontroly, že ho footprint obsahuje | Rozbití racku může rozbít **sousední** rack (do hloubky) |
| 13 | `Block*.onBlockActivated` | Horní strana zakázaná | Nejde otevřít shora |

Ostatní postřehy (neopraveno): `search`/`currentLine` chestu jsou sdílené pro všechny hráče; racky s různou orientací se na sebe spojí (jen podle masteru).

## Patch
- `TEMultiTankBase`: celý stack se vždy přepočítá najednou (`relinkStack`) – správné `bottomTE/topTE/isBottom/isTop` pro všechny členy; obsah nespodních členů se přesune do spodního (opravuje i staré světy s osiřelými itemy při načtení).
- Zničení: obsah se nejdřív vyjme, stack se rozdělí, obsah se vrátí do spodní části, pak horní, zbytek se vysype na místě zničeného racku.
- Klient: po každém sync paketu přepočet stacku (`relinkPending`).
- `MultiStackHandler`: interní stack = jen typ (count 1), `getStackInSlot` vrací kopii; oprava starých NBT; extract max 64; vyčištění při 0.
- Shift-klik s plným inventářem → drop místo voidu.
- Chest GUI se zavře, když se stack změní/zničí.
- `getMaster` kontroluje footprint.
- Horní strana: otevře/vloží; položí se jen stejný rack (stavění stacku).

Dotčeno i `TEFluidTank` a `TEIndustrialBatteryBank` (stejný základ) – jen oprava odkazů, přenos tekutin/energie neimplementován.

## Testovací scénáře
1. Rack s itemy + rack nahoru → počet zůstane, kapacita ×2, popisek jen dole.
2. Stack 3 → zničit spodní → itemy v nové spodní části, nic na zemi (pokud se vejde).
3. Stack 3 → zničit prostřední → spodní drží co se vejde, zbytek nahoře, zbytek na zemi.
4. Stack 3 → zničit horní → přebytek nad kapacitu na zemi.
5. Po kroku 2 vkládat do racků (ruka, hopper, Crafting Station) → nic nemizí.
6. Uložit + reload → obsah sedí.
7. Chest: otevřené GUI a jiný hráč přidá/zničí rack → GUI se zavře, bez crashe.
8. Shift-klik prázdnou rukou s plným inventářem → stack padne na zem.
9. Shora klik prázdnou rukou / itemem → otevře / vloží; s rackem v ruce → postaví.
