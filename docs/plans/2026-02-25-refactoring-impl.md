# BookingTimeline Refactoring Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Clean up dead code/bugs and extract shared rendering logic to eliminate ~120 lines of duplication between `renderWeekView()` and `renderDayView()`.

**Architecture:** Extract 3 helper methods from the duplicated code blocks, keeping each render method's unique logic intact.

**Tech Stack:** Java (ZK Framework)

---

### Task 1: Remove dead code and unused imports

**Files:**
- Modify: `src/tw/ninniku/booking/form/BookingTimeline.java`

**Step 1: Remove unused imports**

Delete these import lines:
```java
import org.adempiere.webui.theme.ThemeManager;
import org.zkoss.zk.ui.http.ExecutionImpl;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
```

**Step 2: Delete dead `convertTimestamp()` method (lines 360-367)**

Remove the entire method:
```java
	private Timestamp convertTimestamp(String dateString) {
		LocalDateTime localDateTime = LocalDateTime.parse(dateString);
		long timestamp = localDateTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
		return new Timestamp(timestamp);
	}
```

**Step 3: Delete dead `draw()` method (lines 502-506)**

Remove the entire method:
```java
	private void draw(int delay) {
		String cmd = " setTimeout(function(){ drawChart();}," + delay + ");";
		Clients.evalJavaScript(cmd);
	}
```

**Step 4: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingTimeline.java
git commit -m "Remove dead code: convertTimestamp, draw, unused imports"
```

---

### Task 2: Fix minor bugs and code hygiene

**Files:**
- Modify: `src/tw/ninniku/booking/form/BookingTimeline.java`

**Step 1: Remove duplicate getFellow calls**

In `initForm()`, lines 418-419 are duplicates of 416-417. Remove:
```java
		btnPrev = (Button) component.getFellow("btnPrev");
		btnNext = (Button) component.getFellow("btnNext");
```
(the second pair only)

**Step 2: Remove duplicate trx.commit()**

In `updateBooking(JSONObject)`, line 348 is a duplicate. Remove:
```java
			// Print a message indicating a successful commit
			trx.commit();
```

**Step 3: Make errorMessage private**

Change:
```java
	public String errorMessage = "";
```
to:
```java
	private String errorMessage = "";
```

**Step 4: Remove unused variable `code` in getResourceJSON()**

In `getResourceJSON()`, remove:
```java
				String code = rs.getString("value");
```

**Step 5: Remove unused variable `list` in addResourceTypeItem()**

In `addResourceTypeItem()`, remove:
```java
		ArrayList<Item> list = new ArrayList<Item>();
```

**Step 6: Clean up stale comments**

- Remove `// TODO Auto-generated method stub` in `valueChange()`
- Remove `// MQTT.thread(...)` comment in `onEvent()`

**Step 7: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingTimeline.java
git commit -m "Fix duplicate getFellow, double commit, clean up unused vars and stale comments"
```

---

### Task 3: Extract buildResourceNameMap() helper

**Files:**
- Modify: `src/tw/ninniku/booking/form/BookingTimeline.java`

**Step 1: Add the helper method**

Add before `getResourceColor()`:
```java
	private Map<Integer, String> buildResourceNameMap() {
		Map<Integer, String> map = new HashMap<>();
		if (groups != null) {
			for (Group g : groups) {
				try {
					map.put(Integer.valueOf(g.getId()), g.getContent());
				} catch (NumberFormatException e) {
					// Ignore invalid IDs
				}
			}
		}
		return map;
	}
```

**Step 2: Replace in renderWeekView()**

Replace the resource name map block (lines ~632-642) with:
```java
		Map<Integer, String> resourceNameMap = buildResourceNameMap();
```

**Step 3: Replace in renderDayView()**

Replace the resource name map block (lines ~849-858) with:
```java
		Map<Integer, String> resourceNameMap = buildResourceNameMap();
```

**Step 4: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingTimeline.java
git commit -m "Extract buildResourceNameMap() helper to reduce duplication"
```

