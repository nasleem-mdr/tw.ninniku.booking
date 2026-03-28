# Own-Booking Pulse Border Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a white breathing-light left-border animation to event cards in week/day view that belong to the current user.

**Architecture:** Two-file change — CSS keyframe + class in `meetingroom.zul`; add `own-booking` class to rendered HTML in `renderEventCards` when `b.getCreatedBy() == currentUserId`. No new data fetched; `currentUserId` already exists on `BookingVM`.

**Tech Stack:** ZK MVVM, CSS `@keyframes`, Java HTML string rendering in `BookingVM`.

---

### Task 1: Add CSS animation to meetingroom.zul

**Files:**
- Modify: `web/zul/meetingroom.zul` — inline `<style>` block (after line with `.event-card.editable:active`)

- [ ] **Step 1: Locate insertion point**

Open `web/zul/meetingroom.zul`. Find the line:
```css
.event-card.editable:active { cursor: grabbing; }
```
The new rules go immediately after this line.

- [ ] **Step 2: Add keyframe and class**

Insert the following two blocks after `.event-card.editable:active`:

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

- [ ] **Step 3: Commit**

```bash
git add web/zul/meetingroom.zul
git commit -m "feat: add bkOwnBookingPulse CSS animation for own-booking cards"
```

---

### Task 2: Mark own-booking cards in renderEventCards

**Files:**
- Modify: `src/tw/ninniku/booking/viewmodel/BookingVM.java` — `renderEventCards` method

- [ ] **Step 1: Locate the card class assembly**

In `renderEventCards`, find these two lines (they appear together):
```java
boolean isOwnerOrAdmin = isAdmin || b.getCreatedBy() == currentUserId;
...
String editableClass = isOwnerOrAdmin ? "editable" : "";
```

- [ ] **Step 2: Add ownClass variable**

Immediately after the `editableClass` line, add:

```java
String ownClass = (b.getCreatedBy() == currentUserId) ? "own-booking" : "";
```

- [ ] **Step 3: Include ownClass in the card's class attribute**

Find the `html.append(String.format(...))` call. Its first argument contains:
```java
"<div class='event-card %s' style='..."
```

Change it to:
```java
"<div class='event-card %s %s' style='..."
```

And in the corresponding `String.format` argument list, add `ownClass` as the second `%s` argument, right after `editableClass`:

```java
html.append(String.format(
        "<div class='event-card %s %s' style='top:%.1fpx;height:%.1fpx;"
        + "background-color:%s;width:%.1f%%;left:%.1f%%;' "
        + "data-id='%d' data-resource-id='%d' data-start-ms='%d' data-end-ms='%d' "
        + "data-booking-name=\"%s\" data-booking-desc=\"%s\">"
        + "%s%s%s</div>",
        editableClass, ownClass, top, height, color, width, left,
        b.getS_ResourceAssignment_ID(), b.getS_Resource_ID(), startMs, endMs,
        nameAttr, descAttr,
        displayContent, deleteIconHtml, resizeHandle));
```

- [ ] **Step 4: Visual verification**

Deploy the plugin and open the booking calendar. In week or day view:
- Own bookings (created by the logged-in user) should show a white left border pulsing from opaque to nearly transparent every 2 seconds.
- Other users' bookings should have no left border animation.
- Admin user's own bookings should show both the `editable` cursor and the pulse animation.

- [ ] **Step 5: Commit**

```bash
git add src/tw/ninniku/booking/viewmodel/BookingVM.java
git commit -m "feat: add own-booking breathing border to week/day event cards"
```
