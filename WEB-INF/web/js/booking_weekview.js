// booking_weekview.js
// BookingApp.WeekView — owns all week/day-view interaction symbols.
// Authoritative owner of openCustomAddDialog, openEditDialog, onWeekEventClick, etc.
// ZUL inline script block functions absorbed into this module in Task 10.

var BookingApp = BookingApp || {};
BookingApp.WeekView = (function () {

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

        var item = {
            s_booking_id: String(s_booking_id),
            group: String(group),
            startTimestamp: String(start.getTime()),
            endTimestamp: String(end.getTime())
        };

        var itemDataWidget = zk.Widget.$('$itemData');
        var dateLastWidget = zk.Widget.$('$dateLast');

        if (itemDataWidget && dateLastWidget) {
            itemDataWidget.setValue(JSON.stringify(item));
            itemDataWidget.fireOnChange();
            dateLastWidget.setValue(Date.now().toString());
            dateLastWidget.fireOnChange();
        } else {
            console.error("ZK Widgets not found!");
        }
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
                startMs: card.data('start-ms')
            };

            card.addClass('dragging');
            dragGhost = card.clone().removeClass('dragging').addClass('drag-ghost').css({
                width: dayCol.width() + 'px',
                zIndex: 1000
            });
            dayCol.append(dragGhost);

            isDragging = true;
            _wasDragging = true;

            if ($('.drag-tooltip').length === 0) {
                dragTooltip = $('<div class="drag-tooltip"></div>');
                $('body').append(dragTooltip);
            } else { dragTooltip = $('.drag-tooltip'); }
        });

        // 3. Create Start (Clicking empty space)
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
                dragData.card.removeClass('dragging');
                $('.drag-ghost').remove();
                dragGhost = null;

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

            dragMode = null;
            dragData = null;
            isDragging = false;
            setTimeout(function () { _wasDragging = false; }, 100);
        });
    }

    function doScroll() {
        var el = document.querySelector('.scroll-body');
        if (el && el.scrollHeight > 500) {
            el.scrollTop = 320;
            console.log('Scrolled to 320px (8:00 AM), current:', el.scrollTop);
        }
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
            var itemDataWidget = zk.Widget.$('$itemData');
            var bookingDeletedWidget = zk.Widget.$('$bookingDeleted');
            var json = JSON.stringify({ id: String(id) });

            if (itemDataWidget && bookingDeletedWidget) {
                itemDataWidget.setValue(json);
                itemDataWidget.fireOnChange();
                bookingDeletedWidget.setValue('DELETE-' + id);
                bookingDeletedWidget.fireOnChange();
            } else {
                var $jq = window.$ || window.jQuery || window.jq;
                $jq('#itemData').val(json).trigger('change');
                $jq('#bookingDeleted').val('DELETE-' + id).trigger('change');
            }
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
        updateMeetingRoomSelector();
        $(".weekly").show();
        $('#is-weekly').removeAttr('checked');
        showBeforeDate();

        var start = new Date(item.start);
        var end = new Date(item.end);

        $("#booking-name").val("");
        $("#description").val("");
        $("#assign-date-from").val(start.toISOString().slice(0, 16));
        $("#assign-date-to").val(end.toISOString().slice(0, 16));
        $("#s_resource_id").val(item.group);
        $("#s_booking_id").val(0);

        $(".input-error").removeClass("input-error");
        $(".error-text").remove();

        $("#update-form").dialog({
            title: "Added New Booking",
            modal: true,
            width: "500px",
            buttons: {
                Ok: function () {
                    if (!validateBookingForm()) return;

                    $(this).dialog("close");
                    $("#assign-date-from-timestamp").val(toTimestamp($("#assign-date-from").val()));
                    $("#assign-date-to-timestamp").val(toTimestamp($("#assign-date-to").val()));
                    $("#repeat-date-to-timestamp").val(toTimestamp($("#repeat-date-to").val()));

                    var json = convertFormToJSON($("#booking-form"));
                    if (!json.hasOwnProperty("s_booking_id") || json["s_booking_id"] === "") {
                        json["s_booking_id"] = "0";
                    }
                    zk.Widget.$("$itemData").setValue(JSON.stringify(json));
                    zk.Widget.$("$itemData").fireOnChange();
                    zk.Widget.$("$bookingUpdated").setValue(Date.now().toString());
                    zk.Widget.$("$bookingUpdated").fireOnChange();

                    if (callback) callback(item);
                },
                Cancel: function () {
                    $(this).dialog("close");
                    if (callback) callback(null);
                }
            }
        });
    }

    function openEditDialog(item, callback) {
        $(".weekly").hide();
        $("#s_resource_id").val(item.group);
        $("#s_booking_id").val(item.s_booking_id);
        $("#group").val(item.group);
        $("#booking-name").val(item.name ? item.name : item.content);
        $("#description").val(item.description);

        var start = new Date(item.start);
        var end = new Date(item.end);

        $("#assign-date-from").val(start.toISOString().slice(0, 16));
        $("#assign-date-to").val(end.toISOString().slice(0, 16));

        $(".input-error").removeClass("input-error");
        $(".error-text").remove();

        $("#update-form").dialog({
            title: "Edit Booking",
            modal: true,
            width: "500px",
            buttons: {
                "Delete": function () {
                    if (confirm("Are you sure you want to delete this booking?")) {
                        $(this).dialog("close");
                        if (!item.id && item.s_booking_id) item.id = item.s_booking_id;
                        zk.Widget.$("$itemData").setValue(JSON.stringify(item));
                        zk.Widget.$("$itemData").fireOnChange();
                        zk.Widget.$("$bookingDeleted").setValue(Date.now().toString());
                        zk.Widget.$("$bookingDeleted").fireOnChange();
                        if (callback) callback(null);
                    }
                },
                Ok: function () {
                    if (!validateBookingForm()) return;

                    $(this).dialog("close");
                    if (item) {
                        item.description = $("#description").val();
                        item.group = $("#group").val();
                    }
                    $("#assign-date-from-timestamp").val(toTimestamp($("#assign-date-from").val()));
                    $("#assign-date-to-timestamp").val(toTimestamp($("#assign-date-to").val()));

                    var json = convertFormToJSON($("#booking-form"));
                    if (!json.hasOwnProperty("s_booking_id") || json["s_booking_id"] === "") {
                        var val = $("#s_booking_id").val();
                        json["s_booking_id"] = (val === undefined || val === null) ? "0" : val;
                    }
                    zk.Widget.$("$itemData").setValue(JSON.stringify(json));
                    zk.Widget.$("$itemData").fireOnChange();
                    zk.Widget.$("$bookingUpdated").setValue(Date.now().toString());
                    zk.Widget.$("$bookingUpdated").fireOnChange();

                    if (callback) callback(item);
                },
                Cancel: function () {
                    $(this).dialog("close");
                    if (callback) callback(null);
                }
            }
        });
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

    // Also run immediately in case of ZUL update
    updateTimeIndicator();

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
