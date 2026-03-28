/**
 *  Ninniku IT Hub
 */

var BookingApp = BookingApp || {};
BookingApp.Timeline = (function () {

    // ── ZK event bridge ──────────────────────────────────────────────────────
    function sendZkBookingEvent(eventName, data) {
        var vmContainer = zk.Widget.$('$bookingVMContainer');
        if (vmContainer) {
            zAu.send(new zk.Event(vmContainer, eventName, data));
        } else {
            console.warn('bookingVMContainer not found for event:', eventName);
        }
    }

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
		var json = JSON.stringify({
			id: String(item.id),
			group: String(item.group),
			startTimestamp: String(item.start.getTime()),
			endTimestamp: String(item.end.getTime())
		});
		var vmContainer = zk.Widget.$('$bookingVMContainer');
		if (vmContainer) {
			zAu.send(new zk.Event(vmContainer, 'onDragUpdate', json));
		}
		callback(item);
	},

	onMoving: function (item, callback) {
		callback(item); // send back the (possibly) changed item

	},

	onUpdate: function (item, callback) {
		openEditDialog(item, callback);
	},

	onRemove: function (item, callback) {
        if (confirm('Are you sure you want to delete this booking?')) {
            var bookingId = item.id || item.s_booking_id || 0;
            sendZkBookingEvent('onBookingDelete', String(bookingId));
            callback(item);
        } else {
            callback(null);
        }
    }
};
console.log('Options defined');

function initChart() {
	if (typeof vis === 'undefined' || typeof $ === 'undefined') {
		setTimeout(initChart, 100);
		return;
	}

	container = document.getElementById("booking-chart");
	if (!container) {
		// DOM update not yet applied — retry until the container appears
		setTimeout(initChart, 50);
		return;
	}

	// Destroy any existing timeline before creating a new one
	if (timeline) {
		try { timeline.destroy(); } catch(ex) { /* ignore */ }
		timeline = null;
	}

	timeline = new vis.Timeline(container, items, groups, options);
	$("#loading").hide();

	var today = new Date();
	var fiveDaysLater = new Date(today);
	fiveDaysLater.setDate(today.getDate() + 10);
	timeline.setWindow(today, fiveDaysLater);

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
    var startMs = item.start instanceof Date ? item.start.getTime() : Number(item.start);
    var endMs   = item.end   instanceof Date ? item.end.getTime()   : Number(item.end);
    var json = JSON.stringify({
        's_booking_id':              String(item.id || item.s_booking_id || 0),
        's_resource_id':             String(item.group || 0),
        'booking-name':              item.name || item.content || '',
        'description':               item.description || '',
        'startTimestamp':            String(startMs),
        'endTimestamp':              String(endMs),
        'assign-date-from-timestamp': String(startMs),
        'assign-date-to-timestamp':   String(endMs)
    });
    sendZkBookingEvent('onBookingEdit', json);
    if (callback) callback(item); // keep timeline item in place
}

function clickNew(item, callback) {
    var startMs = item.start instanceof Date ? item.start.getTime() : Number(item.start);
    var endMs   = item.end   instanceof Date ? item.end.getTime()   : Number(item.end);
    var json = JSON.stringify({
        's_booking_id':              '0',
        's_resource_id':             String(item.group || 0),
        'booking-name':              '',
        'description':               '',
        'startTimestamp':            String(startMs),
        'endTimestamp':              String(endMs),
        'assign-date-from-timestamp': String(startMs),
        'assign-date-to-timestamp':   String(endMs)
    });
    sendZkBookingEvent('onBookingAdd', json);
    if (callback) callback(null); // don't add ghost item to timeline — server will refresh
}

console.log('DEBUG: End of booking.js - Loaded Successfully');

    return {
        initChart:      initChart,
        drawChart:      drawChart,
        setGroups:      function(g) { groups = g; },
        setItems:       function(data) { items = new vis.DataSet(data); },
        getGroups:      function() { return groups; },
        clickNew:       clickNew,
        openEditDialog: openEditDialog
    };

})();
