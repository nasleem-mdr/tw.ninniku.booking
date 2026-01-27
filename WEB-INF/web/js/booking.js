/**
 *  Ninniku IT Hub 
 */

var isload = false;
console.log('booking.js loaded - v3 Check');
// Global functions for custom views


/* window.weekViewScrollTo8Am moved to booking_weekview.js */

var items;
var groups;
var options;
var timeline;
var container;
var cStart;
// Define hiddenDates globally
var hiddenDates = [
	{ start: '2023-05-16 18:00:00', end: '2023-05-17 08:00:00', repeat: 'daily' },
	{ start: '2023-07-22 00:00:00', end: '2023-07-24 00:00:00', repeat: 'weekly' }
];

options = {
	locale: 'en',
	orientation: 'both',
	zoomMin: 86400000,
	zoomMax: 2678400000,
	xss: { disabled: true },
	showTooltips: true,
	tooltip: {
		followMouse: true,
		overflowMethod: 'flip'
	},
	hiddenDates: hiddenDates,
	editable: {
		add: true,
		remove: true,
		updateGroup: true,
		updateTime: true,
		overrideItems: false
	},
	onAdd: function (item, callback) {

		let date = new Date(item.start);
		date.setHours(date.getHours() + 1);
		item.end = date.getTime();
		clickNew(item, callback);
		//callback(item); // send back adjusted new item
	},

	onMove: function (item, callback) {
		item.startTimestamp = item.start.getTime().toString();
		item.endTimestamp = item.end.getTime().toString();
		zk.$("$itemData").setValue(JSON.stringify(item));
		zk.$("$itemData").fireOnChange();
		zk.$("$dateLast").setValue(Date.now().toString());
		cStart = item.start;
		zk.$("$dateLast").fireOnChange();

		callback(item);
	},

	onMoving: function (item, callback) {
		callback(item); // send back the (possibly) changed item

	},

	onUpdate: function (item, callback) {
		openEditDialog(item, callback);
	},

	onRemove: function (item, callback) {
		//callback(null); // cancel deletion
		zk.$("$itemData").setValue(JSON.stringify(item));
		zk.$("$itemData").fireOnChange();

		zk.$("$bookingDeleted").setValue(Date.now().toString());
		zk.$("$bookingDeleted").fireOnChange();
		callback(item);
	}
};
console.log('Options defined');

function initChart() {
	if (typeof vis === 'undefined' || typeof $ === 'undefined') {
		setTimeout(initChart, 100);
		return;
	}

	container = document.getElementById("booking-chart");
	if (container) {
		//container = $("#booking-chart");
		timeline = new vis.Timeline(container, items, groups, options);
		$("#loading").hide();

		var today = new Date();

		// Add 5 days to today's date
		var fiveDaysLater = new Date(today);
		fiveDaysLater.setDate(today.getDate() + 10);
		//timeline.moveTo(new Date());
		timeline.setWindow(today, fiveDaysLater);

		//timeline.focus("1000014");
	}

	updateMeetingRoomSelector();
	showBeforeDate();

	$("#is-weekly").click(function () {
		showBeforeDate();
	});
}
function showBeforeDate() {
	if ($("#is-weekly").is(':checked')) {
		$(".before-date").show();
	} else {
		$(".before-date").hide();
	}
}
function drawChart() {
	if (timeline) {
		timeline.setData({ groups: groups, items: items });
		timeline.redraw();

		var today = new Date();

		// Add 5 days to today's date
		var fiveDaysLater = new Date(today);
		fiveDaysLater.setDate(today.getDate() + 10);
		//timeline.moveTo(new Date());
		timeline.setWindow(today, fiveDaysLater);
	}
}
function convertFormToJSON(form) {
	const array = $(form).serializeArray(); // Encodes the set of form elements as an array of names and values.
	const json = {};
	$.each(array, function () {
		json[this.name] = this.value || "";
	});
	return json;
}

function reload() {

}

function updateMeetingRoomSelector() {
	var select = document.getElementById("s_resource_id");
	if (select) {
		select.options.length = 0;
		if (groups) {
			groups.forEach(function (element) {

				var option = document.createElement("option");
				option.value = element.id.toString();
				option.text = element.content;
				select.appendChild(option);

			});
		}
	}
}

