# BookingTimeline Refactoring Design

## Summary

Refactor `BookingTimeline.java` in two phases: (1) cleanup dead code and minor bugs, (2) extract shared rendering logic between `renderWeekView()` and `renderDayView()` into helper methods to eliminate ~120 lines of duplication.

## Phase 1: Cleanup

| Item | Action |
|------|--------|
| Duplicate `getFellow` for btnPrev/btnNext | Remove duplicate lines 418-419 |
| Double `trx.commit()` | Remove duplicate commit at line 348 |
| Dead `convertTimestamp()` method | Delete |
| Dead `draw()` method | Delete |
| Unused imports (`ThemeManager`, `ExecutionImpl`, `LocalDateTime`, `ZoneOffset`) | Remove |
| Unused variable `list` in `addResourceTypeItem()` | Remove |
| Unused variable `code` in `getResourceJSON()` | Remove |
| Public `errorMessage` field | Make private |
| Stale TODO/MQTT comments | Clean up |

## Phase 2: Extract Shared Rendering

### Shared code between renderWeekView() and renderDayView()

Both methods contain nearly identical blocks:
- Resource name map initialization
- Work hours / height calculation
- Event sorting comparator
- Greedy column-packing overlap algorithm
- Event card HTML rendering (positioning, permissions, delete icon, resize handle, onclick)
- Footer HTML and ZK Html component creation

### Extracted helpers

1. **`buildResourceNameMap()`** → `Map<Integer, String>`
2. **`sortAndPackEvents(List<MResourceAssignment>)`** → `List<List<MResourceAssignment>>` (sorted + column-packed)
3. **`renderEventCards(StringBuilder, List<List<MResourceAssignment>>, int startHour, Map<Integer, String>)`** → appends event card HTML to builder

Each render method keeps its unique header/column logic but calls shared helpers.
