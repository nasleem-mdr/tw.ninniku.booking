// booking_weekview.js
// BookingApp.WeekView — owns all week/day-view interaction symbols.
// Authoritative owner of openCustomAddDialog, openEditDialog, onWeekEventClick, etc.
// ZUL inline script block functions absorbed into this module in Task 10.

var BookingApp = BookingApp || {};
BookingApp.WeekView = (function () {

    // ── ZK event bridge ──────────────────────────────────────────────────────
    function sendZkBookingEvent(eventName, data) {
        var vmContainer = zk.Widget.$('$bookingVMContainer');
        if (vmContainer) {
            zAu.send(new zk.Event(vmContainer, eventName, data));
        } else {
            console.warn('bookingVMContainer not found for event:', eventName);
        }
    }

    // ---------------------------------------------------------------------------
    // Private helpers (absorbed from meetingroom.zul inline script)
    // ---------------------------------------------------------------------------

    function getGroups() {
        if (window.BookingApp && window.BookingApp.Timeline && typeof window.BookingApp.Timeline.getGroups === 'function') {
            return window.BookingApp.Timeline.getGroups();
        }
        return window.groups || [];
    }

    function toTimestamp(strDate) {
        if (!strDate) return "";
        var datum = Date.parse(strDate);
        return datum;
    }

    function convertFormToJSON(form) {
        var array = $(form).serializeArray();
        var json = {};
        $.each(array, function () {
            json[this.name] = this.value || "";
        });
        return json;
    }

    function updateMeetingRoomSelector() {
        var select = document.getElementById("s_resource_id");
        if (select) {
            select.options.length = 0;
            var groups = getGroups();
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

    function showBeforeDate() {
        if ($("#is-weekly").is(':checked')) {
            $(".before-date").show();
        } else {
            $(".before-date").hide();
        }
    }

    function validateBookingForm() {
        $(".input-error").removeClass("input-error");
        $(".error-text").remove();

        var bName = $("#booking-name");
        var isValid = true;

        if (!bName.val() || bName.val().trim() === "") {
            bName.addClass("input-error");
            bName.after("<span class='error-text'>Subject is required</span>");
            isValid = false;
        }
        return isValid;
    }

    // ---------------------------------------------------------------------------
    // Drag and Drop private state and helpers
    // ---------------------------------------------------------------------------

    var $ = window.$ || window.jQuery || window.jq;

    function getTimeFromY(y, dayKey) {
        var mins = (y / 40) * 60;
        var date = new Date(dayKey + 'T00:00:00');

        var startHour = 0;
        var root = document.querySelector('.week-view-root');
        if (root && root.dataset && root.dataset.startHour) {
            startHour = parseInt(root.dataset.startHour, 10);
        }
        date.setHours(startHour);
        date.setMinutes(date.getMinutes() + mins);
        return date;
    }

    var dragMode = null;
    var dragData = null;
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

    function getResourceColor(id) {
        var colors = ['#3F51B5', '#009688', '#FF9800', '#E91E63', '#673AB7', '#2196F3', '#4CAF50', '#FFC107', '#9C27B0', '#795548'];
        return colors[id % colors.length];
    }

    function updateTooltip(start, end, e) {
        if (!dragTooltip) return;
        var timeStr = formatTime(start) + ' - ' + formatTime(end);
        var durStr = '(' + formatDuration(end - start) + ')';
        dragTooltip.text(timeStr + ' ' + durStr);
        dragTooltip.css({ top: (e.clientY - 40) + 'px', left: (e.clientX + 15) + 'px' });
    }

    function triggerUpdate(s_booking_id, group, start, end) {
        console.log("Updating Booking: " + s_booking_id + " to " + start.toLocaleString() + " - " + end.toLocaleString());
        var json = JSON.stringify({
            id: String(s_booking_id),
            group: String(group),
            startTimestamp: String(start.getTime()),
            endTimestamp: String(end.getTime())
        });
        sendZkBookingEvent('onDragUpdate', json);
    }

    function initDragEvents() {
        $ = window.$ || window.jQuery || window.jq;
        if (typeof $ === 'undefined') {
            setTimeout(initDragEvents, 100);
            return;
        }

        $(document).off('mousedown', '.day-col');
        $(document).off('mousedown', '.resize-handle');
        $(document).off('mousedown', '.event-card');
        $(document).off('dblclick', '.day-col');
        $(document).off('mousemove.weekview');
        $(document).off('mouseup.weekview');

        // 1. Resize Start
        $(document).on('mousedown', '.resize-handle', function (e) {
            e.preventDefault();
            e.stopPropagation();
            var card = $(this).closest('.event-card');
            var dayCol = card.closest('.day-col');

            dragMode = 'resize';
            dragData = {
                card: card,
                dayCol: dayCol,
                startY: e.clientY,
                originalHeight: card.height(),
                id: card.data('id'),
                resId: card.data('resource-id'),
                startMs: card.data('start-ms'),
                endMs: card.data('end-ms'),
                dayKey: dayCol.data('date')
            };
            isDragging = true;
            _wasDragging = true;
        });

        // 2. Move Start (Clicking body of card)
        $(document).on('mousedown', '.event-card', function (e) {
            if ($(e.target).hasClass('delete-icon') || $(e.target).hasClass('resize-handle')) return;
            if (!$(this).hasClass('editable')) return;

            e.preventDefault();
            e.stopPropagation();

            var card = $(this);
            var dayCol = card.closest('.day-col');
            var rect = card[0].getBoundingClientRect();

            dragMode = 'move';
            dragData = {
                card: card,
                originalCol: dayCol,
                offsetY: e.clientY - rect.top,
                originalTop: parseFloat(card.css('top')),
                id: card.data('id'),
                resId: card.data('resource-id'),
                duration: card.data('end-ms') - card.data('start-ms'),
                startMs: card.data('start-ms'),
                startClientX: e.clientX,
                startClientY: e.clientY
            };

            // Don't set isDragging or create ghost here — wait for movement threshold
            isDragging = false;
            _wasDragging = false;
        });

        // 3a. Double-click on empty space → open Add Booking dialog at that time
        $(document).on('dblclick', '.day-col', function (e) {
            if ($(e.target).closest('.event-card').length > 0) return;
            e.preventDefault();
            e.stopPropagation();

            var col = $(this);
            var rect = this.getBoundingClientRect();
            var offsetY = e.clientY - rect.top;
            var dayKey = col.data('date');
            var resId = col.data('resource-id');
            if (!dayKey) return;

            var start = getTimeFromY(offsetY, dayKey);
            snapTo30(start);
            var end = new Date(start.getTime() + 60 * 60000);
            openCustomAddDialogRange(start.getTime(), end.getTime(), resId);
        });

        // 3b. Create Start (drag on empty space)
        $(document).on('mousedown', '.day-col', function (e) {
            if ($(e.target).closest('.event-card').length > 0) return;
            e.preventDefault();

            var col = $(this);
            var rect = this.getBoundingClientRect();
            var offsetY = e.clientY - rect.top;
            var dayKey = col.data('date');
            var resId = col.data('resource-id');

            if (!dayKey) return;

            dragMode = 'create';
            dragData = { col: col, startY: offsetY, dayKey: dayKey, resId: resId };
            isDragging = false;
            _wasDragging = false;

            $('.drag-tooltip').remove();
            dragTooltip = null;
        });

        // --- Global Mouse Move ---
        $(document).on('mousemove.weekview', function (e) {
            if (!dragData) return;

            if (dragMode === 'create') {
                var col = dragData.col;
                var rect = col[0].getBoundingClientRect();
                var currentY = e.clientY - rect.top;

                if (!isDragging && Math.abs(currentY - dragData.startY) > 5) {
                    isDragging = true;
                    if ($('.drag-ghost').length === 0) {
                        dragGhost = $('<div class="drag-ghost"></div>');
                        col.append(dragGhost);
                    } else { dragGhost = $('.drag-ghost'); }

                    if ($('.drag-tooltip').length === 0) {
                        dragTooltip = $('<div class="drag-tooltip"></div>');
                        $('body').append(dragTooltip);
                    } else { dragTooltip = $('.drag-tooltip'); }
                }

                if (isDragging) {
                    var top = Math.min(dragData.startY, currentY);
                    var height = Math.abs(currentY - dragData.startY);
                    dragGhost.css({ top: top + 'px', height: height + 'px' });

                    var startY = Math.min(dragData.startY, currentY);
                    var endY = Math.max(dragData.startY, currentY);
                    if (endY - startY < 10) endY = startY + 10;

                    var start = getTimeFromY(startY, dragData.dayKey);
                    var end = getTimeFromY(endY, dragData.dayKey);
                    start = snapTo30(start);
                    end = snapTo30(end);
                    if (end.getTime() <= start.getTime()) { end = new Date(start.getTime() + 30 * 60000); }

                    updateTooltip(start, end, e);
                }
            } else if (dragMode === 'resize') {
                var currentHeight = Math.max(15, dragData.originalHeight + (e.clientY - dragData.startY));
                dragData.card.css('height', currentHeight + 'px');

                var start = new Date(dragData.startMs);
                var newDurationMs = (currentHeight / 40.0) * 60 * 60000;
                var end = new Date(start.getTime() + newDurationMs);

                if (!dragTooltip) {
                    dragTooltip = $('<div class="drag-tooltip"></div>');
                    $('body').append(dragTooltip);
                }
                var endSnap = new Date(end.getTime());
                snapTo30(endSnap);

                updateTooltip(start, endSnap, e);
            } else if (dragMode === 'move') {
                var dx = e.clientX - dragData.startClientX;
                var dy = e.clientY - dragData.startClientY;

                if (!isDragging && Math.sqrt(dx * dx + dy * dy) > 5) {
                    isDragging = true;
                    _wasDragging = true;
                    dragData.card.addClass('dragging');
                    dragGhost = dragData.card.clone().removeClass('dragging').addClass('drag-ghost').css({
                        width: dragData.originalCol.width() + 'px',
                        zIndex: 1000
                    });
                    dragData.originalCol.append(dragGhost);
                    if ($('.drag-tooltip').length === 0) {
                        dragTooltip = $('<div class="drag-tooltip"></div>');
                        $('body').append(dragTooltip);
                    } else { dragTooltip = $('.drag-tooltip'); }
                }

                if (isDragging) {
                    var elemBelow = document.elementFromPoint(e.clientX, e.clientY);
                    var targetCol = $(elemBelow).closest('.day-col');

                    if (targetCol.length > 0) {
                        if (!dragGhost.parent().is(targetCol)) {
                            dragGhost.appendTo(targetCol);
                            dragGhost.css('width', targetCol.width() + 'px');
                        }
                        var colRect = targetCol[0].getBoundingClientRect();
                        var newY = e.clientY - colRect.top - dragData.offsetY;
                        newY = Math.max(0, newY);

                        dragGhost.css('top', newY + 'px');

                        var dayKey = targetCol.data('date');
                        var start = getTimeFromY(newY, dayKey);
                        snapTo30(start);

                        var end = new Date(start.getTime() + dragData.duration);

                        updateTooltip(start, end, e);
                    }
                }
            }
        });

        // --- Global Mouse Up ---
        $(document).on('mouseup.weekview', function (e) {
            if (!dragMode) return;

            $('.drag-tooltip').remove();
            dragTooltip = null;

            if (dragMode === 'create' && isDragging) {
                var col = dragData.col;
                var rect = col[0].getBoundingClientRect();
                var currentY = e.clientY - rect.top;

                var startY = Math.min(dragData.startY, currentY);
                var endY = Math.max(dragData.startY, currentY);
                if (endY - startY < 10) endY = startY + 10;

                var start = getTimeFromY(startY, dragData.dayKey);
                var end = getTimeFromY(endY, dragData.dayKey);
                start = snapTo30(start);
                end = snapTo30(end);
                if (end.getTime() <= start.getTime()) { end = new Date(start.getTime() + 30 * 60000); }

                openCustomAddDialogRange(start.getTime(), end.getTime(), dragData.resId);
                $('.drag-ghost').remove();
                dragGhost = null;

            } else if (dragMode === 'resize') {
                var card = dragData.card;
                var currentHeight = card.height();

                var start = new Date(dragData.startMs);
                var newDurationMs = (currentHeight / 40.0) * 60 * 60000;
                var end = new Date(start.getTime() + newDurationMs);
                snapTo30(end);

                if (end.getTime() <= start.getTime()) {
                    end = new Date(start.getTime() + 30 * 60000);
                }

                triggerUpdate(dragData.id, dragData.resId, start, end);

            } else if (dragMode === 'move') {
                var dx = e.clientX - dragData.startClientX;
                var dy = e.clientY - dragData.startClientY;
                var dist = Math.sqrt(dx * dx + dy * dy);

                dragData.card.removeClass('dragging');
                $('.drag-ghost').remove();
                dragGhost = null;

                if (!isDragging || dist < 5) {
                    // Click — open edit dialog using data attributes
                    var card = dragData.card;
                    openCustomEditDialog(
                        card.data('id'),
                        card.attr('data-booking-name') || '',
                        card.attr('data-booking-desc') || '',
                        card.data('resource-id'),
                        card.data('start-ms'),
                        card.data('end-ms')
                    );
                } else {
                    // Drag — update booking time
                    var elemBelow = document.elementFromPoint(e.clientX, e.clientY);
                    var targetCol = $(elemBelow).closest('.day-col');

                    if (targetCol.length > 0) {
                        var colRect = targetCol[0].getBoundingClientRect();
                        var newY = e.clientY - colRect.top - dragData.offsetY;
                        newY = Math.max(0, newY);

                        var dayKey = targetCol.data('date');
                        var newResId = targetCol.data('resource-id');

                        var start = getTimeFromY(newY, dayKey);
                        snapTo30(start);

                        var end = new Date(start.getTime() + dragData.duration);

                        triggerUpdate(dragData.id, newResId, start, end);
                    }
                }
            }

            dragMode = null;
            dragData = null;
            isDragging = false;
            setTimeout(function () { _wasDragging = false; }, 100);
        });
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
            clickNew(item, function () { });
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
        openEditDialog(item, function () { });
    }

    function openCustomAddDialogRange(startMs, endMs, resourceId) {
        var groups = getGroups();
        if (!resourceId) {
            resourceId = groups.length > 0 ? groups[0].id : 0;
        }
        var item = { start: new Date(startMs), end: new Date(endMs), group: resourceId, content: '' };
        clickNew(item, function () { });
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
        if (confirm('Are you sure you want to delete this booking?')) {
            sendZkBookingEvent('onBookingDelete', String(id));
        }
    }

    function weekViewScrollTo8Am() {
        var attempt = 0;
        function tryScroll() {
            var el = document.querySelector('.scroll-body');
            if (el && el.scrollHeight > 500) {
                el.scrollTop = 320;
                console.log('Scrolled to 320px (8:00 AM), current:', el.scrollTop);
            }
            if ((!el || el.scrollTop < 300) && attempt < 10) {
                attempt++;
                setTimeout(tryScroll, 100);
            }
        }
        setTimeout(tryScroll, 100);
    }

    function updateTimeIndicator() {
        var $line = $('.current-time-line');
        if ($line.length === 0) return;

        var now = new Date();
        var currentHour = now.getHours();
        var currentMin = now.getMinutes();

        var root = $('.week-view-root');
        if (root.length === 0) return;

        var startHour = parseInt(root.attr('data-start-hour') || 0);
        var minutesFromStart = (currentHour - startHour) * 60 + currentMin;
        var topPx = minutesFromStart * (40.0 / 60.0);
        var totalHeight = root.find('.days-grid').height();

        if (minutesFromStart < 0 || topPx > totalHeight) {
            $line.hide();
        } else {
            $line.css('top', topPx + 'px').show();
        }
    }

    function clickNew(item, callback) {
        var startMs = item.start instanceof Date ? item.start.getTime() : Number(item.start);
        var endMs   = item.end   instanceof Date ? item.end.getTime()   : Number(item.end);
        var resId   = item.group || 0;
        var json = JSON.stringify({
            's_booking_id':              '0',
            's_resource_id':             String(resId),
            'booking-name':              '',
            'description':               '',
            'startTimestamp':            String(startMs),
            'endTimestamp':              String(endMs),
            'assign-date-from-timestamp': String(startMs),
            'assign-date-to-timestamp':   String(endMs)
        });
        sendZkBookingEvent('onBookingAdd', json);
        if (callback) callback(null);
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
        if (callback) callback(item);
    }

    // ---------------------------------------------------------------------------
    // Initialization — run drag events and time indicator on load
    // ---------------------------------------------------------------------------

    initDragEvents();

    $(document).ready(function () {
        updateTimeIndicator();
        if (window._timeIndicatorInterval) clearInterval(window._timeIndicatorInterval);
        window._timeIndicatorInterval = setInterval(updateTimeIndicator, 60000);
    });

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