function openEditDialog(item, callback) {
	$(".weekly").hide();

	$("#s_resource_id").val(item.group);
	$("#s_booking_id").val(item.s_booking_id);
	$("#group").val(item.group);
	$("#booking-name").val(item.name ? item.name : item.content); // Handle varied naming
	$("#description").val(item.description);

	let start = new Date(item.start);
	// Handle timezone offset if not already handled
	// start.setHours(start.getHours() - (start.getTimezoneOffset() / 60)); 
	// Note: It seems the original code did manual offset adjustment. Keeping it consistent.
	// However, if called from external views passed as strings/timestamp, we might need care.
	// Assuming 'item.start' is Date object or ISO string.
	if (!(start instanceof Date) || isNaN(start)) start = new Date(item.start);

	start.setHours(start.getHours() - (start.getTimezoneOffset() / 60));

	let end = new Date(item.end);
	if (!(end instanceof Date) || isNaN(end)) end = new Date(item.end);
	end.setHours(end.getHours() - (end.getTimezoneOffset() / 60));

	$("#assign-date-from").val(start.toISOString().slice(0, 16));
	$("#assign-date-to").val(end.toISOString().slice(0, 16));

	$("#update-form").dialog({
		title: "修改預約單",
		modal: true,
		width: "500px",
		buttons: {
			"Delete": function () {
				if (confirm("Are you sure you want to delete this booking?")) {
					$(this).dialog("close");

					// Ensure item has id for deletion
					if (!item.id && item.s_booking_id) item.id = item.s_booking_id;

					zk.$("$itemData").setValue(JSON.stringify(item));
					zk.$("$itemData").fireOnChange();

					zk.$("$bookingDeleted").setValue(Date.now().toString());
					zk.$("$bookingDeleted").fireOnChange();

					if (callback) callback(null); // Remove from timeline if present
				}
			},
			Ok: function () {

				var bName = $("#booking-name").val();
				var bDesc = $("#description").val();
				if (!bName || bName.trim() === "") {
					alert("Please enter a Subject (Name).");
					return;
				}
				if (!bDesc || bDesc.trim() === "") {
					alert("Please enter a Memo (Description).");
					return;
				}

				$(this).dialog("close");
				if (item) {
					item.description = $("#description").val();
					item.group = $("#group").val();
				}
				$("#assign-date-from-timestamp").val(toTimestamp($("#assign-date-from").val()));
				$("#assign-date-to-timestamp").val(toTimestamp($("#assign-date-to").val()));
				const json = convertFormToJSON($("#booking-form"));
				/**
				將Form 資料轉換成 json 讓後端處理
				 */
				if (!json.hasOwnProperty("s_booking_id") || json["s_booking_id"] === "") {
					var val = $("#s_booking_id").val();
					if (val === undefined || val === null) {
						val = "0";
					}
					json["s_booking_id"] = val;
				}
				zk.$("$itemData").setValue(JSON.stringify(json));
				zk.$("$itemData").fireOnChange();
				zk.$("$bookingUpdated").setValue(Date.now().toString());
				zk.$("$bookingUpdated").fireOnChange();

				if (callback) callback(item);

			}, Cancel: function () {
				$(this).dialog("close");
				if (callback) callback(null);
			}
		}
	});
}

// Expose to window for external access (e.g. from booking_weekview.js)
window.openEditDialog = openEditDialog;

function clickNew(item, callback) {

	updateMeetingRoomSelector();
	$(".weekly").show();
	$('#is-weekly').removeAttr('checked');
	showBeforeDate();

	var item = item;
	var callback = callback;
	let start = new Date(item.start);
	start.setHours(start.getHours() - (start.getTimezoneOffset() / 60));
	let end = new Date(item.end);
	end.setHours(end.getHours() - (end.getTimezoneOffset() / 60));

	// Default name empty for new
	$("#booking-name").val("");
	$("#description").val("");

	$("#assign-date-from").val(start.toISOString().slice(0, 16));
	$("#assign-date-to").val(end.toISOString().slice(0, 16));
	$("#s_resource_id").val(item.group);
	$("#s_booking_id").val(0);
	$("#update-form").dialog({
		title: "新增預約單",
		modal: true,
		width: "500px",
		buttons: {
			Ok: function () {

				var bName = $("#booking-name").val();
				var bDesc = $("#description").val();
				if (!bName || bName.trim() === "") {
					alert("Please enter a Subject (Name).");
					return;
				}
				if (!bDesc || bDesc.trim() === "") {
					alert("Please enter a Memo (Description).");
					return;
				}

				$(this).dialog("close");


				$("#assign-date-from-timestamp").val(toTimestamp($("#assign-date-from").val()));
				$("#assign-date-to-timestamp").val(toTimestamp($("#assign-date-to").val()));
				$("#repeat-date-to-timestamp").val(toTimestamp($("#repeat-date-to").val()));

				const json = convertFormToJSON($("#booking-form"));
				//item.content = $("#booking-name").val();

				/**
				将Form 資料轉換成 json 讓後端處理
				 */
				if (!json.hasOwnProperty("s_booking_id") || json["s_booking_id"] === "") {
					var val = $("#s_booking_id").val();
					if (val === undefined || val === null) {
						val = "0";
					}
					json["s_booking_id"] = val;
				}
				zk.$("$itemData").setValue(JSON.stringify(json));
				zk.$("$itemData").fireOnChange();
				zk.$("$bookingUpdated").setValue(Date.now().toString());
				zk.$("$bookingUpdated").fireOnChange();

				if (callback) callback(item);
			}, Cancel: function (i) {

				$(this).dialog("close");
				if (callback) callback(null);
			}
		}
	});
}

