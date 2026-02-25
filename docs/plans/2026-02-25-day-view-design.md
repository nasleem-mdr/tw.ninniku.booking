# Day View Design

## Summary

Add a Day View to the ZK booking system that shows a single day with each resource (meeting room) as its own column. Reuses the existing Week View CSS classes and JavaScript drag-and-drop logic for minimal code change.

## Layout

```
┌──────────┬──────────┬──────────┬──────────┐
│  Time    │ Room A   │ Room B   │ Room C   │
├──────────┼──────────┼──────────┼──────────┤
│ 08:00    │          │          │          │
│ 09:00    │ [Event]  │ [Event]  │          │
│ 10:00    │          │          │ [Event]  │
│ ...      │          │          │          │
│ 18:00    │          │          │          │
└──────────┴──────────┴──────────┴──────────┘
```

- Header row shows resource/room names (color-coded)
- Time axis: 40px per hour, respects "Only Work Hours" toggle (8-18 or 0-23)
- Each resource column is a `.day-col` with `data-date` and `data-resource-id`
- Overlapping events within a room use the existing greedy column-packing algorithm

## Interactions

All interactions reuse existing JavaScript drag system unchanged:

| Action | Behavior |
|--------|----------|
| Click empty slot | Opens add dialog with room pre-selected |
| Drag to create | Ghost in resource column, 30min snap, opens add dialog |
| Drag to move | Move between resource columns = change room |
| Resize | Bottom handle adjusts end time |
| Click event | Opens edit dialog |
| Delete (x) | Confirmation then delete |
| Current time line | Red horizontal line, same as Week View |

## Implementation Approach

Reuse Week View architecture (Approach A):

### Java Changes (BookingTimeline.java)

1. Add `VIEW_DAY = "DAY"` constant
2. Add `btnViewDay` field
3. Add `renderDayView()` method:
   - Query `fetchBookings(dayStart, dayEnd)` for one day
   - Iterate over `groups` (resources) to create columns
   - Each column: header = room name, body = events for that room
   - Reuse same HTML structure: `.week-view-root`, `.week-header`, `.day-col`, `.event-card`
   - Reuse `getResourceColor()` for header coloring
   - Reuse overlap algorithm per resource column
4. Update `updateViewMode()` to handle DAY
5. Update `navigateView()`: DAY mode navigates ±1 day
6. Update `refreshView()` to call `renderDayView()` for DAY mode
7. Wire up `btnViewDay` in `initForm()`

### ZUL Changes (meetingroom.zul)

Add Day button to toolbar between Week and the separator:
```xml
<button id="btnViewDay" label="Day" mold="os"/>
```

### JavaScript Changes

None. The existing drag system works on `.day-col` elements using `data-date` and `data-resource-id` attributes, which the Day View will set correctly.

## Navigation

- Prev/Next: ±1 day in DAY mode
- Today: jumps to current date (existing behavior)
- View switching: Timeline / Week / Day buttons, active button is disabled

## Data Flow

1. `renderDayView()` → `fetchBookings(dayStart, dayEnd)` for single day
2. Group bookings by `S_Resource_ID`
3. Render each resource as a column with its bookings
4. All save/update/delete flows unchanged (ZK widget communication)

## Reference

Flutter Day View: `/Users/ray/sources/tw.idempiere.flutter/lib/features/booking/presentation/booking_day_view.dart`
