(function () {
    'use strict';

    var widget = document.querySelector('[data-otp-widget]');
    if (!widget) return;

    var purpose = widget.getAttribute('data-purpose') || 'REGISTER';
    var phoneInput = document.getElementById(widget.getAttribute('data-phone-input'));
    var hiddenPhone = document.getElementById(widget.getAttribute('data-hidden-phone'));
    var sendBtn = document.getElementById('sendOtpBtn');
    var resendBtn = document.getElementById('resendOtpBtn');
    var verifyBtn = document.getElementById('verifyOtpBtn');
    var block = document.getElementById('otpBlock');
    var boxesWrap = document.getElementById('otpBoxes');
    var boxes = boxesWrap ? Array.prototype.slice.call(boxesWrap.querySelectorAll('input')) : [];
    var statusEl = document.getElementById('otpStatus');
    var verifiedEl = document.getElementById('otpVerified');
    var submitBtn = document.getElementById('submitBtn');
    var redirectTarget = widget.getAttribute('data-redirect') || null;

    var verifiedPhone = null;
    var cooldownTimer = null;

    function phoneDigits(value) {
        var digits = (value || '').replace(/[^0-9]/g, '');
        if (digits.length === 12 && digits.indexOf('91') === 0) digits = digits.slice(2);
        else if (digits.length === 11 && digits.charAt(0) === '0') digits = digits.slice(1);
        return digits;
    }

    function validPhone(value) {
        return /^[6-9][0-9]{9}$/.test(phoneDigits(value));
    }

    function currentPhone() {
        return phoneDigits(phoneInput ? phoneInput.value : (hiddenPhone ? hiddenPhone.value : ''));
    }

    function setStatus(message, kind) {
        if (!statusEl) return;
        statusEl.textContent = message || '';
        statusEl.className = 'otp-status' + (kind ? ' is-' + kind : '');
        statusEl.hidden = !message;
    }

    function setCooldown(seconds) {
        if (cooldownTimer) clearInterval(cooldownTimer);
        var left = seconds;
        var buttons = [sendBtn, resendBtn].filter(Boolean);
        buttons.forEach(function (btn) {
            btn.disabled = true;
            btn.dataset.original = btn.dataset.original || btn.textContent;
            btn.textContent = btn.dataset.original + ' (' + left + 's)';
        });
        cooldownTimer = setInterval(function () {
            left -= 1;
            if (left <= 0) {
                clearInterval(cooldownTimer);
                cooldownTimer = null;
                buttons.forEach(function (btn) {
                    btn.disabled = false;
                    btn.textContent = btn.dataset.original;
                });
                return;
            }
            buttons.forEach(function (btn) {
                btn.textContent = btn.dataset.original + ' (' + left + 's)';
            });
        }, 1000);
    }

    function refreshSendState() {
        if (!sendBtn || hiddenPhone) return;
        sendBtn.disabled = !!cooldownTimer || !validPhone(phoneInput.value) || verifiedPhone !== null;
    }

    function refreshSubmit() {
        if (!submitBtn) return;
        var email = document.getElementById('email');
        var emailOk = !email || /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email.value.trim());
        var password = document.getElementById('password');
        var passwordOk = !password || password.value.length >= 6;
        submitBtn.disabled = !(verifiedPhone && emailOk && passwordOk);
    }

    function otpValue() {
        return boxes.map(function (box) { return box.value.trim(); }).join('');
    }

    function clearOtp() {
        boxes.forEach(function (box) { box.value = ''; });
        if (boxes[0]) boxes[0].focus();
    }

    function post(url, payload) {
        return fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        }).then(function (response) {
            return response.json().catch(function () {
                return { success: false, message: 'Unexpected server response' };
            });
        });
    }

    function requestOtp() {
        var phone = currentPhone();
        if (!validPhone(phone)) {
            setStatus('Enter a valid 10-digit Indian mobile number', 'error');
            return;
        }
        if (hiddenPhone) hiddenPhone.value = phone;
        setStatus('Sending code…', 'muted');
        sendBtn.disabled = true;
        if (resendBtn) resendBtn.disabled = true;

        post('/api/auth/otp/send', { phone: phone, purpose: purpose })
            .then(function (result) {
                if (!result.success) {
                    setStatus(result.message || 'Could not send the code', 'error');
                    refreshSendState();
                    if (resendBtn) resendBtn.disabled = false;
                    return;
                }
                setStatus('Code sent to +91 ' + phone + ' — valid for a few minutes', 'ok');
                if (block) block.hidden = false;
                if (verifyBtn) verifyBtn.disabled = false;
                setCooldown(60);
                clearOtp();
            })
            .catch(function () {
                setStatus('Network error — please try again', 'error');
                refreshSendState();
            });
    }

    function verifyOtp() {
        var code = otpValue();
        var phone = currentPhone();
        if (code.length !== 6) {
            setStatus('Enter the 6-digit code', 'error');
            return;
        }
        setStatus('Verifying…', 'muted');
        if (verifyBtn) verifyBtn.disabled = true;

        post('/api/auth/otp/verify', { phone: phone, code: code, purpose: purpose })
            .then(function (result) {
                if (!result.success) {
                    setStatus(result.message || 'Incorrect code', 'error');
                    if (verifyBtn) verifyBtn.disabled = false;
                    clearOtp();
                    return;
                }
                verifiedPhone = phone;
                if (phoneInput) phoneInput.readOnly = true;
                if (sendBtn) sendBtn.disabled = true;
                if (resendBtn) resendBtn.hidden = true;
                boxes.forEach(function (box) { box.readOnly = true; });
                if (verifyBtn) verifyBtn.hidden = true;
                if (verifiedEl) {
                    verifiedEl.hidden = false;
                    verifiedEl.textContent = '✓ Mobile number verified';
                }
                setStatus('Mobile verified — finish the form below', 'ok');
                refreshSubmit();
                var target = (result.data && result.data.redirect) || redirectTarget;
                if (target) {
                    setStatus('Mobile verified — taking you in…', 'ok');
                    window.setTimeout(function () { window.location.href = target; }, 700);
                }
            })
            .catch(function () {
                setStatus('Network error — please try again', 'error');
                if (verifyBtn) verifyBtn.disabled = false;
            });
    }

    if (sendBtn) sendBtn.addEventListener('click', function (e) { e.preventDefault(); requestOtp(); });
    if (resendBtn) resendBtn.addEventListener('click', function (e) { e.preventDefault(); requestOtp(); });
    if (verifyBtn) verifyBtn.addEventListener('click', function (e) { e.preventDefault(); verifyOtp(); });

    if (phoneInput) {
        phoneInput.addEventListener('input', function () {
            if (verifiedPhone) return;
            refreshSendState();
        });
        phoneInput.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && sendBtn && !sendBtn.disabled) {
                e.preventDefault();
                requestOtp();
            }
        });
    }

    boxes.forEach(function (box, index) {
        box.addEventListener('input', function () {
            box.value = box.value.replace(/[^0-9]/g, '').slice(0, 1);
            if (box.value && index < boxes.length - 1) boxes[index + 1].focus();
            if (otpValue().length === 6) verifyOtp();
        });
        box.addEventListener('keydown', function (e) {
            if (e.key === 'Backspace' && !box.value && index > 0) {
                boxes[index - 1].focus();
                boxes[index - 1].value = '';
                e.preventDefault();
            }
            if (e.key === 'Enter') {
                e.preventDefault();
                verifyOtp();
            }
        });
        box.addEventListener('paste', function (e) {
            var text = (e.clipboardData || window.clipboardData).getData('text') || '';
            var digits = text.replace(/[^0-9]/g, '');
            if (!digits) return;
            e.preventDefault();
            boxes.forEach(function (target, i) { target.value = digits.charAt(i) || ''; });
            var lastFilled = Math.min(digits.length, boxes.length) - 1;
            if (boxes[lastFilled]) boxes[lastFilled].focus();
            if (otpValue().length === 6) verifyOtp();
        });
    });

    var emailInput = document.getElementById('email');
    if (emailInput) {
        var hint = document.getElementById('emailHint');
        emailInput.addEventListener('input', function () {
            var value = emailInput.value.trim();
            var ok = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value);
            if (!hint) return;
            if (!value) {
                hint.textContent = '';
                hint.className = 'form-text';
            } else if (ok) {
                hint.textContent = '✓ Valid email format — no verification code is sent';
                hint.className = 'form-text text-success';
            } else {
                hint.textContent = 'Enter a valid email, e.g. example@gmail.com';
                hint.className = 'form-text text-danger';
            }
            refreshSubmit();
        });
        emailInput.addEventListener('input', refreshSubmit);
    }

    var passwordInput = document.getElementById('password');
    if (passwordInput) passwordInput.addEventListener('input', refreshSubmit);

    refreshSendState();
    refreshSubmit();
})();
