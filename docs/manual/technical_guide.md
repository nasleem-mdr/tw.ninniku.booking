# Booking Plugin Technical Guide

**Version:** 3.31 | **Updated:** 2026-02-26

## 1. Overview

The `tw.ninniku.booking` plugin provides a graphical resource booking interface for iDempiere. It allows users to visualize and manage resource assignments (e.g. meeting rooms, equipment) using three views: **Week**, **Day**, and **Timeline**.

## 2. Technology Stack

| Layer | Technology |
|-------|-----------|
| **Backend Framework** | iDempiere (OSGi Bundle) |
| **Server-Side UI** | ZK Framework (Java) |
| **Timeline Visualization** | vis.js (Vis Timeline) |
| **Dialogs & Interactions** | jQuery / jQuery UI |
| **Week/Day View Rendering** | Server-side HTML generation (Java → ZK Html component) |
| **Week/Day View Interactions** | Inline JavaScript (IIFE in meetingroom.zul) |
| **Styles** | Inline CSS in ZUL + ZK native components |
| **Database** | PostgreSQL / Oracle (standard iDempiere DBs) |
| **Tables** | `S_Resource`, `S_ResourceType`, `S_ResourceAssignment` |

## 3. Project Structure

```
tw.ninniku.booking/
├── src/tw/ninniku/booking/
│   ├── form/
│   │   └── BookingTimeline.java      # Main controller (ADForm)
│   └── model/
│       ├── MResourceAssignment.java   # Extended model with overlap check
│       └── MBooking.java              # Booking-specific model logic
├── src/web/
│   ├── meetingroom.zul                # Main UI (English)
│   └── meetingroom_tw.zul             # Main UI (Traditional Chinese)
├── js/
│   ├── booking.js                     # Timeline client-side logic
│   ├── booking_weekview.js            # Week/Day view event handlers
│   ├── vis-timeline-graph2d.min.js    # Vis.js library
│   └── ...
├── styles/                            # CSS styling
├── META-INF/MANIFEST.MF              # OSGi bundle definition
├── OSGI-INF/                          # Service component definitions
└── docs/
    ├── manual/                        # User & technical guides
    └── plans/                         # Design & implementation plans
```

## 4. Key Components

### 4.1. Controller: `BookingTimeline.java`

Implements `ADForm` and acts as the bridge between the backend database and the frontend ZUL/JS.

**Responsibilities:**

1. **Initialization** — Loads `meetingroom.zul`, initializes UI components (toolbar, dropdowns, buttons, checkbox).
2. **Data Loading** — Queries `S_ResourceAssignment` and `S_Resource` tables.
3. **JSON Generation** — Converts DB records into JSON for `vis.js` (Timeline) or HTML rendering (Week/Day).
4. **View Rendering** — `renderWeekView()` and `renderDayView()` generate complete HTML strings server-side, injected via ZK `Html` component.
5. **Event Handling** — Listens for ZK events (button clicks, checkbox change) and hidden field `onChange` events (triggered by JS for CRUD).
6. **Persistence** — Saves/Updates/Deletes records using `MResourceAssignment`, with transaction management and overlap checks.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `initForm()` | Initialize UI, wire event listeners, load first view. |
| `refreshView()` | Re-render the current view mode (Timeline, Week, or Day). |
| `renderWeekView()` | Build 5-day (Mon–Fri) HTML grid with event cards. |
| `renderDayView()` | Build single-day HTML grid with resource-as-columns. |
| `buildResourceNameMap()` | Create `Map<Integer, String>` of resource ID → name. |
| `sortAndPackEvents()` | Pack overlapping events into non-overlapping columns. |
| `renderEventCards()` | Render positioned, styled, permission-aware event cards. |
| `appendViewFooter()` | Close divs, add current-time indicator, inject HTML into ZK. |
| `updateBooking(JSONObject)` | Validate, save, and handle weekly repetition. |
| `deleteBooking(JSONObject)` | Validate and delete a booking. |
| `getBookingJSON()` | Generate vis.js-compatible JSON for Timeline view. |
| `fetchBookings()` | Query bookings for a custom date range (Week/Day views). |
| `getResourceJSON()` | Load all resources of the selected type. |
| `isWritable()` | Check if current user's role has Read/Write access to the form. |

### 4.2. View: `meetingroom.zul` / `meetingroom_tw.zul`

Defines the layout using ZK's Native namespace (`xmlns:n="native"`) to embed raw HTML.

**Structure:**

* **Toolbar** — Version, view buttons (Week \| Day \| Timeline), resource dropdown, Add Booking, navigation, work hours checkbox, Refresh.
* **Booking Container** — A `div` that hosts either vis.js Timeline or server-rendered HTML for Week/Day.
* **Hidden Form (`#update-form`)** — jQuery UI dialog for booking details (Subject, Memo, Date Range, Recurring options).
* **Hidden Data Fields** — `visible="false"` textboxes acting as JS→Java communication channel:
  * `bookingUpdated` — triggers create/update flow
  * `bookingDeleted` — triggers delete flow
  * `itemData` — carries JSON payload

