# Own-Booking Breathing Border — Design Spec

**Date:** 2026-03-28
**Scope:** Week view + day view only (not timeline)

---

## Problem

In week and day views, all event cards look identical regardless of who created them. Users cannot quickly identify their own bookings at a glance.

---

## Solution

Add a CSS breathing-light animation (`bkOwnBookingPulse`) to the left border of cards where `createdBy == currentUserId`. The effect pulses the border between opaque white and nearly transparent on a 2-second cycle — matching the pattern established in `tw.idempiere.requestkanban` (`rkBorderPulse`).

---

## Changes

### 1. `web/zul/meetingroom.zul` — add CSS

Add inside the existing `<style>` block (alongside `.event-card`, `.event-card.editable`):

```css
@keyframes bkOwnBookingPulse {
  0%, 100% { border-left-color: rgba(255,255,255,0.9); }
  50%       { border-left-color: rgba(255,255,255,0.1); }
}
.event-card.own-booking {
  border-left: 3px solid rgba(255,255,255,0.9);
  animation: bkOwnBookingPulse 2s ease-in-out infinite;
}
```

**Why white?** Cards already have a dark resource color as background; white border is visible against all resource colors.
**Why 2s?** Feels like a breathing light. The kanban reference uses 5s (more subtle); 2s is more prominent for a calendar context.

### 2. `src/tw/ninniku/booking/viewmodel/BookingVM.java` — `renderEventCards`

Add `own-booking` class to the card's class string when `b.getCreatedBy() == currentUserId`:

```java
boolean isOwn = b.getCreatedBy() == currentUserId;
String ownClass = isOwn ? "own-booking" : "";
// card class string: "event-card " + editableClass + " " + ownClass
```

`editableClass` (`editable`) and `ownClass` (`own-booking`) are independent:
- Admin booking someone else's room → `editable` only
- Current user's own booking → `own-booking` (+ `editable` if also owner/admin)

---

## Non-changes

- Timeline view: not affected. Its rendering uses vis-timeline.js and a separate item JSON pipeline.
- `booking.css`, `booking_weekview.js`, `BookingForm.java`: no changes needed.
- No new data fetched — `currentUserId` is already available in `BookingVM`.
