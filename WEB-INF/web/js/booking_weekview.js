// booking_weekview.js - extracted from meetingroom.zul

// Fallback: Define global functions here in case booking.js is cached/stale
window.openCustomAddDialog = function (dateStr, minutesOffset, resourceId) {
    try {
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
            alert("Error: window.clickNew is not a function. Check booking.js loading.");
            console.error("clickNew not defined");
        }
    } catch (e) {
        alert("Error in openCustomAddDialog: " + e.message);
        console.error(e);
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

// --- Drag and Drop Logic (Inline Fallback) ---
(function () {
    var $ = window.$ || window.jQuery || window.jq;

    // Helper to get time from Y coordinate relative to day-col
    function getTimeFromY(y, dayKey) {
        // 40px = 60 mins -> 1px = 1.5 mins
        var mins = (y / 40) * 60;
        // Force local time parsing
        var date = new Date(dayKey + 'T00:00:00');
        date.setMinutes(date.getMinutes() + mins);
        return date;
    }

    var dragStartData = null;
    var dragGhost = null;
    var dragTooltip = null;
    var isDragging = false;

    // Helper: Round date to nearest 30 minutes
    function snapTo30(d) {
        var m = d.getMinutes();
        var r = Math.round(m / 30) * 30;
        d.setMinutes(r, 0, 0);
        return d;
    }

    // Helper: Format time as HH:mm
    function formatTime(date) {
        var h = date.getHours();
        var m = date.getMinutes();
        return (h < 10 ? '0' + h : h) + ':' + (m < 10 ? '0' + m : m);
    }

    // Helper: Format duration
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

        // Unbind first to avoid duplicates
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

            // Remove existing tooltip if any
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
                // Create Ghost
                if ($('.drag-ghost').length === 0) {
                    dragGhost = $('<div class="drag-ghost"></div>');
                    col.append(dragGhost);
                } else {
                    dragGhost = $('.drag-ghost');
                }

                // Create Tooltip
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

                // Calculate Times for Tooltip
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

                // Position tooltip near cursor but not under it
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

                // Minimum height check (e.g. 15 mins = 10px)
                if (endY - startY < 10) endY = startY + 10;

                var start = getTimeFromY(startY, dragStartData.dayKey);
                var end = getTimeFromY(endY, dragStartData.dayKey);

                start = snapTo30(start);
                end = snapTo30(end);

                // Ensure valid duration (min 30 min)
                if (end.getTime() <= start.getTime()) {
                    end = new Date(start.getTime() + 30 * 60000);
                }

                if (window.openCustomAddDialogRange) {
                    window.openCustomAddDialogRange(start.getTime(), end.getTime(), dragStartData.resId);
                }

                $('.drag-ghost').remove();
                dragGhost = null;
                _weekViewWasDragging = true; // Prevent click event from firing
            }

            // Cleanup
            $('.drag-tooltip').remove();
            dragTooltip = null;

            dragStartData = null;
            isDragging = false;
            setTimeout(function () { _weekViewWasDragging = false; }, 100);
        });
    }

    // Start initialization
    initDragEvents();
})();
