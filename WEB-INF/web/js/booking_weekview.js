// booking_weekview.js
// BookingApp.WeekView — owns all week/day-view interaction symbols.
// Authoritative owner of openCustomAddDialog, openEditDialog, onWeekEventClick, etc.
// The ZUL inline script block functions are absorbed into this module in Task 10.

var BookingApp = BookingApp || {};
BookingApp.WeekView = (function () {

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    function getGroups() {
        // After Task 9, BookingApp.Timeline exposes getGroups(). Until then, fall back to window.groups.
        if (window.BookingApp && window.BookingApp.Timeline && typeof window.BookingApp.Timeline.getGroups === 'function') {
            return window.BookingApp.Timeline.getGroups();
        }
        return window.groups || [];
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    function openCustomAddDialog(dateStr, minutesOffset, resourceId) {
        try {
            var groups = getGroups();
            if (!resourceId) {
                resourceId = groups.length > 0 ? groups[0].id : 0;
            }
            var date = new Date(dateStr);
            date.setMinutes(date.getMinutes() + minutesOffset);
            var endDate = new Date(date);
            endDate.setHours(endDate.getHours() + 1);
            var item = { start: date, end: endDate, group: resourceId, content: '' };
            if (typeof BookingApp.WeekView.clickNew === 'function') {
                BookingApp.WeekView.clickNew(item, function () { });
            } else {
                console.error("BookingApp.WeekView.clickNew not defined");
            }
        } catch (e) {
            console.error("Error in openCustomAddDialog:", e);
        }
    }

    function openCustomEditDialog(id, name, desc, resourceId, startMs, endMs) {
        var item = {
            id: id, s_booking_id: id, name: name, content: name,
            description: desc, group: resourceId,
            start: new Date(startMs), end: new Date(endMs)
        };
        if (typeof BookingApp.WeekView.openEditDialog === 'function') {
            BookingApp.WeekView.openEditDialog(item, function () { });
        }
    }

    function openCustomAddDialogRange(startMs, endMs, resourceId) {
        var groups = getGroups();
        if (!resourceId) {
            resourceId = groups.length > 0 ? groups[0].id : 0;
        }
        var item = { start: new Date(startMs), end: new Date(endMs), group: resourceId, content: '' };
        if (typeof BookingApp.WeekView.clickNew === 'function') {
            BookingApp.WeekView.clickNew(item, function () { });
        } else {
            console.error("BookingApp.WeekView.clickNew not defined");
        }
    }

    var _wasDragging = false;
    function onWeekDayClick(event, elem, dayKey, resourceId) {
        if (_wasDragging) { _wasDragging = false; return; }
        var rect = elem.getBoundingClientRect();
        var min = (event.clientY - rect.top) / 40 * 60;
        openCustomAddDialog(dayKey, Math.floor(min / 30) * 30, resourceId);
    }

    function onWeekEventClick(event, id, name, desc, resId, startMs, endMs) {
        event.stopPropagation();
        openCustomEditDialog(id, name, desc, resId, startMs, endMs);
    }

    function onWeekEventDelete(event, id) {
        event.stopPropagation();
        if (typeof zAu !== 'undefined') {
            var payload = JSON.stringify({ id: String(id) });
            var tb = document.querySelector('[id$="bookingDeleted"]');
            if (tb) {
                tb.value = payload;
                zAu.send(new zk.Event(zk.Widget.$(tb), 'onChange', { value: payload }, { toServer: true }));
            }
        }
    }

    function weekViewScrollTo8Am() {
        var scrollBody = document.querySelector('.scroll-body');
        if (scrollBody) scrollBody.scrollTop = 8 * 40;
    }

    function updateTimeIndicator() {
        var lines = document.querySelectorAll('.current-time-line');
        if (!lines.length) return;
        var now = new Date();
        var rootEl = document.querySelector('.week-view-root');
        var startHour = rootEl ? parseInt(rootEl.getAttribute('data-start-hour') || '0') : 0;
        var top = ((now.getHours() - startHour) * 60 + now.getMinutes()) * (40 / 60);
        lines.forEach(function (line) { line.style.top = top + 'px'; });
    }

    // clickNew and openEditDialog delegate to BookingApp.Timeline (defined in Task 9)
    function clickNew(item, callback) {
        if (window.BookingApp && window.BookingApp.Timeline) {
            BookingApp.Timeline.clickNew(item, callback);
        }
    }
    function openEditDialog(item, callback) {
        if (window.BookingApp && window.BookingApp.Timeline) {
            BookingApp.Timeline.openEditDialog(item, callback);
        }
    }

    return {
        openCustomAddDialog:      openCustomAddDialog,
        openCustomEditDialog:     openCustomEditDialog,
        openCustomAddDialogRange: openCustomAddDialogRange,
        onWeekDayClick:           onWeekDayClick,
        onWeekEventClick:         onWeekEventClick,
        onWeekEventDelete:        onWeekEventDelete,
        weekViewScrollTo8Am:      weekViewScrollTo8Am,
        updateTimeIndicator:      updateTimeIndicator,
        clickNew:                 clickNew,
        openEditDialog:           openEditDialog
    };

})();
