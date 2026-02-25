# Day View Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a Day View showing one day with each meeting room as its own column, reusing existing Week View CSS and JS drag logic.

**Architecture:** New `renderDayView()` method in `BookingTimeline.java` generates HTML using the same CSS classes (`.week-view-root`, `.day-col`, `.event-card`) as the Week View. Columns represent resources instead of days. All existing JS drag-and-drop works unchanged because it operates on `.day-col` elements via `data-date` and `data-resource-id` attributes.

**Tech Stack:** Java (ZK Framework), ZUL templates, inline CSS/JS

---

### Task 1: Add Day button to ZUL toolbar

**Files:**
- Modify: `WEB-INF/web/meetingroom.zul:17`

**Step 1: Add the button element**

In `meetingroom.zul`, after the Week button (line 17), add the Day button:

```xml
            <button id="btnViewWeek" label="Week" mold="os"/>
            <button id="btnViewDay" label="Day" mold="os"/>
            <separator bar="true"/>
```

**Step 2: Verify the ZUL file is valid**

Open the file and confirm the toolbar now has: Timeline, Week, Day, separator, <, Today, >.

**Step 3: Commit**

```bash
git add WEB-INF/web/meetingroom.zul
git commit -m "Add Day view button to toolbar"
```

---

### Task 2: Wire up Day button in Java — constant, field, initForm

**Files:**
- Modify: `src/tw/ninniku/booking/form/BookingTimeline.java:73,77-78,403-406,436-437`

**Step 1: Add field and constant**

At line 73, add `btnViewDay` to the button declaration:

```java
	private Button btnViewTimeline, btnViewWeek, btnViewDay;
```

At line 78 (after `VIEW_WEEK`), add:

```java
	private static final String VIEW_DAY = "DAY";
```

**Step 2: Initialize button in initForm()**

After `btnViewWeek.setDisabled(true);` (around line 406), add:

```java
		btnViewDay = (Button) component.getFellow("btnViewDay");
```

**Step 3: Register event listener**

After `btnViewWeek.addEventListener(Events.ON_CLICK, this);` (around line 437), add:

```java
		btnViewDay.addEventListener(Events.ON_CLICK, this);
```

**Step 4: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingTimeline.java
git commit -m "Wire up Day view button field, constant, and listener"
```

---

### Task 3: Handle Day button in event routing and view switching

**Files:**
- Modify: `src/tw/ninniku/booking/form/BookingTimeline.java` — methods: `onEvent`, `updateViewMode`, `navigateView`, `refreshView`

**Step 1: Add event handler in onEvent()**

After the `btnViewWeek` handler (around line 128), add:

```java
		} else if (event.getTarget() == btnViewDay) {
			updateViewMode(VIEW_DAY);
```

**Step 2: Update updateViewMode()**

Add `btnViewDay.setDisabled(...)` after `btnViewWeek`:

```java
	private void updateViewMode(String mode) {
		this.currentViewMode = mode;
		btnViewTimeline.setDisabled(mode.equals(VIEW_TIMELINE));
		btnViewWeek.setDisabled(mode.equals(VIEW_WEEK));
		btnViewDay.setDisabled(mode.equals(VIEW_DAY));
		refreshView();
	}
```

**Step 3: Update navigateView()**

Add DAY case before the else block:

```java
		if (VIEW_WEEK.equals(currentViewMode)) {
			cal.add(Calendar.WEEK_OF_YEAR, direction);
		} else if (VIEW_DAY.equals(currentViewMode)) {
			cal.add(Calendar.DAY_OF_YEAR, direction);
		} else {
			// Timeline default nav (maybe 1 week?)
			cal.add(Calendar.DAY_OF_YEAR, direction * 7);
		}
```

**Step 4: Update refreshView()**

Add DAY case after the WEEK case:

```java
		} else if (VIEW_WEEK.equals(currentViewMode)) {
			renderWeekView();
		} else if (VIEW_DAY.equals(currentViewMode)) {
			renderDayView();
		}
```

**Step 5: Add stub renderDayView()**

Add a temporary stub method (before `getResourceColor()`):

```java
	private void renderDayView() {
		// TODO: implement in next task
		Html zkHtml = new Html();
		zkHtml.setContent("<div style='padding:20px;'>Day View coming soon</div>");
		bookingContainer.appendChild(zkHtml);
	}
```

**Step 6: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingTimeline.java
git commit -m "Handle Day view in event routing, navigation, and view switching"
```

---

### Task 4: Implement renderDayView() method

**Files:**
- Modify: `src/tw/ninniku/booking/form/BookingTimeline.java` — replace stub `renderDayView()`

**Step 1: Replace the stub with the full implementation**

Replace the stub `renderDayView()` with the following. This method mirrors `renderWeekView()` but iterates over resources (rooms) as columns instead of days:

```java
	private void renderDayView() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(currentViewDate);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		Timestamp start = new Timestamp(cal.getTimeInMillis());

		cal.add(Calendar.DAY_OF_YEAR, 1);
		Timestamp end = new Timestamp(cal.getTimeInMillis());

		List<MResourceAssignment> bookings = fetchBookings(start, end);

		// Initialize resource name map
		Map<Integer, String> resourceNameMap = new HashMap<>();
		if (groups != null) {
			for (Group g : groups) {
				try {
					resourceNameMap.put(Integer.valueOf(g.getId()), g.getContent());
				} catch (NumberFormatException e) {
					// Ignore invalid IDs
				}
			}
		}

		StringBuilder html = new StringBuilder();

		// Determine view range based on toggle
		boolean workHoursOnly = chkWorkHours != null && chkWorkHours.isChecked();
		int startHour = workHoursOnly ? 8 : 0;
		int endHour = workHoursOnly ? 18 : 23;
		int hourCount = endHour - startHour + 1;
		int pxPerHour = 40;
		int heightPx = hourCount * pxPerHour;

		SimpleDateFormat sdfKey = new SimpleDateFormat("yyyy-MM-dd");
		SimpleDateFormat sdfTitle = new SimpleDateFormat("yyyy/MM/dd (EEE)");
		String dayKey = sdfKey.format(currentViewDate);

		html.append("<div class='week-view-root' data-start-hour='" + startHour + "'>");

		// Header — date title + resource names
		html.append("<div class='week-header'>");
		html.append("<div class='header-time-spacer' style='font-size:11px; line-height:40px;'>"
				+ sdfTitle.format(currentViewDate) + "</div>");

		if (groups != null) {
			for (Group g : groups) {
				String color = getResourceColor(Integer.valueOf(g.getId()));
				html.append("<div class='header-day' style='color:" + color + "; font-weight:bold;'>")
						.append(g.getContent()).append("</div>");
			}
		}
		html.append("</div>"); // End Header

		html.append("<div class='scroll-body'>");
		html.append("<div class='week-layout' style='min-height: " + heightPx + "px;'>");

		// Time Column
		html.append("<div class='time-col'>");
		for (int i = startHour; i <= endHour; i++) {
			html.append("<div class='time-slot'>").append(String.format("%02d:00", i)).append("</div>");
		}
		html.append("</div>");

		// Resource columns
		html.append("<div class='days-grid'>");

		boolean canWrite = isWritable();
		int adUserId = Env.getContextAsInt(Env.getCtx(), "#AD_User_ID");

		if (groups != null) {
			for (Group g : groups) {
				int resId = Integer.valueOf(g.getId());
				html.append("<div class='day-col' data-date='" + dayKey + "' data-resource-id='" + resId
						+ "' style='height: " + heightPx + "px;'>");

				// Filter events for this resource
				List<MResourceAssignment> resEvents = new ArrayList<>();
				for (MResourceAssignment b : bookings) {
					if (b.getS_Resource_ID() == resId) {
						resEvents.add(b);
					}
				}

				// Sort by start time, then end time
				Collections.sort(resEvents, new Comparator<MResourceAssignment>() {
					public int compare(MResourceAssignment o1, MResourceAssignment o2) {
						int val = o1.getAssignDateFrom().compareTo(o2.getAssignDateFrom());
						if (val == 0)
							return o2.getAssignDateTo().compareTo(o1.getAssignDateTo());
						return val;
					}
				});

				// Pack into columns (greedy overlap algorithm)
				List<List<MResourceAssignment>> columns = new ArrayList<>();
				for (MResourceAssignment evt : resEvents) {
					boolean placed = false;
					for (List<MResourceAssignment> col : columns) {
						MResourceAssignment last = col.get(col.size() - 1);
						if (evt.getAssignDateFrom().getTime() >= last.getAssignDateTo().getTime()) {
							col.add(evt);
							placed = true;
							break;
						}
					}
					if (!placed) {
						List<MResourceAssignment> newCol = new ArrayList<>();
						newCol.add(evt);
						columns.add(newCol);
					}
				}

				// Render event cards
				int numCols = columns.size();
				double colWidthPercent = 95.0 / (numCols > 0 ? numCols : 1);

				for (int colIndex = 0; colIndex < numCols; colIndex++) {
					List<MResourceAssignment> col = columns.get(colIndex);
					for (MResourceAssignment b : col) {
						long startMs = b.getAssignDateFrom().getTime();
						long endMs = b.getAssignDateTo().getTime();

						Calendar dayStart = Calendar.getInstance();
						dayStart.setTime(b.getAssignDateFrom());
						dayStart.set(Calendar.HOUR_OF_DAY, startHour);
						dayStart.set(Calendar.MINUTE, 0);
						dayStart.set(Calendar.SECOND, 0);

						long offsetMs = startMs - dayStart.getTimeInMillis();
						long durationMs = endMs - startMs;

						double pxPerMin = 40.0 / 60.0;
						double top = (offsetMs / 60000.0) * pxPerMin;
						double height = (durationMs / 60000.0) * pxPerMin;
						if (height < 15)
							height = 15;

						double left = 2.0 + (colIndex * colWidthPercent);
						double width = colWidthPercent - 2.0;

						MUser user = new MUser(Env.getCtx(), b.getCreatedBy(), null);

						String resName = resourceNameMap.getOrDefault(b.getS_Resource_ID(), "");
						if (!resName.isEmpty())
							resName = "[" + resName + "] ";
						String title = resName + b.getName() + " (" + user.getName() + ")";

						String color = getResourceColor(b.getS_Resource_ID());
						String displayContent = title;
						if (b.getDescription() != null && !b.getDescription().isEmpty()) {
							displayContent += "<br/><span style='font-size:10px; opacity:0.9;'>"
									+ b.getDescription() + "</span>";
						}

						boolean isOwnerOrAdmin = canWrite || b.getCreatedBy() == adUserId;
						String deleteIconHtml = "";
						if (isOwnerOrAdmin) {
							deleteIconHtml = String.format(
									"<span class='delete-icon' onclick='window.onWeekEventDelete(event, %d)'>&times;</span>",
									b.getS_ResourceAssignment_ID());
						}

						String nameJS = b.getName().replace("\\", "\\\\").replace("'", "\\'").replace("\"",
								"\\\"");
						String descJS = "";
						if (b.getDescription() != null) {
							descJS = b.getDescription().replace("\r", "").replace("\n", " ")
									.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
						}

						String editableClass = isOwnerOrAdmin ? "editable" : "";
						String resizeHandleHtml = isOwnerOrAdmin ? "<div class='resize-handle'></div>" : "";

						html.append(String.format(
								"<div class='event-card %s' style='top:%.1fpx; height:%.1fpx; background-color:%s; width:%.1f%%; left:%.1f%%;' "
										+ "data-id='%d' data-resource-id='%d' data-start-ms='%d' data-end-ms='%d' "
										+ "onclick=\"onWeekEventClick(event, '%s', '%s', '%s', '%s', %s, %s);\">"
										+ "%s%s%s</div>",
								editableClass, top, height, color, width, left,
								b.getS_ResourceAssignment_ID(), b.getS_Resource_ID(), startMs, endMs,
								b.getS_ResourceAssignment_ID(), nameJS, descJS,
								b.getS_Resource_ID(), startMs, endMs, displayContent, deleteIconHtml,
								resizeHandleHtml));
					}
				}

				html.append("</div>"); // End day-col (resource column)
			}
		}

		// Current time indicator
		html.append("<div class='current-time-line'></div>");

		html.append("</div>"); // days-grid
		html.append("</div>"); // week-layout
		html.append("</div>"); // scroll-body
		html.append("</div>"); // root

		Html zkHtml = new Html();
		zkHtml.setHflex("1");
		zkHtml.setVflex("1");
		zkHtml.setContent(html.toString());
		bookingContainer.appendChild(zkHtml);
	}
```

