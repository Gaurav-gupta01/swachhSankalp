/**
 * Live camera capture for every photo input on the site.
 *
 * Any element with `data-camera-input="<file input id>"` opens a camera preview;
 * the snapshot is written back into that file input as a real image file and a
 * `change` event is fired so the page's own upload/preview logic runs unchanged.
 * When the camera is unavailable the file picker is offered instead.
 */
(function () {
    'use strict';

    var MODAL_ID = 'cvCameraModal';
    var stream = null;
    var targetInput = null;
    var capturedBlob = null;

    document.addEventListener('click', function (event) {
        var trigger = event.target.closest('[data-camera-input]');
        if (!trigger) return;
        var input = document.getElementById(trigger.getAttribute('data-camera-input'));
        if (!input) return;
        event.preventDefault();
        open(input);
    });

    function open(input) {
        targetInput = input;
        capturedBlob = null;

        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            showFallback('This browser cannot open the camera. Choose a photo from your device instead.');
            return;
        }

        showModal();
        setStep('starting');

        navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false })
            .then(function (mediaStream) {
                stream = mediaStream;
                var video = el('cvCamVideo');
                video.srcObject = mediaStream;
                capturedBlob = null;
                setStep('live');
            })
            .catch(function (err) {
                showFallback('Camera unavailable (' + (err && err.message ? err.message : 'permission denied')
                    + '). Choose a photo from your device instead.');
            });
    }

    function showFallback(message) {
        showModal();
        setStep('error');
        var msg = el('cvCamMsg');
        msg.textContent = message;
        msg.classList.remove('d-none');
    }

    function setStep(step) {
        var video = el('cvCamVideo');
        var shot = el('cvCamShot');
        var msg = el('cvCamMsg');
        var shoot = el('cvCamShoot');
        var retake = el('cvCamRetake');
        var use = el('cvCamUse');
        var browse = el('cvCamBrowse');
        var starting = el('cvCamStarting');

        msg.classList.add('d-none');
        shoot.classList.add('d-none');
        retake.classList.add('d-none');
        use.classList.add('d-none');
        browse.classList.add('d-none');
        starting.classList.add('d-none');
        video.classList.add('d-none');
        shot.classList.add('d-none');

        if (step === 'live') {
            video.classList.remove('d-none');
            shoot.classList.remove('d-none');
        } else if (step === 'shot') {
            shot.classList.remove('d-none');
            retake.classList.remove('d-none');
            use.classList.remove('d-none');
        } else if (step === 'starting') {
            starting.classList.remove('d-none');
        } else if (step === 'error') {
            browse.classList.remove('d-none');
        }
    }

    function shoot() {
        var video = el('cvCamVideo');
        var canvas = el('cvCamCanvas');
        var width = video.videoWidth || 1280;
        var height = video.videoHeight || 720;
        canvas.width = width;
        canvas.height = height;
        canvas.getContext('2d').drawImage(video, 0, 0, width, height);

        el('cvCamShot').src = canvas.toDataURL('image/jpeg', 0.9);
        canvas.toBlob(function (blob) {
            capturedBlob = blob;
            setStep('shot');
        }, 'image/jpeg', 0.85);
    }

    function usePhoto() {
        if (!capturedBlob || !targetInput) {
            setStep('live');
            return;
        }
        var file = new File([capturedBlob], 'capture-' + Date.now() + '.jpg', { type: 'image/jpeg' });
        try {
            var transfer = new DataTransfer();
            transfer.items.add(file);
            targetInput.files = transfer.files;
        } catch (err) {
            showFallback('This browser cannot attach the captured photo. Choose a file instead.');
            return;
        }
        hide();
        targetInput.dispatchEvent(new Event('change', { bubbles: true }));
    }

    function showModal() {
        var modal = ensureModal();
        if (window.bootstrap && window.bootstrap.Modal) {
            window.bootstrap.Modal.getOrCreateInstance(modal).show();
        } else {
            modal.classList.add('show');
            modal.style.display = 'block';
            modal.setAttribute('aria-hidden', 'false');
        }
    }

    function hide() {
        var modal = document.getElementById(MODAL_ID);
        stopStream();
        if (window.bootstrap && window.bootstrap.Modal) {
            window.bootstrap.Modal.getOrCreateInstance(modal).hide();
        } else {
            modal.classList.remove('show');
            modal.style.display = 'none';
            modal.setAttribute('aria-hidden', 'true');
        }
    }

    function stopStream() {
        if (stream) {
            stream.getTracks().forEach(function (track) { track.stop(); });
            stream = null;
        }
        var video = el('cvCamVideo');
        if (video) video.srcObject = null;
    }

    function el(id) {
        return document.getElementById(id);
    }

    function ensureModal() {
        var existing = document.getElementById(MODAL_ID);
        if (existing) return existing;

        var wrap = document.createElement('div');
        wrap.innerHTML =
            '<div class="modal fade" id="' + MODAL_ID + '" tabindex="-1" aria-hidden="true">' +
            '  <div class="modal-dialog modal-dialog-centered modal-lg">' +
            '    <div class="modal-content">' +
            '      <div class="modal-header">' +
            '        <h5 class="modal-title"><i class="bi bi-camera-fill me-2"></i>Capture photo</h5>' +
            '        <button type="button" class="btn-close" data-cv-camera="close" aria-label="Close"></button>' +
            '      </div>' +
            '      <div class="modal-body text-center">' +
            '        <video id="cvCamVideo" class="cv-camera-video w-100" playsinline autoplay muted></video>' +
            '        <canvas id="cvCamCanvas" class="d-none"></canvas>' +
            '        <img id="cvCamShot" class="preview-img w-100 d-none" alt="Captured photo">' +
            '        <div id="cvCamStarting" class="text-muted py-5 d-none">' +
            '          <span class="spinner-border spinner-border-sm me-2"></span>Starting camera…</div>' +
            '        <div id="cvCamMsg" class="alert alert-danger mt-3 d-none"></div>' +
            '      </div>' +
            '      <div class="modal-footer justify-content-center flex-wrap gap-2">' +
            '        <button type="button" class="btn btn-outline-secondary" data-cv-camera="close">Cancel</button>' +
            '        <button type="button" class="btn btn-outline-primary d-none" id="cvCamRetake">' +
            '          <i class="bi bi-arrow-counterclockwise me-1"></i>Retake</button>' +
            '        <button type="button" class="btn btn-success" id="cvCamShoot">' +
            '          <i class="bi bi-camera-fill me-1"></i>Capture</button>' +
            '        <button type="button" class="btn btn-accent d-none" id="cvCamUse">' +
            '          <i class="bi bi-check-lg me-1"></i>Use this photo</button>' +
            '        <button type="button" class="btn btn-outline-secondary d-none" id="cvCamBrowse">' +
            '          <i class="bi bi-folder2-open me-1"></i>Choose a file instead</button>' +
            '      </div>' +
            '    </div>' +
            '  </div>' +
            '</div>';
        var modal = wrap.firstElementChild;
        document.body.appendChild(modal);

        modal.addEventListener('click', function (event) {
            var action = event.target.closest('[data-cv-camera]');
            if (action && action.getAttribute('data-cv-camera') === 'close') {
                hide();
                return;
            }
            if (event.target === modal) hide();
        });
        modal.addEventListener('hidden.bs.modal', stopStream);
        el('cvCamShoot').addEventListener('click', shoot);
        el('cvCamRetake').addEventListener('click', function () { setStep('live'); });
        el('cvCamUse').addEventListener('click', usePhoto);
        el('cvCamBrowse').addEventListener('click', function () {
            hide();
            if (targetInput) targetInput.click();
        });
        return modal;
    }
})();
