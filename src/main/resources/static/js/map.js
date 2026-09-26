(function () {
    'use strict';

    var mapElement = document.getElementById('map');
    if (!mapElement) return;

    if (typeof L === 'undefined') {
        mapElement.innerHTML = '<div class="empty-state d-flex align-items-center justify-content-center h-100">' +
            'Map library failed to load. Check your connection and refresh.</div>';
        return;
    }

    var points = (typeof geoPoints !== 'undefined' && Array.isArray(geoPoints)) ? geoPoints : [];
    var colors = { HIGH: '#e0453a', MEDIUM: '#f0a92c', LOW: '#2fa86b' };

    var map = L.map('map').setView([15.2750, 73.9570], 14);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; OpenStreetMap contributors'
    }).addTo(map);

    var bounds = [];
    points.forEach(function (point) {
        if (point.latitude === null || point.longitude === null) return;
        var marker = L.circleMarker([point.latitude, point.longitude], {
            radius: 9,
            color: '#ffffff',
            weight: 2,
            fillColor: colors[point.severity] || '#19c37d',
            fillOpacity: 0.92
        }).addTo(map);

        marker.bindPopup(
            '<strong>' + point.publicId + '</strong><br>' +
            point.wasteType + '<br>' +
            point.severityLabel + ' · ' + point.status +
            (point.ward ? '<br>' + point.ward : '')
        );
        bounds.push([point.latitude, point.longitude]);
    });

    if (bounds.length > 1) {
        map.fitBounds(bounds, { padding: [30, 30] });
    } else if (bounds.length === 1) {
        map.setView(bounds[0], 15);
    }
})();
