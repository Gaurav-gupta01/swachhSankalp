(function () {
    'use strict';

    var form = document.getElementById('animalForm');
    if (!form) return;

    var dropzone = document.getElementById('dropzone');
    var fileInput = document.getElementById('fileInput');
    var previewWrap = document.getElementById('previewWrap');
    var preview = document.getElementById('preview');
    var fileName = document.getElementById('fileName');
    var clearPhotoBtn = document.getElementById('clearPhotoBtn');
    var geoBtn = document.getElementById('geoBtn');
    var geoStatus = document.getElementById('geoStatus');
    var submitBtn = document.getElementById('submitBtn');
    var fLatitude = document.getElementById('latitude');
    var fLongitude = document.getElementById('longitude');
    var fAddress = document.getElementById('address');

    dropzone.addEventListener('click', function () { fileInput.click(); });

    ['dragenter', 'dragover'].forEach(function (evt) {
        dropzone.addEventListener(evt, function (e) {
            e.preventDefault();
            dropzone.classList.add('dragover');
        });
    });
    ['dragleave', 'drop'].forEach(function (evt) {
        dropzone.addEventListener(evt, function (e) {
            e.preventDefault();
            dropzone.classList.remove('dragover');
        });
    });
    dropzone.addEventListener('drop', function (e) {
        if (e.dataTransfer.files && e.dataTransfer.files.length) {
            handleFile(e.dataTransfer.files[0]);
        }
    });
    fileInput.addEventListener('change', function () {
        if (fileInput.files.length) {
            showPreview(fileInput.files[0]);
        }
    });
    clearPhotoBtn.addEventListener('click', function () {
        fileInput.value = '';
        previewWrap.style.display = 'none';
        clearPhotoBtn.style.display = 'none';
    });

    geoBtn.addEventListener('click', function () {
        if (!navigator.geolocation) {
            geoStatus.textContent = 'Geolocation is not supported by this browser. Please type the address.';
            return;
        }
        geoStatus.textContent = 'Locating…';
        geoBtn.disabled = true;
        navigator.geolocation.getCurrentPosition(function (pos) {
            var lat = pos.coords.latitude;
            var lng = pos.coords.longitude;
            fLatitude.value = lat.toFixed(6);
            fLongitude.value = lng.toFixed(6);
            geoStatus.textContent = 'Captured: ' + lat.toFixed(5) + ', ' + lng.toFixed(5);
            geoBtn.disabled = false;
            reverseGeocode(lat, lng);
        }, function (err) {
            geoBtn.disabled = false;
            geoStatus.textContent = 'Could not capture GPS (' + err.message + '). Please type the address.';
        }, { enableHighAccuracy: true, timeout: 9000, maximumAge: 60000 });
    });

    form.addEventListener('submit', function (e) {
        var type = form.querySelector('select[name="animalType"]');
        if (!type || !type.value) {
            e.preventDefault();
            alert('Choose which animal you are reporting.');
            if (type) type.focus();
            return;
        }
        var hasCoords = fLatitude.value !== '' && fLongitude.value !== '';
        if (!fAddress.value.trim() && !hasCoords) {
            e.preventDefault();
            alert('Add an address or capture your GPS location so the team can find the animal.');
            fAddress.focus();
            return;
        }
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Submitting…';
    });

    function handleFile(file) {
        if (!file.type || file.type.indexOf('image/') !== 0) {
            alert('Please choose an image file (JPG, PNG or WEBP).');
            return;
        }
        if (file.size > 8 * 1024 * 1024) {
            alert('Image is larger than 8 MB. Please choose a smaller photo.');
            return;
        }
        try {
            var transfer = new DataTransfer();
            transfer.items.add(file);
            fileInput.files = transfer.files;
        } catch (err) {
            /* older browsers: the file stays on the input when picked directly */
        }
        showPreview(file);
    }

    function showPreview(file) {
        preview.src = URL.createObjectURL(file);
        previewWrap.style.display = 'block';
        fileName.textContent = file.name + ' · ' + Math.round(file.size / 1024) + ' KB';
        clearPhotoBtn.style.display = 'inline-block';
    }

    function reverseGeocode(lat, lng) {
        var controller = new AbortController();
        var timer = setTimeout(function () { controller.abort(); }, 4000);
        fetch('https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=' + lat + '&lon=' + lng + '&zoom=18',
            { signal: controller.signal })
            .then(function (res) { return res.ok ? res.json() : null; })
            .then(function (data) {
                if (data && data.display_name && !fAddress.value.trim()) {
                    fAddress.value = data.display_name;
                }
            })
            .catch(function () { /* offline or blocked - coordinates are enough */ })
            .finally(function () { clearTimeout(timer); });
    }
})();