---

### Task 4: Extract sortAndPackEvents() helper

**Files:**
- Modify: `src/tw/ninniku/booking/form/BookingTimeline.java`

**Step 1: Add the helper method**

Add before `getResourceColor()`:
```java
	private List<List<MResourceAssignment>> sortAndPackEvents(List<MResourceAssignment> events) {
		// Sort by start time, then by end time descending
		Collections.sort(events, new Comparator<MResourceAssignment>() {
			public int compare(MResourceAssignment o1, MResourceAssignment o2) {
				int val = o1.getAssignDateFrom().compareTo(o2.getAssignDateFrom());
				if (val == 0)
					return o2.getAssignDateTo().compareTo(o1.getAssignDateTo());
				return val;
			}
		});

		// Pack into columns (simple greedy algorithm)
		List<List<MResourceAssignment>> columns = new ArrayList<>();
		for (MResourceAssignment evt : events) {
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
		return columns;
	}
```

**Step 2: Replace in renderWeekView()**

Replace sort block + pack block (~lines 701-728) with:
```java
			List<List<MResourceAssignment>> columns = sortAndPackEvents(dayEvents);
```

**Step 3: Replace in renderDayView()**

Replace sort block + pack block (~lines 929-955) with:
```java
				List<List<MResourceAssignment>> columns = sortAndPackEvents(resEvents);
```

**Step 4: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingTimeline.java
git commit -m "Extract sortAndPackEvents() helper to reduce duplication"
```

---

### Task 5: Extract renderEventCards() helper

**Files:**
- Modify: `src/tw/ninniku/booking/form/BookingTimeline.java`

**Step 1: Add the helper method**

This is the largest extraction. Add before `getResourceColor()`:

```java
	private void renderEventCards(StringBuilder html, List<List<MResourceAssignment>> columns, int startHour,
			Map<Integer, String> resourceNameMap) {
		int numCols = columns.size();
		double colWidthPercent = 95.0 / (numCols > 0 ? numCols : 1);

		boolean canWrite = isWritable();
		int adUserId = Env.getContextAsInt(Env.getCtx(), "#AD_User_ID");

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
					displayContent += "<br/><span style='font-size:10px; opacity:0.9;'>" + b.getDescription()
							+ "</span>";
				}

				boolean isOwnerOrAdmin = canWrite || b.getCreatedBy() == adUserId;
				String deleteIconHtml = "";
				if (isOwnerOrAdmin) {
					deleteIconHtml = String.format(
							"<span class='delete-icon' onclick='window.onWeekEventDelete(event, %d)'>&times;</span>",
							b.getS_ResourceAssignment_ID());
				}

				String nameJS = b.getName().replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
				String descJS = "";
				if (b.getDescription() != null) {
					descJS = b.getDescription().replace("\r", "").replace("\n", " ").replace("\\", "\\\\")
							.replace("'", "\\'").replace("\"", "\\\"");
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
						b.getS_Resource_ID(), startMs, endMs, displayContent, deleteIconHtml, resizeHandleHtml));
			}
		}
	}
```

**Step 2: Replace in renderWeekView()**

Replace the event card rendering block (~lines 730-809) with:
```java
			renderEventCards(html, columns, startHour, resourceNameMap);
```

**Step 3: Replace in renderDayView()**

Replace the event card rendering block (~lines 958-1031) with:
```java
				renderEventCards(html, columns, startHour, resourceNameMap);
```

**Step 4: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingTimeline.java
git commit -m "Extract renderEventCards() helper to eliminate event card duplication"
```

---

### Task 6: Verify and push

**Step 1: Review final file**

Read `BookingTimeline.java` to verify:
- No compile errors (matching braces, correct method signatures)
- `renderWeekView()` and `renderDayView()` are significantly shorter
- All 3 helpers are used by both render methods
- No dead code remains

**Step 2: Push**

```bash
git push
```
