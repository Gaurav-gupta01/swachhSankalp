(function () {
    'use strict';

    var form = document.getElementById('reportForm');
    if (!form) return;

    var dropzone = document.getElementById('dropzone');
    var fileInput = document.getElementById('fileInput');
    var previewWrap = document.getElementById('previewWrap');
    var preview = document.getElementById('preview');
    var fileName = document.getElementById('fileName');
    var analyzeBtn = document.getElementById('analyzeBtn');
    var changePhotoBtn = document.getElementById('changePhotoBtn');
    var analyzingCard = document.getElementById('analyzingCard');
    var progressBar = document.getElementById('progressBar');
    var progressLabel = document.getElementById('progressLabel');
    var aiCard = document.getElementById('aiCard');
    var categoryIcon = document.getElementById('categoryIcon');
    var categoryLabel = document.getElementById('categoryLabel');
    var aiReason = document.getElementById('aiReason');
    var confidencePill = document.getElementById('confidencePill');
    var severityLabel = document.getElementById('severityLabel');
    var severityTrack = document.getElementById('severityTrack');
    var severityFill = document.getElementById('severityFill');
    var lowConfidenceBox = document.getElementById('lowConfidenceBox');
    var manualCategory = document.getElementById('manualCategory');
    var candidatesBox = document.getElementById('candidates');
    var submitBtn = document.getElementById('submitBtn');
    var stepDot2 = document.getElementById('stepDot2');
    var stepDot3 = document.getElementById('stepDot3');

    var fImageFile = document.getElementById('imageFile');
    var fWasteType = document.getElementById('wasteType');
    var fConfidence = document.getElementById('confidence');
    var fSeverity = document.getElementById('severity');
    var fLatitude = document.getElementById('latitude');
    var fLongitude = document.getElementById('longitude');
    var fAddress = document.getElementById('address');
    var fWard = document.getElementById('ward');

    var geoBtn = document.getElementById('geoBtn');
    var geoStatus = document.getElementById('geoStatus');

    var selectedFile = null;
    var progressTimer = null;

    dropzone.addEventListener('click', function () { fileInput.click(); });
    changePhotoBtn.addEventListener('click', function () { fileInput.click(); });

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
        if (fileInput.files.length) handleFile(fileInput.files[0]);
    });
    analyzeBtn.addEventListener('click', analyze);

    manualCategory.addEventListener('change', function () {
        fWasteType.value = manualCategory.value;
        var option = manualCategory.options[manualCategory.selectedIndex];
        categoryLabel.textContent = option.textContent.trim();
        categoryIcon.textContent = option.textContent.trim().split(' ')[0];
        confidencePill.textContent = 'Manual selection';
        confidencePill.classList.add('low');
    });

    form.addEventListener('submit', function (e) {
        if (!fImageFile.value) {
            e.preventDefault();
            alert('Upload a waste photo and run the AI analysis first.');
            return;
        }
        var hasCoords = fLatitude.value !== '' && fLongitude.value !== '';
        if (!fAddress.value.trim() && !hasCoords) {
            e.preventDefault();
            alert('Add an address or capture your GPS location so the team can find the waste.');
            fAddress.focus();
            return;
        }
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Submitting…';
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
            stepDot3.classList.add('active');
            reverseGeocode(lat, lng);
        }, function (err) {
            geoBtn.disabled = false;
            geoStatus.textContent = 'Could not capture GPS (' + err.message + '). Please type the address.';
        }, { enableHighAccuracy: true, timeout: 9000, maximumAge: 60000 });
    });

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

    function handleFile(file) {
        if (!file.type || file.type.indexOf('image/') !== 0) {
            alert('Please choose an image file (JPG, PNG or WEBP).');
            return;
        }
        if (file.size > 8 * 1024 * 1024) {
            alert('Image is larger than 8 MB. Please choose a smaller photo.');
            return;
        }
        selectedFile = file;
        preview.src = URL.createObjectURL(file);
        previewWrap.style.display = 'block';
        fileName.textContent = file.name + ' · ' + Math.round(file.size / 1024) + ' KB';
        changePhotoBtn.style.display = 'inline-block';
        analyzeBtn.disabled = false;
        aiCard.style.display = 'none';
        stepDot2.classList.remove('active');
        fImageFile.value = '';
        submitBtn.disabled = true;
        analyze();
    }

    function analyze() {
        if (!selectedFile) return;

        var data = new FormData();
        data.append('image', selectedFile, selectedFile.name);

        analyzingCard.style.display = 'block';
        aiCard.style.display = 'none';
        startProgress();

        fetch('/api/ai/classify', { method: 'POST', body: data, credentials: 'same-origin' })
            .then(function (res) {
                if (res.status === 401) {
                    window.location.href = '/login?next=/report/new';
                    throw new Error('Login required');
                }
                return res.json().then(function (body) {
                    if (!res.ok) throw new Error(body.message || 'AI analysis failed');
                    return body;
                });
            })
            .then(applyResult)
            .catch(function (err) {
                stopProgress(true);
                aiCard.style.display = 'block';
                categoryIcon.textContent = '⚠️';
                categoryLabel.textContent = 'AI analysis unavailable';
                aiReason.textContent = err.message;
                confidencePill.textContent = 'No result';
                confidencePill.classList.add('low');
                severityLabel.textContent = 'Medium';
                lowConfidenceBox.style.display = 'block';
                candidatesBox.textContent = 'You can still submit by selecting the category manually.';
                submitBtn.disabled = true;
            });
    }

    function applyResult(result) {
        stopProgress(false);

        fImageFile.value = result.imageFile;
        fWasteType.value = result.category;
        fConfidence.value = result.confidence;
        fSeverity.value = result.severity;

        var pct = Math.round(result.confidence * 100);
        categoryIcon.textContent = result.categoryIcon;
        categoryLabel.textContent = result.wasteType;
        aiReason.textContent = result.reason;
        confidencePill.textContent = pct + '% confidence';
        confidencePill.classList.toggle('low', !!result.lowConfidence);

        var sev = (result.severity || 'Medium').toUpperCase();
        severityLabel.textContent = result.severity;
        severityFill.style.width = (sev === 'HIGH' ? 100 : sev === 'MEDIUM' ? 66 : 33) + '%';
        severityTrack.className = 'severity-track severity-' + sev;

        lowConfidenceBox.style.display = result.lowConfidence ? 'block' : 'none';
        if (result.lowConfidence) {
            manualCategory.value = result.category;
        }

        candidatesBox.textContent = (result.candidates || []).map(function (c) {
            return c.label + ' ' + Math.round(c.score * 100) + '%';
        }).join('  ·  ');

        aiCard.style.display = 'block';
        stepDot2.classList.add('active');
        submitBtn.disabled = false;
    }

    function startProgress() {
        var progress = 0;
        progressBar.style.width = '0%';
        progressLabel.textContent = '0%';
        if (progressTimer) clearInterval(progressTimer);
        progressTimer = setInterval(function () {
            progress = Math.min(92, progress + 6 + Math.random() * 16);
            progressBar.style.width = progress + '%';
            progressLabel.textContent = Math.round(progress) + '%';
        }, 170);
    }

    function stopProgress(failed) {
        if (progressTimer) {
            clearInterval(progressTimer);
            progressTimer = null;
        }
        if (failed) {
            analyzingCard.style.display = 'none';
            return;
        }
        progressBar.style.width = '100%';
        progressLabel.textContent = '100%';
        setTimeout(function () { analyzingCard.style.display = 'none'; }, 450);
    }
})();