**Inline JavaScript (IIFE):**

The ZUL file contains ~400 lines of inline JS implementing the Week/Day view drag-and-drop system:

| Mode | Trigger | Behavior |
|------|---------|----------|
| **CREATE** | Click + drag on empty `.day-col` area | Blue ghost rectangle, 30-min snap, opens booking dialog with pre-filled range. |
| **MOVE** | Click + drag on `.event-card.editable` | Ghost clone follows cursor, column detection via `elementFromPoint()`, 30-min snap, auto-save. |
| **RESIZE** | Click + drag on `.resize-handle` | Extend/shorten card height, 30-min snap to end time, auto-save. |

**Helper Functions in JS:**

| Function | Purpose |
|----------|---------|
| `getTimeFromY(y)` | Convert Y pixel position to datetime. |
| `snapTo30(date)` | Round to nearest 30-minute boundary. |
| `formatTime(date)` | Format as "HH:MM". |
| `formatDuration(ms)` | Format as "Xh Ym". |
| `triggerUpdate(json)` | Write JSON to hidden fields, fire ZK change event. |

### 4.3. Client Logic: `booking.js` / `booking_weekview.js`

* **`booking.js`** — Configures vis.js Timeline, handles Timeline-specific drag-and-drop and click events.
* **`booking_weekview.js`** — Handles event card click (`onWeekEventClick`), new booking click (`onWeekDayClick`), and edit/delete dialog management.

**Dialog Functions:**

| Function | Purpose |
|----------|---------|
| `clickNew()` | Open new booking dialog, show recurrence fields. |
| `openEditDialog()` | Open edit dialog with pre-filled data, hide recurrence fields. |
| `openCustomAddDialogRange(start, end, resourceId)` | Wrapper for drag-to-create completion. |
| `openCustomEditDialog(eventData)` | Wrapper for event card click. |

### 4.4. Model: `MResourceAssignment.java`

Extends the standard `org.compiere.model.MResourceAssignment`.

* **Overlap Check**: `isOverlap()` — queries the database to prevent double-booking of the same resource during the same time slot. Returns `true` if a conflicting assignment exists.

## 5. View Rendering Architecture

### 5.1. Shared Rendering Pipeline (Week & Day Views)

Both views share the same rendering pipeline via extracted helper methods:

```
fetchBookings(startDate, endDate)
    ↓
Filter events by column (day or resource)
    ↓
sortAndPackEvents(events)          → Pack into non-overlapping columns
    ↓
renderEventCards(columns, ...)     → Generate positioned HTML cards
    ↓
appendViewFooter(sb, ...)          → Close HTML, add time indicator, inject into ZK
```

### 5.2. Event Packing Algorithm (`sortAndPackEvents`)

1. Sort events by start time (ties broken by longer duration first).
2. For each event, scan existing columns left-to-right.
3. Place in the first column where it doesn't overlap any existing event.
4. If no column fits, create a new column.
5. Return `List<List<MResourceAssignment>>` — each inner list is a column of non-overlapping events.

### 5.3. Event Card Rendering (`renderEventCards`)

Each card receives:

* **Position**: `top` = offset from day start (px), `height` = duration (px), min 15px.
* **Width**: `95% / numColumns`, offset by column index.
* **Color**: From a 19-color Material Design palette, assigned by resource ID hash.
* **Permissions**: If user is creator or has Admin access:
  * Card gets `.editable` CSS class (enables drag cursor).
  * Delete icon (`×`) rendered in top-right corner.
  * Resize handle rendered at bottom.
* **Content**: Booking name, creator name, memo (truncated).
* **Click handler**: `onWeekEventClick(id)` to open edit dialog.

### 5.4. Week View Specifics