// Expose to window for external access
window.clickNew = clickNew;



/**
 * Week View Drag and Drop Event Initialization
 */
/* Week View Logic - Merged from booking_weekview.js for reliability */
console.log('DEBUG: Start of Merged Week View Logic');

window.openCustomAddDialog = function (dateStr, minutesOffset, resourceId) {
	console.log('DEBUG: openCustomAddDialog called', dateStr, minutesOffset, resourceId);
	if (!resourceId) {
		resourceId = (window.groups && window.groups.length > 0) ? window.groups[0].id : 0;
	}
	var date = new Date(dateStr);
	date.setMinutes(date.getMinutes() + minutesOffset);
	var endDate = new Date(date);
	endDate.setHours(endDate.getHours() + 1);
	var item = { start: date, end: endDate, group: resourceId, content: '' };

	if (typeof window.clickNew === 'function') {
		window.clickNew(item, function () { });
	} else {
		console.error("clickNew not defined");
	}
};

window.openCustomEditDialog = function (id, name, desc, resourceId, startMs, endMs) {
	var item = {
		id: id, s_booking_id: id, name: name, content: name, description: desc,
		group: resourceId, start: new Date(startMs), end: new Date(endMs)
	};
	if (typeof window.openEditDialog === 'function') window.openEditDialog(item, function () { });
};

window.openCustomAddDialogRange = function (startMs, endMs, resourceId) {
	if (!resourceId) {
		resourceId = (window.groups && window.groups.length > 0) ? window.groups[0].id : 0;
	}
	var start = new Date(startMs);
	var end = new Date(endMs);
	var item = { start: start, end: end, group: resourceId, content: '' };
	if (typeof window.clickNew === 'function') window.clickNew(item, function () { });
	else console.error("clickNew not defined");
};

var _weekViewWasDragging = false;
window.onWeekDayClick = function (event, elem, dayKey, resourceId) {
	if (_weekViewWasDragging) { _weekViewWasDragging = false; return; }
	var rect = elem.getBoundingClientRect();
	var min = (event.clientY - rect.top) / 40 * 60;
	if (window.openCustomAddDialog) window.openCustomAddDialog(dayKey, min, resourceId);
};

window.onWeekEventClick = function (event, id, name, desc, resId, startMs, endMs) {
	event.stopPropagation();
	if (window.openCustomEditDialog) window.openCustomEditDialog(id, name, desc, resId, startMs, endMs);
};

window.weekViewScrollTo8Am = function () {
	setTimeout(function () {
		var el = document.querySelector('.scroll-body');
		if (el) el.scrollTop = 320;
	}, 200);
};

window.onWeekEventDelete = function (event, id) {
	event.stopPropagation();
	if (confirm('Are you sure you want to delete this booking?')) {
		// Find ZK widgets for hidden fields
		var itemDataWidget = zk.Widget.$('$itemData');
		var boookingDeletedWidget = zk.Widget.$('$bookingDeleted');

		var json = JSON.stringify({ id: String(id) });

		if (itemDataWidget && boookingDeletedWidget) {
			itemDataWidget.setValue(json);
			itemDataWidget.fireOnChange();

			boookingDeletedWidget.setValue('DELETE-' + id);
			boookingDeletedWidget.fireOnChange();
		} else {
			// JQuery Fallback
			var $ = window.$ || window.jQuery || window.jq;
			$('#itemData').val(json).trigger('change');
			$('#bookingDeleted').val('DELETE-' + id).trigger('change');
		}
	}
};

