(function () {
    'use strict';

    if (typeof Chart === 'undefined') return;

    var palette = ['#0f6b4f', '#19c37d', '#f0a92c', '#e0453a', '#1f5bbf', '#8a6d00', '#7b4bbf', '#0aa2a2', '#6b7c76'];

    function hideFallback(id) {
        var el = document.getElementById(id);
        if (el) el.style.display = 'none';
    }

    var categoryChart = document.getElementById('categoryChart');
    if (categoryChart && categoryLabels && categoryLabels.length) {
        hideFallback('categoryFallback');
        new Chart(categoryChart, {
            type: 'doughnut',
            data: {
                labels: Array.from(categoryLabels),
                datasets: [{
                    data: Array.from(categoryValues),
                    backgroundColor: palette,
                    borderWidth: 2,
                    borderColor: '#ffffff'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { position: 'right' } }
            }
        });
    }

    var timeChart = document.getElementById('timeChart');
    var points = Array.isArray(timeSeries) ? timeSeries : [];
    if (timeChart && points.length) {
        hideFallback('timeFallback');
        new Chart(timeChart, {
            type: 'line',
            data: {
                labels: points.map(function (p) { return p.day; }),
                datasets: [{
                    label: 'Reports',
                    data: points.map(function (p) { return p.count; }),
                    borderColor: '#0f6b4f',
                    backgroundColor: 'rgba(25, 195, 125, .25)',
                    fill: true,
                    tension: 0.35,
                    pointRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
            }
        });
    }

    var wardChart = document.getElementById('wardChart');
    if (wardChart && wardLabels && wardLabels.length) {
        new Chart(wardChart, {
            type: 'bar',
            data: {
                labels: Array.from(wardLabels),
                datasets: [{
                    label: 'Reports',
                    data: Array.from(wardValues),
                    backgroundColor: '#19c37d',
                    borderRadius: 6
                }]
            },
            options: {
                indexAxis: 'y',
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { x: { beginAtZero: true, ticks: { precision: 0 } } }
            }
        });
    }
})();
