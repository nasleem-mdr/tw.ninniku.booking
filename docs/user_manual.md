# Booking System User Manual

## 1. Introduction
The Booking System allows you to manage resource reservations (such as meeting rooms) easily within iDempiere. You can view availability, create new bookings, and manage existing reservations through an intuitive graphical interface.

## 2. Accessing the Booking System
1.  Log in to iDempiere.
2.  Navigate to the **Booking** form in the menu (location depends on your system configuration).
3.  The main booking window will open, displaying the Timeline view by default.

## 3. Interface Overview

### 3.1. Toolbar
Located at the top of the window:
*   **Version**: Displays the current system version.
*   **Refresh**: Reloads the latest data from the database.
*   **Resource**: Dropdown to select the type of resource you want to view (e.g., "Meeting Room", "Car").
*   **View Modes**:
    *   **Timeline**: Horizontal scrollable timeline.
    *   **Week**: Weekly calendar grid.
    *   **Month**: Monthly calendar view.
*   **Navigation**: `<` (Previous), `Today`, `>` (Next).

### 3.2. Timeline View
*   Resources are listed on the left (Y-axis).
*   Time is displayed horizontally (X-axis).
*   **Red Line**: Indicates the current time.
*   **Scroll**: Mouse wheel or drag to move through time.

### 3.3. Week View
*   Displays a traditional weekly schedule (Mon-Sun).
*   Columns represent days, rows represent time slots.
*   Events are color-coded by resource.

## 4. Managing Bookings

### 4.1. Creating a New Booking
**Method 1: Timeline View**
1.  Double-click or drag on an empty space in the timeline row of the desired resource.
2.  The "新增預約單" (New Booking) dialog will appear.
3.  Fill in the details:
    *   **Resource**: Verify the correct resource is selected.
    *   **Meeting Subject**: Title of the booking.
    *   **Memo**: Additional description/notes.
    *   **From/To**: Start and End time.
4.  **Recurring Bookings**:
    *   Check `Weekly repetition` (Weekly).
    *   Set `Before this date` to define when the recurrence should stop.
5.  Click **Ok** to save.

**Method 2: Week/Month View**
1.  Click on any empty time slot in the day column.
2.  The dialog will open with the start time pre-filled.

### 4.2. Editing a Booking
1.  Click on an existing booking block.
2.  The "修改預約單" (Edit Booking) dialog will appear.
3.  Modify the details as needed.
4.  Click **Ok** to save changes.

### 4.3. Deleting a Booking
1.  Click on an existing booking block to open the edit dialog.
2.  Click the **Delete** button.
3.  Confirm the deletion when prompted.

### 4.4. Moving/Resizing (Timeline Only)
*   **Move**: Click and drag a booking block to a new time or resource.
*   **Resize**: Hover over the edge of a booking block until the cursor changes, then drag to extend or shorten the duration.
*   *Note*: The system will automatically save the new time after you drop/release the mouse.

## 5. Errors and validation
*   **Time Overlap**: If you try to create or move a booking to a time slot that is already occupied by another booking for the same resource, the system will show an error message ("Time overlap, update failed") and revert the change.