/* Drag and Drop Logic */
(function () {
	var $ = window.$ || window.jQuery || window.jq;

	function getTimeFromY(y, dayKey) {
		var mins = (y / 40) * 60;
		var date = new Date(dayKey + 'T00:00:00');
		date.setMinutes(date.getMinutes() + mins);
		return date;
	}

	var dragStartData = null;
	var dragGhost = null;
	var dragTooltip = null;
	var isDragging = false;

	function snapTo30(d) {
		var m = d.getMinutes();
		var r = Math.round(m / 30) * 30;
		d.setMinutes(r, 0, 0);
		return d;
	}

	function formatTime(date) {
		var h = date.getHours();
		var m = date.getMinutes();
		return (h < 10 ? '0' + h : h) + ':' + (m < 10 ? '0' + m : m);
	}

	function formatDuration(diffMs) {
		var diffMins = Math.round(diffMs / 60000);
		var h = Math.floor(diffMins / 60);
		var m = diffMins % 60;
		var str = '';
		if (h > 0) str += h + 'h ';
		if (m > 0 || h === 0) str += m + 'm';
		return str.trim();
	}

	function initDragEvents() {
		$ = window.$ || window.jQuery || window.jq;
		if (typeof $ === 'undefined') {
			setTimeout(initDragEvents, 100);
			return;
		}

		$(document).off('mousedown', '.day-col');
		$(document).off('mousemove.weekview');
		$(document).off('mouseup.weekview');

		$(document).on('mousedown', '.day-col', function (e) {
			if ($(e.target).closest('.event-card').length > 0) return;
			e.preventDefault();

			var col = $(this);
			var rect = this.getBoundingClientRect();
			var offsetY = e.clientY - rect.top;

			var dayKey = col.data('date') || col.attr('data-date');
			var resId = col.data('resource-id') || col.attr('data-resource-id');

			if (!dayKey) return;

			dragStartData = {
				col: col,
				startY: offsetY,
				dayKey: dayKey,
				resId: resId
			};
			isDragging = false;
			_weekViewWasDragging = false;
			$('.drag-tooltip').remove();
			dragTooltip = null;
		});

		$(document).on('mousemove.weekview', function (e) {
			if (!dragStartData) return;

			var col = dragStartData.col;
			var rect = col[0].getBoundingClientRect();
			var currentY = e.clientY - rect.top;

			if (!isDragging && Math.abs(currentY - dragStartData.startY) > 5) {
				isDragging = true;
				if ($('.drag-ghost').length === 0) {
					dragGhost = $('<div class="drag-ghost"></div>');
					col.append(dragGhost);
				} else {
					dragGhost = $('.drag-ghost');
				}
				if ($('.drag-tooltip').length === 0) {
					dragTooltip = $('<div class="drag-tooltip"></div>');
					$('body').append(dragTooltip);
				} else {
					dragTooltip = $('.drag-tooltip');
				}
			}

			if (isDragging) {
				var top = Math.min(dragStartData.startY, currentY);
				var height = Math.abs(currentY - dragStartData.startY);
				dragGhost.css({
					top: top + 'px',
					height: height + 'px'
				});

				var startY = Math.min(dragStartData.startY, currentY);
				var endY = Math.max(dragStartData.startY, currentY);
				if (endY - startY < 10) endY = startY + 10;

				var start = getTimeFromY(startY, dragStartData.dayKey);
				var end = getTimeFromY(endY, dragStartData.dayKey);
				start = snapTo30(start);
				end = snapTo30(end);
				if (end.getTime() <= start.getTime()) {
					end = new Date(start.getTime() + 30 * 60000);
				}

				var timeStr = formatTime(start) + ' - ' + formatTime(end);
				var durStr = '(' + formatDuration(end - start) + ')';
				dragTooltip.text(timeStr + ' ' + durStr);
				dragTooltip.css({
					top: (e.clientY - 40) + 'px',
					left: (e.clientX + 15) + 'px'
				});
			}
		});

		$(document).on('mouseup.weekview', function (e) {
			if (!dragStartData) return;

			if (isDragging) {
				var col = dragStartData.col;
				var rect = col[0].getBoundingClientRect();
				var currentY = e.clientY - rect.top;
				var startY = Math.min(dragStartData.startY, currentY);
				var endY = Math.max(dragStartData.startY, currentY);
				if (endY - startY < 10) endY = startY + 10;

				var start = getTimeFromY(startY, dragStartData.dayKey);
				var end = getTimeFromY(endY, dragStartData.dayKey);
				start = snapTo30(start);
				end = snapTo30(end);
				if (end.getTime() <= start.getTime()) {
					end = new Date(start.getTime() + 30 * 60000);
				}

				if (window.openCustomAddDialogRange) {
					window.openCustomAddDialogRange(start.getTime(), end.getTime(), dragStartData.resId);
				}
				$('.drag-ghost').remove();
				dragGhost = null;
				_weekViewWasDragging = true;
			}

			$('.drag-tooltip').remove();
			dragTooltip = null;
			dragStartData = null;
			isDragging = false;
			setTimeout(function () { _weekViewWasDragging = false; }, 100);
		});
	}

	initDragEvents();
})();
console.log('DEBUG: End of booking.js - Loaded Successfully');
