/**
 *  Ninniku IT Hub 
 */

var isload = false;
var items;
var groups;
var options;
var timeline;
var container;
var cStart;

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
	hiddenDates: hiddenDates = [
		{ start: '2023-05-16 18:00:00', end: '2023-05-17 08:00:00', repeat: 'daily' },
		{ start: '2023-07-22 00:00:00', end: '2023-07-24 00:00:00', repeat: 'weekly' }
	],
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

// Global functions for custom views
window.openCustomAddDialog = function (dateStr, minutesOffset, resourceId) {
	if (!resourceId) {
		resourceId = (groups && groups.length > 0) ? groups[0].id : 0;
	}

	var date = new Date(dateStr);
	// Add minutes offset to get precise start time
	date.setMinutes(date.getMinutes() + minutesOffset);

	var endDate = new Date(date);
	endDate.setHours(endDate.getHours() + 1);

	var item = {
		start: date,
		end: endDate,
		group: resourceId,
		content: ''
	};
	clickNew(item, null);
};

window.openCustomEditDialog = function (id, name, desc, resourceId, startMs, endMs) {
	var item = {
		id: id,
		s_booking_id: id,
		name: name,
		content: name,
		description: desc,
		group: resourceId,
		start: new Date(startMs),
		end: new Date(endMs)
	};
	openEditDialog(item, null);
};

function toTimestamp(value) {
	const date = new Date(value);
	return date.getTime();
}