**Step 2: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingTimeline.java
git commit -m "Implement renderDayView with resource columns"
```

---

### Task 5: Widen time-spacer for date label in Day View

**Files:**
- Modify: `WEB-INF/web/meetingroom.zul:38`

**Step 1: Adjust CSS for the header-time-spacer**

The date label (e.g., "2025/02/25 (Wed)") needs more space than the "60px" time spacer. Update the `.header-time-spacer` to use `min-width` instead of a fixed `width`, or adjust the Day View header to use inline style override. Since the Week View also uses this spacer (and it's fine at 60px there), the best approach is the inline style already applied in `renderDayView()` — the `style='font-size:11px; line-height:40px;'` on the spacer div.

However, the 60px width is too narrow for the date text. Update the CSS to allow text overflow:

In `meetingroom.zul` line 38, change:

```css
.header-time-spacer { width: 60px; min-width: 60px; border-right: 1px solid #eee; background: #f9f9f9; }
```

to:

```css
.header-time-spacer { width: 60px; min-width: 60px; border-right: 1px solid #eee; background: #f9f9f9; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 10px; line-height: 40px; text-align: center; }
```

**Step 2: Commit**

```bash
git add WEB-INF/web/meetingroom.zul
git commit -m "Improve header-time-spacer CSS for Day View date label"
```

---

### Task 6: Manual verification checklist

This is a manual testing task — deploy and verify in the browser.

**Step 1: Verify toolbar**

- [ ] Timeline, Week, Day buttons all visible
- [ ] Clicking Day disables the Day button, enables others
- [ ] Clicking Week/Timeline switches correctly

**Step 2: Verify Day View layout**

- [ ] Resource columns display with room names in header
- [ ] Header shows date (e.g., "2025/02/25 (Wed)")
- [ ] Time slots display correctly (08:00-18:00 when "Only Work Hours" checked)
- [ ] Unchecking "Only Work Hours" shows 00:00-23:00

**Step 3: Verify interactions**

- [ ] Click empty slot → add dialog opens with correct room pre-selected
- [ ] Drag to create time range → ghost appears, dialog opens
- [ ] Drag event between room columns → room changes on save
- [ ] Resize event → end time adjusts
- [ ] Click event → edit dialog opens
- [ ] Delete (x) → confirmation, then removed
- [ ] Current time red line visible

**Step 4: Verify navigation**

- [ ] Prev (<) moves back 1 day
- [ ] Next (>) moves forward 1 day
- [ ] Today jumps to current date

**Step 5: Final commit with version bump**

```bash
git add -A
git commit -m "Day View: complete implementation with resource columns"
git push
```
