# Booking System User Manual

**Version:** 3.31 | **Updated:** 2026-02-26

## 1. Introduction

The Booking System allows you to manage resource reservations (such as meeting rooms) easily within iDempiere. You can view availability, create new bookings, and manage existing reservations through an intuitive graphical interface.

## 2. Accessing the Booking System

1. Log in to iDempiere.
2. Navigate to the **Booking** form in the menu (location depends on your system configuration).
3. The main booking window will open, displaying the Timeline view by default.

## 3. Interface Overview

### 3.1. Toolbar

Located at the top of the window:

| Element | Description |
|---------|-------------|
| **Version** | Displays the current system version (e.g. 3.31). |
| **Refresh** | Reloads the latest data from the database. |
| **Resource** | Dropdown to select the type of resource (e.g. "Meeting Room", "Car"). |
| **View Modes** | **Week** \| **Day** \| **Timeline** — switch between views. |
| **Navigation** | `<` (Previous) \| `Today` \| `>` (Next). |
| **Add Booking** | Opens a dialog to create a new booking at the current time. |
| **Only Work Hours** | Checkbox — when checked, only shows 08:00–18:00; when unchecked, shows 00:00–23:00. |

### 3.2. Timeline View

* Resources are listed on the left (Y-axis).
* Time is displayed horizontally (X-axis).
* **Red Line**: Indicates the current time.
* **Scroll**: Mouse wheel or drag to move through time.
* Items are fully interactive: drag to move, drag edges to resize.

### 3.3. Week View

* Displays a **5-day** schedule (**Mon–Fri**).
* Columns represent days; rows represent time slots (30-minute grid).
* Events are color-coded by resource (Material Design palette).
* Hour labels shown in the left-hand time column.
* A **red line** indicates the current time.
* Use the **Only Work Hours** checkbox to toggle between work hours (08:00–18:00) and full day (00:00–23:00).

### 3.4. Day View

* Shows a **single day** with each resource as its own column.
* Date is displayed as a full-width title row (e.g. `2026/02/26 (Thu)`).
* Resource names appear as column headers, color-coded to match event cards.
* Time grid and hour labels are shared with the Week View.
* Use `<` / `>` buttons to navigate day-by-day.

## 4. Managing Bookings

### 4.1. Creating a New Booking

**Method 1: Add Booking Button**

1. Click the **Add Booking** button in the toolbar.
2. The "新增預約單" (New Booking) dialog will appear with the current time pre-filled.
3. Fill in the details:
   * **Resource**: Select the desired resource.
   * **Meeting Subject**: Title of the booking (required).
   * **Memo**: Additional description/notes.
   * **From / To**: Start and end time.
4. **Recurring Bookings** (optional):
   * Check `Weekly repetition`.
   * Set `Before this date` to define when the recurrence should stop.
5. Click **Ok** to save.

**Method 2: Click on Empty Time Slot (Week / Day View)**

1. Click on an empty area in a day column or resource column.
2. The dialog will open with the start time pre-filled.

**Method 3: Drag-to-Create (Week / Day View)**

1. Click and drag on an empty area in a day column (Week View) or resource column (Day View).
2. A **blue ghost** rectangle shows the selected time range.
3. A tooltip displays the start/end time and duration (e.g. `09:00 – 10:30 (1h 30m)`).
4. Time snaps to **30-minute** increments.
5. Release to open the booking dialog with the time range pre-filled.

### 4.2. Editing a Booking

1. Click on an existing booking card.
2. The "修改預約單" (Edit Booking) dialog will appear.
3. Modify the details as needed.
4. Click **Ok** to save changes.

### 4.3. Deleting a Booking

**Method 1: Via Edit Dialog**
1. Click on an existing booking card to open the edit dialog.
2. Click the **Delete** button.
3. Confirm the deletion when prompted.

**Method 2: Delete Icon (Week / Day View)**
1. Hover over a booking card that you own (or if you have Admin access).
2. Click the **×** icon in the top-right corner of the card.
3. Confirm the deletion when prompted.

> **Note**: The delete icon only appears for the booking's creator or users with Admin (Read/Write) access.

### 4.4. Moving a Booking (Drag-to-Move)

**Timeline View:**
* Click and drag a booking block to a new time or resource row.

**Week / Day View:**
* Click and drag an existing booking card to a new time slot or column.
* A ghost image follows the cursor during the drag.
* Time snaps to **30-minute** increments.
* The system detects which column (day or resource) you drop into.
* The booking is automatically saved after you release the mouse.

> **Restriction**: Only the booking's **creator** or a user with **Admin** (Read/Write) access can drag-to-move a booking. Other users see the card in read-only mode.

### 4.5. Resizing a Booking (Week / Day View)

1. Hover over the **bottom edge** of a booking card until the cursor changes to a resize handle.
2. Drag down to extend or up to shorten the duration.
3. Time snaps to **30-minute** increments.
4. The booking is automatically saved after you release.

> **Restriction**: Only the booking's **creator** or a user with **Admin** (Read/Write) access can resize a booking.

### 4.6. Work Hours Toggle

* Check the **Only Work Hours** checkbox to show only 08:00–18:00 in Week and Day views.
* Uncheck to show the full 00:00–23:00 range.
* The toggle takes effect immediately.

## 5. Errors and Validation

| Scenario | Message / Behavior |
|----------|-------------------|
| **Time Overlap** | If you try to create or move a booking to a time slot already occupied by another booking for the same resource, the system shows an error ("Time overlap, update failed") and reverts the change. |
| **Missing Subject** | The booking name (Subject) is required. The form will highlight the field with a red border if left empty. |
| **Permission Denied** | If you do not have write access and are not the creator, you cannot move, resize, or delete the booking. The card appears without drag handles or delete icons. |

## 6. Version History (3.x)

| Version | Changes |
|---------|---------|
| **3.00** | Added Week View with weekly calendar grid. |
| **3.01** | Added "Add Booking" button in Week View toolbar. |
| **3.06** | Added delete function in Week View. |
| **3.2x** | Drag-and-drop booking in Week View (create, move, resize). Timezone fix for drag-and-drop. Name validation in Week View. Permission restriction: drag/resize limited to Creator or Admin. Work Hours toggle (08:00–18:00 vs full day). |
| **3.30** | Added **Day View** (single-day, resource-as-columns). Removed Month View. Reordered view buttons to Week \| Day \| Timeline. Week View changed to **Mon–Fri** (5-day) to match Flutter app. |
| **3.31** | Code refactoring: extracted shared helpers (`buildResourceNameMap`, `sortAndPackEvents`, `renderEventCards`). Removed dead code. Fixed duplicate code and stale comments. |
