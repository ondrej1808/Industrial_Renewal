# Storage Rack / Storage Chest – analysis and patch (IR 0.21.8, SuSy 0.2.0.1-patch2)

## Model
Every rack (3×3 footprint, 3 blocks tall) has a master TE. Racks stacked on top of each other (master exactly
3 blocks higher) form a "stack". The shared contents are held **only by the bottom** member (`bottomTE`),
which is resized to `n × capacity`. All other members forward insert/extract/GUI/capabilities to the bottom
via `getBottomTE()`.

## Bugs found (original code)
| # | Where | Bug | Consequence |
|---|-------|-----|-------------|
| 1 | `TEMultiTankBase.passValueDown/checkSize` | Intermediate members never get a new `bottomTE`; the new bottom only sets its `bottomTE` in one branch | After breaking the bottom rack of a stack of 3 or more, the remaining racks point to the **destroyed TE**. Items inserted by hand, hopper or Crafting Station **vanish** |
| 2 | `checkSize` | The top member never clears `isBottom` | Rack placed below or between existing racks: the old bottom's contents become orphaned (invisible), the TESR shows two labels |
| 3 | `TEStorage.onMasterBreak` | Breaking the bottom dumps the **whole** shared content on the ground (behind the rack, offset 2) | Thousands of item entities, despawn after 5 minutes, often inside walls |
| 4 | `TEStorageChest.onMasterBreak` | Same for the chest (dumps all 198+ slots) | Same |
| 5 | Stack split | The lower part shrinks via `setSize` and dumps the overflow; the upper part is empty | Items lost or scattered |
| 6 | Client | `onMasterBreak` runs only on the server, so client `bottomTE` keeps pointing to the dead TE | Rack looks empty, GUI does not open |
| 7 | `MultiStackHandler.getStackInSlot` | Returns and **mutates the internal stack** (even `ItemStack.EMPTY`) | A mod modifying the stack, or saving while the count is 0, loses the item type: **the whole content vanishes** after reload |
| 8 | `MultiStackHandler` NBT | The count is saved into a byte (`Count`), so 256 becomes 0 = "empty" | Source of #7 |
| 9 | `MultiStackHandler.extractItem` | Hands out up to `maxStack` (4096×n) at once instead of max 64 | A mod or hopper may accept only part, the rest is voided |
| 10 | `TEStorage.onActivated` (shift-click) | With a full player inventory the extracted stack is voided | Loss of 64 items |
| 11 | `ContainerStorageChest` | Slots built for the old size; adding/removing a rack while the GUI is open | Crash or writes to wrong slots |
| 12 | `TileEntityMultiBlockBase.getMaster` | Searches a 3×3×3 area for a master without checking that its footprint contains the block | Breaking a rack can break a **neighbouring** rack (depth-wise) |
| 13 | `Block*.onBlockActivated` | Top face disabled | Cannot be opened from the top |
| 14 | `GUIStorageChest` | Mouse wheel read during rendering, only over the narrow scrollbar | Mouse Tweaks takes the wheel and moves items instead of scrolling |
| 15 | `GUIStorageChest` search | Only paints non-matching slots red on the current page | On an empty page the search shows nothing |

Other notes (not fixed): the chest's `search` and `currentLine` are shared by all players; racks with different
orientations are linked into one stack (only the master position is checked).

## Patch
- `TEMultiTankBase`: the whole stack is always resolved at once (`relinkStack`), giving every member correct
  `bottomTE/topTE/isBottom/isTop`. Contents of non-bottom members are moved into the bottom (this also recovers
  orphaned items in old worlds when they load).
- Breaking: the contents are taken out first, the stack is split, the contents go back into the lower part,
  then the upper part, and only the rest is dropped at the broken rack.
- Client: the stack is re-resolved after every sync packet (`relinkPending`).
- `MultiStackHandler`: the internal stack is only the item type (count 1) and `getStackInSlot` returns a copy.
  Legacy NBT is repaired, extract gives max 64, the type is cleared at 0.
- Shift-click with a full inventory drops the stack instead of voiding it.
- The chest GUI closes when the stack changes or is broken.
- `getMaster` checks the footprint.
- Top face: opens/inserts; only the same rack is placed on top (to build the stack).
- Chest GUI: the mouse wheel is handled in the earliest mouse-input event (before Mouse Tweaks) over the slots
  and the scrollbar. The search jumps to the first matching line; Enter jumps to the next match.

`TEFluidTank` and `TEIndustrialBatteryBank` share the same base class and get the pointer fixes too.
Transferring fluids/energy between stack parts is not implemented.

## Test scenarios
1. Rack with items + another rack on top: the count stays, the capacity doubles, only the bottom shows a label.
2. Stack of 3, break the bottom one: the items are in the new bottom part, nothing on the ground (if it fits).
3. Stack of 3, break the middle one: the bottom part keeps what fits, the rest goes to the top part, the rest to the ground.
4. Stack of 3, break the top one: the overflow above capacity is dropped.
5. After step 2, insert into the racks (hand, hopper, Crafting Station): nothing disappears.
6. Save and reload: the contents match.
7. Chest: GUI open while another player adds or breaks a rack: the GUI closes, no crash.
8. Shift + right-click with an empty hand and a full inventory: the stack drops on the ground.
9. Top face: empty hand or item opens/inserts, a rack in hand builds the stack.
10. Chest GUI: the mouse wheel over the slots/scrollbar scrolls, Mouse Tweaks does not move items.
11. Chest search on an empty page jumps to the matching line, Enter jumps to the next match.
