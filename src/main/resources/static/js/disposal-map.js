(function () {
    var container = document.getElementById('disposalMap');
    if (!container || typeof L === 'undefined') {
        return;
    }

    var origin = window.originPoint || null;
    var facilities = window.facilityPoints || [];

    var fallback = [15.3173, 74.0]; // fallback center before geoPoints resolve
    var map = L.map(container);

    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; OpenStreetMap contributors'
    }).addTo(map);

    var bounds = [];

    if (origin && origin.latitude != null && origin.longitude != null) {
        var originLatLng = [origin.latitude, origin.longitude];
        bounds.push(originLatLng);
        L.circleMarker(originLatLng, {
            radius: 9,
            color: '#166534',
            weight: 3,
            fillColor: '#16a34a',
            fillOpacity: 1
        }).addTo(map).bindPopup('<strong>' + (origin.label || 'Collected load') + '</strong>');

        facilities.forEach(function (point) {
            if (point.latitude == null || point.longitude == null) {
                return;
            }
            var latLng = [point.latitude, point.longitude];
            bounds.push(latLng);
            L.circleMarker(latLng, {
                radius: 8,
                color: point.operational ? '#1f5bbf' : '#6b7680',
                weight: 3,
                fillColor: point.operational ? '#e7f0ff' : '#eef1f4',
                fillOpacity: 1
            }).addTo(map).bindPopup(
                '<strong>' + point.name + '</strong><br>' +
                (point.facilityType || '') + '<br>' +
                (point.distanceLabel || '—') + ' · ' + (point.durationLabel || '—') +
                (point.address ? '<br>' + point.address : '') +
                '<br><a href="' + point.navigateUrl + '" target="_blank" rel="noopener">Navigate</a>'
            );
        });

        if (bounds.length > 1) {
            map.fitBounds(bounds, { padding: [30, 30], maxZoom: 15 });
        } else {
            map.setView(originLatLng, 14);
        }
    } else if (facilities.length > 0) {
        facilities.forEach(function (point) {
            if (point.latitude == null || point.longitude == null) {
                return;
            }
            var latLng = [point.latitude, point.longitude];
            bounds.push(latLng);
            L.circleMarker(latLng, {
                radius: 8,
                color: '#1f5bbf',
                weight: 3,
                fillColor: '#e7f0ff',
                fillOpacity: 1
            }).addTo(map).bindPopup('<strong>' + point.name + '</strong>');
        });
        map.fitBounds(bounds, { padding: [30, 30], maxZoom: 15 });
    } else {
        map.setView(fallback, 11);
    }
})();