* **Days**: Monday–Friday (5 columns), calculated from the Monday of the current week.
* **Navigation**: `<` / `>` moves by 1 week, `Today` jumps to current week.
* **Header**: Day name + date (e.g. `Mon 02/26`).
* **Grid background**: Repeating 40px horizontal stripes (alternating #f9f9f9 / white).

### 5.5. Day View Specifics

* **Columns**: One per resource (meeting room), filtered by selected resource type.
* **Navigation**: `<` / `>` moves by 1 day, `Today` jumps to today.
* **Title row**: Full-width centered date (e.g. `2026/02/26 (Thu)`).
* **Header**: Resource names, color-coded.

### 5.6. Timeline View

* Rendered entirely client-side using vis.js.
* Data loaded via `Clients.evalJavaScript()` — JSON arrays of items and groups.
* Options: editable (add, remove, updateGroup, updateTime), tooltip, snap to 30 min, hidden dates.

## 6. Permissions Model

### 6.1. Form Access

Determined by `AD_Form_Access` table:

```java
boolean isWritable() {
    // Query: SELECT isreadwrite FROM AD_Form_Access
    //        WHERE ad_role_id = ? AND ad_form_id = ?
    return "Y".equals(result);
}
```

### 6.2. Per-Event Permissions

Each event card checks:

```
canModify = isWritable() || (event.CreatedBy == currentUserId)
```

* **canModify = true**: Card is `.editable`, drag/resize/delete enabled.
* **canModify = false**: Card is read-only, click opens view-only dialog.

## 7. Data Flow

### 7.1. Load Flow

```
User opens Form
  → BookingTimeline.initForm()
  → Java queries DB (S_ResourceAssignment, S_Resource)
  → Timeline: generates JSON, calls Clients.evalJavaScript()
  → Week/Day: generates HTML string, injects via ZK Html component
  → View rendered in browser
```

### 7.2. Create/Update Flow

```
User action (drag/dialog submit)
  → JS serializes data to JSON
  → JS sets hidden textbox value (itemData)
  → JS fires bookingUpdated.onChange
  → BookingTimeline.onEvent() intercepts
  → Java parses JSON, validates (name required, overlap check)
  → Java saves to S_ResourceAssignment (with transaction)
  → If weekly repetition: create copies every 7 days until end date
  → On success: refreshView()
  → On error: show message, rollback transaction
```

### 7.3. Delete Flow

```
User clicks delete icon or dialog Delete button
  → JS serializes {s_booking_id} to JSON
  → JS fires bookingDeleted.onChange
  → BookingTimeline.deleteBooking() intercepts
  → Java validates ID, calls booking.delete(true)
  → refreshView()
```

## 8. CSS & Layout

### 8.1. Grid Layout

* **Time column**: Fixed width, shows hour labels.
* **Day/Resource columns**: `flex: 1`, equal width, `position: relative` for card positioning.
* **Scroll body**: `overflow-y: auto`, `max-height: 600px`.
* **Sticky header**: `position: sticky; top: 0; z-index: 10`.
* **Grid lines**: `background: repeating-linear-gradient(...)` — 40px per hour.

### 8.2. Event Card Styling

* `position: absolute` within column.
* `border-radius: 4px`, `box-shadow` for depth.
* `cursor: grab` for editable cards, `cursor: default` for read-only.
* Delete icon: `position: absolute; top: 2px; right: 4px`, red on hover.
* Resize handle: `height: 6px; cursor: ns-resize` at bottom of card.

### 8.3. Color Palette

19-color Material Design palette, assigned by `resourceId % 19`:

```
#4FC3F7, #81C784, #FFB74D, #E57373, #BA68C8,
#4DD0E1, #AED581, #FFD54F, #F06292, #7986CB,
#A1887F, #90A4AE, #FF8A65, #DCE775, #4DB6AC,
#9575CD, #FFF176, #64B5F6, #E0E0E0
```

## 9. Installation & Configuration

1. **Environment**: iDempiere 11+, Java 17.
2. **Build**: Maven (`mvn clean install`) or Eclipse PDE export.
3. **Deploy**: Install the `.jar` via Felix Web Console or place in `plugins/` directory. Ensure the `tw.ninniku.booking` bundle is in "Active" state.
4. **Database**: Requires standard tables `S_Resource`, `S_ResourceType`, `S_ResourceAssignment`.
5. **Menu**: Register the Form in iDempiere "Form" window referencing `tw.ninniku.booking.form.BookingTimeline`. Add the Form to the Menu.

## 10. Version History (3.x)

| Version | Changes |
|---------|---------|
| **3.00** | Added Week View with weekly calendar grid alongside existing Timeline View. |
| **3.01** | Added "Add Booking" button in toolbar for Week View. |
| **3.06** | Added delete function in Week View (delete icon on event cards). |
| **3.2x** | Drag-and-drop for Week View (create, move, resize). Timezone fix for drag-and-drop operations. Name validation on booking form. Permission restriction: drag/resize limited to Creator or Admin. Work Hours toggle checkbox (08:00–18:00 vs 00:00–23:00). |
| **3.30** | Added **Day View** (single-day, resource-as-columns). Removed Month View. Reordered toolbar buttons to Week \| Day \| Timeline. Week View changed to **Mon–Fri** (5-day) to match Flutter app. |
| **3.31** | Code refactoring: extracted shared helpers (`buildResourceNameMap`, `sortAndPackEvents`, `renderEventCards`, `appendViewFooter`). Removed dead code (`convertTimestamp`, `draw`, unused imports). Fixed duplicate `getFellow` calls, cleaned up unused variables and stale comments. |
