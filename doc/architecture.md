# Booking Plugin Technical Architecture

## 1. Overview
The `tw.ninniku.booking` plugin provides a graphical resource booking interface for iDempiere. It allows users to visualize and manage resource assignments (e.g., meeting rooms, equipment) using a timeline, week, or month view.

## 2. Technology Stack
*   **Backend Framework**: iDempiere (OSGi Bundle)
*   **Server-Side UI**: ZK Framework (Java)
*   **Client-Side UI**: 
    *   **Timeline Visualization**: `vis.js` (Vis Timeline)
    *   **Dialogs & Interactions**: jQuery / jQuery UI
    *   **Styles**: Custom CSS + ZK native components
*   **Database**: PostgreSQL / Oracle (standard iDempiere supported DBs)
    *   **Tables**: `S_Resource`, `S_ResourceType`, `S_ResourceAssignment`

## 3. Project Structure
*   **`src/tw/ninniku/booking/form/`**: Contains the main Form Controller (`BookingTimeline.java`).
*   **`src/tw/ninniku/booking/model/`**: Contains model logic (`MResourceAssignment`, `MBooking`).
*   **`src/web/`**: Contains ZUL definition files (`meetingroom.zul`, `meetingroom_tw.zul`).
*   **`js/`**: Client-side JavaScript logic (`booking.js`, `vis.js`, etc.).
*   **`styles/`**: CSS styling.

## 4. Key Components

### 4.1. Controller: `BookingTimeline.java`
This class implements `ADForm` and acts as the bridge between the backend database and the frontend ZUL/JS.

*   **Responsibility**:
    1.  **Initialization**: Loads `meetingroom.zul`, initializes UI components.
    2.  **Data Loading**: Queries `S_ResourceAssignment` and `S_Resource`.
    3.  **JSON Generation**: Converts DB records into JSON format compatible with `vis.js` and custom views.
    4.  **Event Handling**: Listens for ZK events (buttons) and hidden field changes (triggered by JS for CRUD operations).
    5.  **Persistence**: Saves/Updates/Deletes records using `MResourceAssignment`.

### 4.2. View: `meetingroom.zul` / `meetingroom_tw.zul`
Defines the layout using ZK's Native namespace (`xmlns:n="native"`) to embed raw HTML for the timeline container and jQuery dialogs.

*   **Structure**:
    *   **Toolbar**: Filters and View Switchers.
    *   **Booking Container**: A `div` that hosts the `vis.js` timeline or custom HTML for Week/Month views.
    *   **Hidden Form (`#update-form`)**: A jQuery UI dialog form used for defining booking details (Subject, Memo, Date Range, Recursive options).
    *   **Hidden Data Fields**: Textboxes with `visible="false"` acting as a communication channel between JS and Java (e.g., `bookingUpdated`, `bookingDeleted`, `itemData`).

### 4.3. Client-Logic: `booking.js`
Handles the interactive part of the UI.

*   **Timeline Initialization**: Configures `vis.Timeline`.
*   **Interaction**: Handles drag-and-drop, resizing, and clicking on events.
*   **Custom Views**: Implements Logic for interactions in Week/Month HTML views (`onWeekDayClick`, `onWeekEventClick`).
*   **Data Synchronization**: 
    *   When a user moves/updates a booking in JS, it serializes the data to JSON.
    *   Sets the value of a hidden ZK Textbox (e.g., `itemData`).
    *   Triggers the `onChange` event to notify the Java server side.

### 4.4. Model: `MResourceAssignment.java`
Extends the standard `org.compiere.model.MResourceAssignment`.
*   **Overlap Check**: Implements `isOverlap()` to prevent double-booking of the same resource during the same time slot.

## 5. Data Flow Diagram

1.  **Load**: 
    *   User opens Form -> `BookingTimeline.initForm()`
    *   Java queries DB -> Generates JSON (`getBookingJSON`, `getResourceJSON`)
    *   Java calls `Clients.evalJavaScript("items = new vis.DataSet(...)")` to render frontend.

2.  **Create/Update**:
    *   User drags/creates event on Timeline/Week View.
    *   `booking.js` opens jQuery Dialog.
    *   User submits Dialog -> JS calls `zk.$("$bookingUpdated").fireOnChange()`.
    *   `BookingTimeline.onEvent()` intercepts event.
    *   Java parses JSON from `itemData`.
    *   Java saves to `S_ResourceAssignment`.
    *   If successful, Java re-queries and refreshes the view.

## 6. Installation & Configuration
The project is an OSGi Bundle.
*   **Manifest**: `META-INF/MANIFEST.MF` defines dependencies (`org.adempiere.ui.zk`, etc.).
*   **Factories**: Registered via `OSGI-INF` (FormFactory, ModelFactory).

