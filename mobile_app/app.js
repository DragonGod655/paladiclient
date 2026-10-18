// ==========================================================================
// PALADI MOBILE CONTROLLER - CLIENT LOGIC
// Real-time synchronization, Radar, Audio Synthesis & Touch Controls
// ==========================================================================

document.addEventListener('DOMContentLoaded', () => {
    // ----------------------------------------------------------------------
    // State
    // ----------------------------------------------------------------------
    let state = {
        connected: false,
        mode: 'pay',
        target: '',
        amount: '',
        nearbyPlayers: [],
        onlinePlayers: [],
        history: [],
        searchQuery: '',
        isSending: false,
        accounts: [],
        selectedAccId: null
    };

    const getSafeStorage = (key) => {
        try {
            if (typeof window !== 'undefined' && window.localStorage) {
                return window.localStorage.getItem(key);
            }
        } catch (e) {
            console.warn('[Storage] Cannot read localStorage:', e);
        }
        return null;
    };

    const setSafeStorage = (key, val) => {
        try {
            if (typeof window !== 'undefined' && window.localStorage) {
                window.localStorage.setItem(key, val);
            }
        } catch (e) {}
    };

    // Helper to resolve API URL in both Web and WebView / file:// environments
    const getApiUrl = (endpoint) => {
        if (window.location.protocol === 'http:' || window.location.protocol === 'https:') {
            return endpoint;
        }
        let baseUrl = getSafeStorage('paladi_server_url');
        if (!baseUrl && window.PALADI_CONFIG) {
            baseUrl = window.PALADI_CONFIG.server_url || window.PALADI_CONFIG.local_url;
        }
        if (!baseUrl) {
            baseUrl = 'http://192.168.1.199:8080';
        }
        baseUrl = baseUrl.replace(/\/+$/, '');
        const clean = endpoint.replace(/^\/+/, '');
        return `${baseUrl}/${clean}`;
    };

    // ----------------------------------------------------------------------
    // Elements
    // ----------------------------------------------------------------------
    const botIgn = document.getElementById('bot-ign');
    const botStatusBadge = document.getElementById('bot-status-badge');
    const botServer = document.getElementById('bot-server');
    const botBalance = document.getElementById('bot-balance');

    // MultiMC & Dawn Account Switcher Elements
    const multiAccBadge = document.getElementById('multi-acc-badge');
    const multiAccActiveName = document.getElementById('multi-acc-active-name');
    const accPillsContainer = document.getElementById('acc-pills-container');
    const accCardsContainer = document.getElementById('acc-cards-container');
    const navAccCount = document.getElementById('nav-acc-count');
    const btnOpenDetailFromVitals = document.getElementById('btn-open-detail-from-vitals');
    const paySenderName = document.getElementById('pay-sender-name');
    const paySenderBal = document.getElementById('pay-sender-bal');
    const botEmeralds = document.getElementById('bot-emeralds');

    // Sender In-Game Status & Offline Warning Elements
    const senderIngameBadge = document.getElementById('sender-ingame-badge');
    const modalSenderOffline = document.getElementById('modal-sender-offline');
    const senderWarnName = document.getElementById('sender-warn-name');
    const btnCloseSenderOffline = document.getElementById('btn-close-sender-offline');
    const botHpVal = document.getElementById('bot-hp-val');
    const botHpBar = document.getElementById('bot-hp-bar');
    const botFoodVal = document.getElementById('bot-food-val');
    const botFoodBar = document.getElementById('bot-food-bar');
    const botCoords = document.getElementById('bot-coords');
    const liveIndicator = document.getElementById('live-indicator');
    const btnRefresh = document.getElementById('btn-refresh');

    // Earnings & Rate Elements
    const botRatePerHour = document.getElementById('bot-rate-per-hour');
    const botTotalEarned = document.getElementById('bot-total-earned');
    const botUptime = document.getElementById('bot-uptime');
    const btnResetStats = document.getElementById('btn-reset-stats');
    const boxAxeSell = document.getElementById('box-axe-sell');
    const botAxeSell = document.getElementById('bot-axe-sell');

    // Shards & Rates Elements
    const botShards = document.getElementById('bot-shards');
    const botRateSubMoney = document.getElementById('bot-rate-sub-money');
    const botRateSubShards = document.getElementById('bot-rate-sub-shards');
    const botRatePerHourShards = document.getElementById('bot-rate-per-hour-shards');
    const botTotalEarnedShards = document.getElementById('bot-total-earned-shards');

    const tabBtns = document.querySelectorAll('.tab-btn');
    const tabPanels = document.querySelectorAll('.tab-panel');
    const radarPlayerCount = document.getElementById('radar-player-count');

    // Pay Form Controls
    const modeInputs = document.querySelectorAll('input[name="pay-mode"]');
    const modeLabels = document.querySelectorAll('.mode-item');
    const inputTarget = document.getElementById('input-target');
    const btnClearTarget = document.getElementById('btn-clear-target');
    const btnPickFromRadar = document.getElementById('btn-pick-from-radar');

    const inputAmount = document.getElementById('input-amount');
    const btnClearAmount = document.getElementById('btn-clear-amount');
    const amountPreview = document.getElementById('amount-formatted-preview');
    const chips = document.querySelectorAll('.chip');
    const cmdPreviewText = document.getElementById('cmd-preview-text');
    const btnExecutePay = document.getElementById('btn-execute-pay');

    // Radar Controls
    const radarSearch = document.getElementById('radar-search');
    const radarContainer = document.getElementById('radar-players-container');

    // Chat Controls
    const quickMsgBtns = document.querySelectorAll('.btn-quick-msg');
    const inputCustomChat = document.getElementById('input-custom-chat');
    const btnSendCustomChat = document.getElementById('btn-send-custom-chat');

    // History Controls
    const historyContainer = document.getElementById('history-container');
    const btnClearHistoryView = document.getElementById('btn-clear-history-view');
    const btnSampleProof = document.getElementById('btn-sample-proof');

    // Delivery Proof Elements
    const deliveryProofCard = document.getElementById('delivery-proof-card');
    const deliveryProofImg = document.getElementById('delivery-proof-img');
    const btnDownloadProof = document.getElementById('btn-download-proof');
    const btnShareProof = document.getElementById('btn-share-proof');
    const btnPreviewFullscreen = document.getElementById('btn-preview-fullscreen');
    const proofCardSub = document.getElementById('proof-card-sub');

    // Fullscreen Proof Modal Elements
    const modalProofPreview = document.getElementById('modal-proof-preview');
    const proofModalImg = document.getElementById('proof-modal-img');
    const proofModalTitle = document.getElementById('proof-modal-title');
    const proofModalSub = document.getElementById('proof-modal-sub');
    const proofModalDownloadBtn = document.getElementById('proof-modal-download-btn');
    const btnCloseProofModal = document.getElementById('btn-close-proof-modal');

    let currentProofData = null;

    // 4G Remote Controls
    const remote4gBar = document.getElementById('remote-4g-bar');
    const remote4gUrl = document.getElementById('remote-4g-url');
    const btnCopy4g = document.getElementById('btn-copy-4g');
    let current4gLink = '';

    if (btnCopy4g) {
        btnCopy4g.addEventListener('click', async () => {
            if (!current4gLink) {
                showToast('Đang tạo link 4G từ máy tính, đợi một chút...', '⏳');
                return;
            }
            try {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    await navigator.clipboard.writeText(current4gLink);
                } else {
                    const ta = document.createElement('textarea');
                    ta.value = current4gLink;
                    document.body.appendChild(ta);
                    ta.select();
                    document.execCommand('copy');
                    document.body.removeChild(ta);
                }
                playChime('success');
                vibratePhone([30, 40]);
                showToast('Đã chép Link 4G! Lưu lại để mở khi ra ngoài.', '📋');
            } catch (e) {
                showToast(current4gLink, '🌐');
            }
        });
    }

    // ----------------------------------------------------------------------
    // Android PWA / WebAPK Installation
    // ----------------------------------------------------------------------
    let deferredInstallPrompt = null;
    const btnOpenInstallModal = document.getElementById('btn-open-install-modal');
    const modalAndroidInstall = document.getElementById('modal-android-install');
    const btnCloseInstallModal = document.getElementById('btn-close-install-modal');
    const btnTriggerPwaInstall = document.getElementById('btn-trigger-pwa-install');

    // Đăng ký Service Worker
    if ('serviceWorker' in navigator) {
        window.addEventListener('load', () => {
            navigator.serviceWorker.register('/sw.js').catch(() => {});
        });
    }

    // Bắt sự kiện Android Chrome sẵn sàng cài đặt WebAPK
    window.addEventListener('beforeinstallprompt', (e) => {
        e.preventDefault();
        deferredInstallPrompt = e;
        if (btnOpenInstallModal) {
            btnOpenInstallModal.style.boxShadow = '0 0 16px rgba(16, 185, 129, 0.7)';
        }
    });

    if (btnOpenInstallModal && modalAndroidInstall) {
        btnOpenInstallModal.addEventListener('click', () => {
            playChime('click');
            vibratePhone(25);
            modalAndroidInstall.classList.add('show');
        });
    }

    if (btnCloseInstallModal && modalAndroidInstall) {
        btnCloseInstallModal.addEventListener('click', () => {
            modalAndroidInstall.classList.remove('show');
        });
        modalAndroidInstall.addEventListener('click', (e) => {
            if (e.target === modalAndroidInstall) {
                modalAndroidInstall.classList.remove('show');
            }
        });
    }

    // Kiểm tra nếu người dùng mở từ Zalo / Messenger / Facebook
    const inappBrowserWarning = document.getElementById('inapp-browser-warning');
    const ua = navigator.userAgent || '';
    const isInApp = /Zalo|FBAN|FBAV|Instagram|Line|ByteLocale|MicroMessenger/i.test(ua);
    if (isInApp && inappBrowserWarning) {
        inappBrowserWarning.style.display = 'flex';
    }

    const btnDownloadApk = document.getElementById('btn-download-apk');
    if (btnDownloadApk) {
        btnDownloadApk.addEventListener('click', () => {
            playChime('success');
            vibratePhone([40, 60]);
            showToast('Đang tải file cài đặt PaladiPay.apk về máy...', '⬇️');
        });
    }

    if (btnTriggerPwaInstall) {
        btnTriggerPwaInstall.addEventListener('click', async () => {
            playChime('success');
            vibratePhone([30, 40]);
            if (deferredInstallPrompt) {
                try {
                    deferredInstallPrompt.prompt();
                    const choice = await deferredInstallPrompt.userChoice;
                    if (choice.outcome === 'accepted') {
                        showToast('Đang cài đặt App PaladiPay ra màn hình...', '📲');
                        if (modalAndroidInstall) modalAndroidInstall.classList.remove('show');
                    }
                    deferredInstallPrompt = null;
                } catch (err) {
                    showToast('Đang tự động chuyển sang tải file APK...', '⬇️');
                    window.location.href = '/PaladiPay.apk';
                }
            } else {
                showToast('Đang tự động tải file PaladiPay.apk về máy...', '⬇️');
                window.location.href = '/PaladiPay.apk';
            }
        });
    }

    // Toast
    const toastNotify = document.getElementById('toast-notify');
    const toastIcon = document.getElementById('toast-icon');
    const toastMsg = document.getElementById('toast-msg');
    let toastTimeout = null;

    // ----------------------------------------------------------------------
    // Web Audio Synthesizer (Haptic & Sound without external files)
    // ----------------------------------------------------------------------
    const playChime = (type = 'success') => {
        try {
            const ctx = new (window.AudioContext || window.webkitAudioContext)();
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();

            osc.connect(gain);
            gain.connect(ctx.destination);

            if (type === 'success') {
                osc.type = 'sine';
                osc.frequency.setValueAtTime(523.25, ctx.currentTime); // C5
                osc.frequency.exponentialRampToValueAtTime(783.99, ctx.currentTime + 0.12); // G5
                gain.gain.setValueAtTime(0.12, ctx.currentTime);
                gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.25);
                osc.start(ctx.currentTime);
                osc.stop(ctx.currentTime + 0.25);
            } else if (type === 'click') {
                osc.type = 'triangle';
                osc.frequency.setValueAtTime(440, ctx.currentTime);
                gain.gain.setValueAtTime(0.05, ctx.currentTime);
                gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.05);
                osc.start(ctx.currentTime);
                osc.stop(ctx.currentTime + 0.05);
            } else if (type === 'error') {
                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(220, ctx.currentTime);
                gain.gain.setValueAtTime(0.1, ctx.currentTime);
                gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.2);
                osc.start(ctx.currentTime);
                osc.stop(ctx.currentTime + 0.2);
            } else if (type === 'payout') {
                // Triumphant Cash Arpeggio (C5 -> E5 -> G5 -> C6)
                const now = ctx.currentTime;
                const notes = [523.25, 659.25, 783.99, 1046.50];
                notes.forEach((freq, idx) => {
                    const o = ctx.createOscillator();
                    const g = ctx.createGain();
                    o.type = 'triangle';
                    o.frequency.setValueAtTime(freq, now + idx * 0.11);
                    g.gain.setValueAtTime(0.18, now + idx * 0.11);
                    g.gain.exponentialRampToValueAtTime(0.001, now + (idx + 1) * 0.11 + 0.18);
                    o.connect(g);
                    g.connect(ctx.destination);
                    o.start(now + idx * 0.11);
                    o.stop(now + (idx + 1) * 0.11 + 0.2);
                });
            }
        } catch (e) {
            // AudioContext not allowed or unsupported
        }
    };

    const vibratePhone = (pattern = [30]) => {
        if ('vibrate' in navigator) {
            try { navigator.vibrate(pattern); } catch (e) {}
        }
    };

    const showToast = (message, icon = '🚀', isError = false) => {
        if (toastTimeout) clearTimeout(toastTimeout);
        toastIcon.textContent = icon;
        toastMsg.textContent = message;

        if (isError) {
            toastNotify.classList.add('error');
        } else {
            toastNotify.classList.remove('error');
        }

        toastNotify.classList.add('show');
        toastTimeout = setTimeout(() => {
            toastNotify.classList.remove('show');
        }, 2800);
    };

    // ----------------------------------------------------------------------
    // Tab Navigation
    // ----------------------------------------------------------------------
    const switchTab = (tabId) => {
        tabBtns.forEach(btn => {
            const isCurrent = btn.dataset.target === tabId;
            btn.classList.toggle('active', isCurrent);
        });

        tabPanels.forEach(panel => {
            const isCurrent = panel.id === tabId;
            panel.classList.toggle('active', isCurrent);
        });

        playChime('click');
        vibratePhone(20);
    };

    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => switchTab(btn.dataset.target));
    });

    if (btnPickFromRadar) {
        btnPickFromRadar.addEventListener('click', () => switchTab('tab-radar-chat'));
    }

    // ----------------------------------------------------------------------
    // Command Preview Calculation
    // ----------------------------------------------------------------------
    const parseAmount = (val) => {
        if (!val) return '';
        let s = val.trim().toLowerCase();
        let mult = 1;
        if (s.endsWith('k')) { mult = 1000; s = s.slice(0, -1); }
        else if (s.endsWith('m')) { mult = 1000000; s = s.slice(0, -1); }
        else if (s.endsWith('b')) { mult = 1000000000; s = s.slice(0, -1); }

        const num = parseFloat(s);
        if (isNaN(num)) return val;
        return (num * mult).toLocaleString('vi-VN');
    };

    const updateCommandPreview = () => {
        state.target = inputTarget.value.trim();
        state.amount = inputAmount.value.trim();

        const previewAmt = parseAmount(state.amount);
        amountPreview.textContent = previewAmt ? `$${previewAmt}` : '0';

        const tgt = state.target || '[người_chơi]';
        const amt = state.amount || '[số_tiền]';

        if (state.mode === 'msg') {
            cmdPreviewText.textContent = `/msg ${tgt} Đã nhận được yêu cầu giao dịch!`;
        } else if (state.mode === 'trade') {
            cmdPreviewText.textContent = `/trade ${tgt}`;
        } else {
            cmdPreviewText.textContent = `/${state.mode} ${tgt} ${amt}`;
        }
    };

    modeInputs.forEach(input => {
        input.addEventListener('change', () => {
            state.mode = input.value;
            modeLabels.forEach(lbl => {
                const isSelected = lbl.querySelector('input').checked;
                lbl.classList.toggle('active', isSelected);
            });
            playChime('click');
            vibratePhone(15);
            updateCommandPreview();
        });
    });

    // ----------------------------------------------------------------------
    // Seller In-Game Status Checker
    // ----------------------------------------------------------------------
    const isSenderOnlineInGame = () => {
        if (!state.connected) return false;
        if (state.lastStatusData && state.lastStatusData.inGame === false) return false;
        if (state.lastStatusData && state.lastStatusData.player && state.lastStatusData.player.server === 'Offline (Chưa vào game)') return false;
        if (!state.accounts || state.accounts.length === 0) return false;
        return true;
    };

    inputTarget.addEventListener('input', updateCommandPreview);
    inputAmount.addEventListener('input', updateCommandPreview);

    btnClearTarget.addEventListener('click', () => {
        inputTarget.value = '';
        updateCommandPreview();
        inputTarget.focus();
    });

    btnClearAmount.addEventListener('click', () => {
        inputAmount.value = '';
        updateCommandPreview();
        inputAmount.focus();
    });

    chips.forEach(chip => {
        chip.addEventListener('click', () => {
            inputAmount.value = chip.dataset.amt;
            updateCommandPreview();
            playChime('click');
            vibratePhone(25);
        });
    });

    // ----------------------------------------------------------------------
    // Execute Pay Action (Chặn khi tài khoản BÁN chưa online trong game)
    // ----------------------------------------------------------------------
    const performPay = async () => {
        if (state.isSending) return;

        state.target = inputTarget.value.trim();
        state.amount = inputAmount.value.trim();

        if (!state.target) {
            showToast('Vui lòng nhập tên người chơi nhận tiền!', '⚠️', true);
            playChime('error');
            vibratePhone([50, 50, 50]);
            inputTarget.focus();
            return;
        }

        if (state.mode !== 'trade' && !state.amount) {
            showToast('Vui lòng nhập số tiền cần pay!', '⚠️', true);
            playChime('error');
            vibratePhone([50, 50, 50]);
            inputAmount.focus();
            return;
        }

        // Chặn nếu tài khoản NGƯỜI BÁN của bạn đang OFFLINE (chưa vào server)
        if (!isSenderOnlineInGame()) {
            const currentSender = (paySenderName && paySenderName.textContent !== 'Chờ kết nối bot...') ? paySenderName.textContent : (state.lastStatusData?.player?.name || 'Tài khoản của bạn');
            if (senderWarnName) senderWarnName.textContent = currentSender;
            if (modalSenderOffline) modalSenderOffline.style.display = 'flex';
            playChime('error');
            vibratePhone([100, 50, 100]);
            return;
        }

        state.isSending = true;
        btnExecutePay.style.opacity = '0.6';
        btnExecutePay.querySelector('.btn-text').textContent = '⏳ ĐANG GỬI LỆNH VÀO GAME...';

        try {
            const resp = await fetch(getApiUrl('/api/pay'), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    target: state.target,
                    amount: state.amount,
                    mode: state.mode,
                    acc: state.selectedAccId
                })
            });

            const data = await resp.json();

            // Nếu tài khoản bán của bạn bị offline
            if (!data.success && data.senderOffline) {
                const currentSender = (paySenderName && paySenderName.textContent !== 'Chờ kết nối bot...') ? paySenderName.textContent : (state.lastStatusData?.player?.name || 'Tài khoản của bạn');
                if (senderWarnName) senderWarnName.textContent = currentSender;
                if (modalSenderOffline) modalSenderOffline.style.display = 'flex';
                playChime('error');
                vibratePhone([100, 50, 100]);
                return;
            }

            if (data.success) {
                playChime('success');
                vibratePhone([40, 60, 40]);
                showToast(`Đã pay thành công cho ${state.target}!`, '💸');

                // Thêm vào log lịch sử ngay lập tức
                const historyEntry = {
                    time: new Date().toLocaleTimeString('vi-VN'),
                    type: state.mode.toUpperCase(),
                    item: state.target,
                    amount: state.amount,
                    status: 'THÀNH CÔNG',
                    proof: data.proof || null
                };
                addLocalHistory(historyEntry);

                // Hiển thị ảnh bằng chứng giao hàng (Delivery Proof)
                if (data.proof) {
                    showDeliveryProofCard(data.proof);
                }
            } else {
                playChime('error');
                vibratePhone([80, 80]);
                showToast(data.error || 'Lỗi khi gửi lệnh tới game', '❌', true);
            }
        } catch (err) {
            playChime('error');
            vibratePhone([80, 80]);
            showToast('Không thể kết nối tới Minecraft Client!', '📡', true);
        } finally {
            state.isSending = false;
            btnExecutePay.style.opacity = '1';
            btnExecutePay.querySelector('.btn-text').textContent = '🚀 BẤM PAY CHO KHÁCH NGAY';
        }
    };

    btnExecutePay.addEventListener('click', () => performPay());

    if (btnCloseSenderOffline) {
        btnCloseSenderOffline.addEventListener('click', () => {
            if (modalSenderOffline) modalSenderOffline.style.display = 'none';
            playChime('click');
        });
    }

    // ----------------------------------------------------------------------
    // Quick Chat Messages
    // ----------------------------------------------------------------------
    quickMsgBtns.forEach(btn => {
        btn.addEventListener('click', async () => {
            const msg = btn.dataset.msg;
            if (!msg) return;

            playChime('click');
            vibratePhone(25);

            try {
                const resp = await fetch(getApiUrl('/api/command'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ command: msg, acc: state.selectedAccId })
                });
                const data = await resp.json();
                if (data.success) {
                    playChime('success');
                    showToast('Đã gửi tin nhắn vào game!', '💬');
                } else {
                    showToast(data.error || 'Lỗi gửi chat', '❌', true);
                }
            } catch (err) {
                showToast('Chưa kết nối được với game!', '📡', true);
            }
        });
    });

    btnSendCustomChat.addEventListener('click', async () => {
        const msg = inputCustomChat.value.trim();
        if (!msg) return;

        playChime('click');
        try {
            const resp = await fetch(getApiUrl('/api/command'), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ command: msg, acc: state.selectedAccId })
            });
            const data = await resp.json();
            if (data.success) {
                playChime('success');
                vibratePhone(25);
                showToast('Đã gửi chat thành công!', '💬');
                inputCustomChat.value = '';
            } else {
                showToast(data.error || 'Lỗi gửi chat', '❌', true);
            }
        } catch (err) {
            showToast('Chưa kết nối được với game!', '📡', true);
        }
    });

    // ----------------------------------------------------------------------
    // Radar Render & Selection
    // ----------------------------------------------------------------------
    radarSearch.addEventListener('input', (e) => {
        state.searchQuery = e.target.value.toLowerCase().trim();
        renderRadar();
    });

    const selectPlayer = (playerName) => {
        inputTarget.value = playerName;
        updateCommandPreview();
        playChime('success');
        vibratePhone([30, 40]);
        showToast(`Đã chọn ${playerName} làm khách!`, '👤');
        switchTab('tab-pay');
    };

    const renderRadar = () => {
        const players = state.nearbyPlayers.filter(p => p.type === 'player');
        radarPlayerCount.textContent = players.length;

        const filtered = players.filter(p => {
            if (!state.searchQuery) return true;
            return p.name.toLowerCase().includes(state.searchQuery);
        });

        if (filtered.length === 0) {
            radarContainer.innerHTML = `
                <div class="empty-state">
                    <span class="empty-icon">👥</span>
                    <p>${state.searchQuery ? 'Không tìm thấy người chơi khớp với từ khóa.' : 'Không có người chơi nào trong phạm vi 64 block.'}</p>
                    <small>Khi có khách lại gần bot, tên sẽ tự động hiện ở đây!</small>
                </div>
            `;
            return;
        }

        radarContainer.innerHTML = filtered.map(p => {
            const isSelected = p.name === inputTarget.value.trim();
            const holding = p.holding && p.holding !== 'Air' ? `Cầm: ${p.holding}` : 'Tay không';
            return `
                <div class="player-card ${isSelected ? 'selected' : ''}" data-name="${p.name}">
                    <div class="player-card-left">
                        <div class="player-head-box">👤</div>
                        <div class="player-card-details">
                            <div class="player-name-row">
                                <span class="player-name">${p.name}</span>
                                <span class="player-dist-badge">${p.dist}m</span>
                            </div>
                            <span class="player-sub-meta">❤️ ${p.health} HP • ${holding}</span>
                        </div>
                    </div>
                    <span class="btn-select-player-pill">${isSelected ? 'ĐÃ CHỌN' : 'CHỌN'}</span>
                </div>
            `;
        }).join('');

        // Attach click listener
        radarContainer.querySelectorAll('.player-card').forEach(card => {
            card.addEventListener('click', () => {
                const name = card.dataset.name;
                selectPlayer(name);
            });
        });
    };

    // ----------------------------------------------------------------------
    // History Render
    // ----------------------------------------------------------------------
    const addLocalHistory = (entry) => {
        state.history.unshift(entry);
        if (state.history.length > 50) state.history.pop();
        renderHistory();
    };

    const renderHistory = () => {
        if (!state.history || state.history.length === 0) {
            historyContainer.innerHTML = `
                <div class="empty-state">
                    <span class="empty-icon">📄</span>
                    <p>Chưa có giao dịch pay nào trong phiên này.</p>
                </div>
            `;
            return;
        }

        historyContainer.innerHTML = state.history.map((item, idx) => {
            const hasProof = !!item.proof;
            const proofHtml = hasProof ? `
                <div class="history-card-proof-wrap">
                    <img src="${item.proof.url}" alt="Proof" class="history-proof-thumb" data-proof-idx="${idx}" title="Bấm để xem ảnh to" />
                    <a href="${item.proof.download_url || (item.proof.url + '?download=1')}" download="${item.proof.filename || 'Eldorado_Proof.png'}" class="btn-history-proof-dl">
                        ⬇️ Tải ảnh proof
                    </a>
                </div>
            ` : '';

            return `
                <div class="history-card">
                    <div class="history-card-left">
                        <span class="history-title">${item.type || 'PAY'}: ${item.item || '-'}</span>
                        <span class="history-meta">Thời gian: ${item.time || ''}</span>
                    </div>
                    <span class="history-badge success">${item.amount || 'Done'}</span>
                    ${proofHtml}
                </div>
            `;
        }).join('');

        // Bắt sự kiện click vào ảnh thu nhỏ để phóng to
        historyContainer.querySelectorAll('.history-proof-thumb').forEach(thumb => {
            thumb.addEventListener('click', () => {
                const idx = parseInt(thumb.dataset.proofIdx, 10);
                const item = state.history[idx];
                if (item && item.proof) openProofModal(item.proof);
            });
        });
    };

    const openProofModal = (proof) => {
        if (!proof || !modalProofPreview) return;
        currentProofData = proof;
        if (proofModalImg) proofModalImg.src = proof.url + '?t=' + Date.now();
        if (proofModalTitle) proofModalTitle.textContent = `Bằng Chứng: ${proof.target || 'Khách'}`;
        if (proofModalSub) proofModalSub.textContent = `Số tiền: ${proof.amount || ''} • Giờ: ${proof.time || ''}`;
        if (proofModalDownloadBtn) {
            proofModalDownloadBtn.href = proof.download_url || (proof.url + '?download=1');
            proofModalDownloadBtn.download = proof.filename || `Eldorado_Pay_${proof.target || 'Delivery'}.png`;
        }
        modalProofPreview.style.display = 'flex';
        playChime('click');
        vibratePhone(25);
    };

    const closeProofModal = () => {
        if (modalProofPreview) {
            modalProofPreview.style.display = 'none';
        }
    };

    if (btnCloseProofModal) {
        btnCloseProofModal.addEventListener('click', closeProofModal);
    }
    if (modalProofPreview) {
        modalProofPreview.addEventListener('click', (e) => {
            if (e.target === modalProofPreview) closeProofModal();
        });
    }

    const showDeliveryProofCard = (proof) => {
        if (!proof || !deliveryProofCard) return;
        currentProofData = proof;
        if (deliveryProofImg) {
            deliveryProofImg.src = proof.url + '?t=' + Date.now();
        }
        if (proofCardSub) {
            proofCardSub.textContent = `Đã giao: ${proof.target} ($${proof.amount}) lúc ${proof.time || ''}`;
        }
        if (btnDownloadProof) {
            btnDownloadProof.href = proof.download_url || (proof.url + '?download=1');
            btnDownloadProof.download = proof.filename || `Eldorado_Pay_${proof.target}.png`;
        }
        deliveryProofCard.style.display = 'block';
        deliveryProofCard.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    };

    if (btnPreviewFullscreen) {
        btnPreviewFullscreen.addEventListener('click', () => {
            if (currentProofData) openProofModal(currentProofData);
        });
    }

    if (btnShareProof) {
        btnShareProof.addEventListener('click', async () => {
            if (!currentProofData) return;
            playChime('click');
            if (navigator.share) {
                try {
                    await navigator.share({
                        title: `Bằng chứng pay ${currentProofData.target}`,
                        text: `Giao hàng thành công cho ${currentProofData.target} - $${currentProofData.amount}`,
                        url: window.location.origin + currentProofData.url
                    });
                    showToast('Đã mở menu chia sẻ!', '📤');
                    return;
                } catch (e) {}
            }
            if (btnDownloadProof) btnDownloadProof.click();
            showToast('Đang tải ảnh về máy...', '⬇️');
        });
    }

    if (btnSampleProof) {
        btnSampleProof.addEventListener('click', async () => {
            playChime('click');
            vibratePhone(20);
            showToast('Đang chụp ảnh bằng chứng giao hàng mẫu...', '📸');

            const testTarget = inputTarget.value.trim() || 'Shedynek';
            const testAmount = inputAmount.value.trim() || '40M';

            try {
                const resp = await fetch(getApiUrl('/api/create-proof'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ target: testTarget, amount: testAmount, acc: state.selectedAccId })
                });
                const res = await resp.json();
                if (res.success && res.proof) {
                    playChime('success');
                    vibratePhone([30, 50]);
                    showToast('Đã chụp xong bằng chứng giao hàng!', '📸');
                    addLocalHistory({
                        time: new Date().toLocaleTimeString('vi-VN'),
                        type: 'PAY',
                        item: testTarget,
                        amount: testAmount,
                        status: 'THÀNH CÔNG',
                        proof: res.proof
                    });
                    showDeliveryProofCard(res.proof);
                    openProofModal(res.proof);
                }
            } catch (err) {
                showToast('Lỗi khi tạo ảnh proof!', '⚠️', true);
            }
        });
    }

    // Tự động tải danh sách các ảnh proof đã có từ máy chủ
    const loadSavedProofs = async () => {
        try {
            const resp = await fetch(getApiUrl('/api/delivery-proofs'));
            const data = await resp.json();
            if (data && data.proofs && data.proofs.length > 0) {
                data.proofs.forEach(p => {
                    if (!state.history.some(h => h.proof && h.proof.id === p.id)) {
                        state.history.push({
                            time: p.time || 'Vừa xong',
                            type: 'PAY',
                            item: p.target,
                            amount: p.amount,
                            status: 'THÀNH CÔNG',
                            proof: p
                        });
                    }
                });
                renderHistory();
            }
        } catch (e) {}
    };
    loadSavedProofs();

    btnClearHistoryView.addEventListener('click', () => {
        state.history = [];
        renderHistory();
        playChime('click');
        showToast('Đã xóa danh sách hiển thị lịch sử', '🧹');
    });

    // ----------------------------------------------------------------------
    // MultiMC Account Switcher & Full Cards Rendering (Trang 1: Tài Khoản)
    // ----------------------------------------------------------------------
    const renderAccountPills = (accounts, activeId) => {
        if (navAccCount) {
            navAccCount.textContent = accounts ? accounts.length : 0;
        }

        if (!accounts || accounts.length === 0) {
            if (accPillsContainer) {
                accPillsContainer.innerHTML = '<div class="acc-pill-empty">Đang tìm kiếm Minecraft Client trong MultiMC & Dawn Launcher...</div>';
            }
            if (accCardsContainer) {
                accCardsContainer.innerHTML = `
                    <div class="empty-state">
                        <span class="empty-icon">🎮</span>
                        <p>Chưa phát hiện tài khoản Minecraft nào đang mở.</p>
                        <small>Hãy khởi động game qua Dawn Launcher, MultiMC hoặc Feather Client trên PC!</small>
                    </div>
                `;
            }
            if (multiAccBadge) multiAccBadge.textContent = '0 Acc';
            if (multiAccActiveName) multiAccActiveName.innerHTML = 'Đang điều khiển: <strong>Chờ acc...</strong>';
            if (paySenderName) paySenderName.textContent = 'Chờ kết nối bot...';
            if (paySenderBal) paySenderBal.textContent = 'Bal: $0';
            return;
        }

        if (multiAccBadge) {
            multiAccBadge.textContent = `${accounts.length} Acc`;
        }

        const activeAcc = accounts.find(a => String(a.id) === String(activeId)) || accounts[0];
        if (multiAccActiveName && activeAcc) {
            multiAccActiveName.innerHTML = `Đang điều khiển: <strong>${activeAcc.name}</strong> (${activeAcc.balance})`;
        }
        if (paySenderName && activeAcc) {
            paySenderName.textContent = activeAcc.name;
        }
        if (paySenderBal && activeAcc) {
            paySenderBal.textContent = `(Bal: ${activeAcc.balance})`;
        }
        if (senderIngameBadge) {
            const isOnline = activeAcc.server && !activeAcc.server.toLowerCase().includes('offline');
            if (isOnline) {
                senderIngameBadge.textContent = '🟢 ĐANG TRONG GAME (SẴN SÀNG PAY)';
                senderIngameBadge.className = 'sender-ingame-badge online';
            } else {
                senderIngameBadge.textContent = '🔴 CHƯA VÀO GAME (OFFLINE)';
                senderIngameBadge.className = 'sender-ingame-badge offline';
            }
        }

        // 1. Render Top Pills Selector
        if (accPillsContainer) {
            accPillsContainer.innerHTML = accounts.map(acc => {
                const isCur = String(acc.id) === String(activeId);
                const shardStr = acc.shards ? ` • ${acc.shards}` : '';
                const shardRate = acc.shardsPerHour ? ` (${acc.shardsPerHour})` : '';
                return `
                    <div class="acc-pill ${isCur ? 'active' : ''}" data-acc-id="${acc.id}">
                        <div class="acc-pill-top">
                            <span class="acc-pill-dot online"></span>
                            <span class="acc-pill-name">${acc.name}</span>
                        </div>
                        <div class="acc-pill-bottom">
                            <span class="acc-pill-bal">${acc.balance}${shardStr}</span>
                            <span class="acc-pill-rate">${acc.perHour || ''}${shardRate}</span>
                        </div>
                    </div>
                `;
            }).join('');

            accPillsContainer.querySelectorAll('.acc-pill').forEach(pill => {
                pill.addEventListener('click', async () => {
                    const id = pill.dataset.accId;
                    state.selectedAccId = id;
                    playChime('click');
                    vibratePhone(25);
                    const chosen = accounts.find(a => String(a.id) === String(id));
                    if (chosen) {
                        showToast(`Tài khoản: ${chosen.name} (${chosen.balance})`, '🎮');
                        openAccountDetailModal(id, chosen);
                    } else {
                        openAccountDetailModal(id);
                    }
                    await pollStatus();
                });
            });
        }

        // 2. Render Full Account Cards on Trang 1 (Bấm vào acc nào xem hết thông tin acc đó)
        if (accCardsContainer) {
            accCardsContainer.innerHTML = accounts.map(acc => {
                const isCur = String(acc.id) === String(activeId);
                const shardStr = acc.shards || '0 ★';
                const shardRate = acc.shardsPerHour ? `(${acc.shardsPerHour})` : '(+0/h)';
                const moneyRate = acc.perHour ? (acc.perHour.startsWith('+') ? acc.perHour : '+' + acc.perHour) : '+$0/h';
                const skinUrl = `https://minotar.net/helm/${encodeURIComponent(acc.name)}/48.png`;

                return `
                    <div class="acc-full-card ${isCur ? 'active' : ''}" data-acc-id="${acc.id}">
                        <div class="acc-card-header">
                            <div class="acc-card-avatar-box">
                                <img class="acc-card-skin" src="${skinUrl}" onerror="this.src='data:image/svg+xml;utf8,<svg xmlns=\\'http://www.w3.org/2000/svg\\' width=\\'44\\' height=\\'44\\'><rect width=\\'44\\' height=\\'44\\' fill=\\'%231e293b\\' rx=\\'8\\'/><text x=\\'50%\\' y=\\'55%\\' dominant-baseline=\\'middle\\' text-anchor=\\'middle\\' font-size=\\'20\\'>🤖</text></svg>'" alt="${acc.name}">
                            </div>
                            <div class="acc-card-info">
                                <div class="acc-card-name-row">
                                    <span class="acc-card-name">${acc.name}</span>
                                    <span class="acc-card-status">${isCur ? 'Đang điều khiển' : 'Online'}</span>
                                    <span class="acc-card-launcher-tag">${(acc.launcher || 'MultiMC / Dawn').replace('Launcher', '').trim()}</span>
                                </div>
                                <span class="acc-card-server">🌐 ${acc.server || 'Đã kết nối'} (Port: ${acc.port})</span>
                            </div>
                            ${isCur ? '<span class="acc-badge-active">ĐANG CHỌN</span>' : ''}
                        </div>

                        <div class="acc-card-balances">
                            <div class="acc-cbal-item">
                                <span class="acc-cbal-lbl">💵 SỐ DƯ TIỀN ($):</span>
                                <span class="acc-cbal-val">${acc.balance}</span>
                                <span class="acc-cbal-rate">${moneyRate}</span>
                            </div>
                            <div class="acc-cbal-item shards">
                                <span class="acc-cbal-lbl">🔮 TIỀN SHARD (★):</span>
                                <span class="acc-cbal-val shards">${shardStr}</span>
                                <span class="acc-cbal-rate shards">${shardRate}</span>
                            </div>
                        </div>

                        <div class="acc-card-footer">
                            <button type="button" class="btn-acc-view-detail" data-acc-id="${acc.id}">
                                🔍 Xem hết thông tin acc này
                            </button>
                            <button type="button" class="btn-acc-pick-pay" data-acc-id="${acc.id}">
                                💸 Dùng acc này để Pay
                            </button>
                        </div>
                    </div>
                `;
            }).join('');

            accCardsContainer.querySelectorAll('.acc-full-card').forEach(card => {
                const id = card.dataset.accId;
                const chosen = accounts.find(a => String(a.id) === String(id));

                // Bấm vào thân thẻ hoặc nút xem chi tiết -> Mở modal xem toàn bộ thông tin
                card.addEventListener('click', async (e) => {
                    if (e.target.closest('.btn-acc-pick-pay')) return;
                    state.selectedAccId = id;
                    playChime('click');
                    vibratePhone(25);
                    openAccountDetailModal(id, chosen);
                    await pollStatus();
                });

                // Nút chọn dùng acc này để Pay -> Chuyển sang Trang 2 (Pay)
                const btnPay = card.querySelector('.btn-acc-pick-pay');
                if (btnPay) {
                    btnPay.addEventListener('click', async (e) => {
                        e.stopPropagation();
                        state.selectedAccId = id;
                        playChime('success');
                        vibratePhone([30, 40]);
                        await pollStatus();
                        switchTab('tab-pay');
                        showToast(`Đã chọn acc ${chosen?.name || id} để thực hiện lệnh Pay!`, '💸');
                    });
                }
            });
        }
    };

    // ----------------------------------------------------------------------
    // Account Detail Modal (Bấm vào acc nào hiện hết thông tin acc đó)
    // ----------------------------------------------------------------------
    const modalAccountDetail = document.getElementById('modal-account-detail');
    const btnCloseAccountModal = document.getElementById('btn-close-account-modal');
    const btnModalSelectPay = document.getElementById('btn-modal-select-pay');
    const btnModalSwitchChat = document.getElementById('btn-modal-switch-chat');
    const btnCopyCoords = document.getElementById('btn-copy-coords');
    const btnOpenMyAcc = document.getElementById('btn-open-my-acc');

    const accModalName = document.getElementById('acc-modal-name');
    const accModalStatus = document.getElementById('acc-modal-status');
    const accModalServer = document.getElementById('acc-modal-server');
    const accModalPort = document.getElementById('acc-modal-port');
    const accModalSkinImg = document.getElementById('acc-modal-skin-img');
    const accModalAvatarFallback = document.getElementById('acc-modal-avatar-fallback');
    const accModalBal = document.getElementById('acc-modal-bal');
    const accModalRate = document.getElementById('acc-modal-rate');
    const accModalShards = document.getElementById('acc-modal-shards');
    const accModalShardsRate = document.getElementById('acc-modal-shards-rate');
    const accModalHp = document.getElementById('acc-modal-hp');
    const accModalHpBar = document.getElementById('acc-modal-hp-bar');
    const accModalFood = document.getElementById('acc-modal-food');
    const accModalFoodBar = document.getElementById('acc-modal-food-bar');
    const accModalUptime = document.getElementById('acc-modal-uptime');
    const accModalEmeralds = document.getElementById('acc-modal-emeralds');
    const accModalEarned = document.getElementById('acc-modal-earned');
    const accModalEarnedShards = document.getElementById('acc-modal-earned-shards');
    const accModalAxeSell = document.getElementById('acc-modal-axe-sell');
    const accModalNearby = document.getElementById('acc-modal-nearby');
    const accModalCoords = document.getElementById('acc-modal-coords');
    const accModalLauncher = document.getElementById('acc-modal-launcher');

    const updateAccountDetailModalContent = (data) => {
        if (!data) return;
        const playerObj = data.player || {};
        const pName = playerObj.name || data.name || 'Paladi Bot';
        const pServer = playerObj.server || data.server || 'Đã kết nối';
        const pPort = data.port || playerObj.port || '';
        const pBal = playerObj.balance || data.balance || '$0';
        const pRate = data.earnings?.perHourFormatted || data.perHour || '+$0/h';
        const pShards = data.shards?.balanceFormatted || data.shards || playerObj.shards || '0 ★';
        const pShardsRate = data.shards?.perHourFormatted || data.shardsPerHour || '+0/h';

        if (accModalName) accModalName.textContent = pName;
        if (accModalServer) accModalServer.textContent = `🌐 Server: ${pServer}`;
        if (accModalPort) accModalPort.textContent = pPort ? `PORT: ${pPort}` : 'PORT: Đang dò';
        const pLauncher = data.launcher || playerObj.launcher || 'MultiMC / Dawn';
        if (accModalLauncher) {
            accModalLauncher.textContent = pLauncher.toLowerCase().includes('dawn') ? '🎮 Dawn Launcher' : (pLauncher.toLowerCase().includes('multi') ? '🕹️ MultiMC' : '🎮 ' + pLauncher);
        }
        if (accModalBal) accModalBal.textContent = pBal;
        if (accModalRate) accModalRate.textContent = `(${pRate ? (pRate.startsWith('+') ? pRate : '+' + pRate) : '+$0/h'})`;
        
        if (accModalShards) accModalShards.textContent = pShards;
        if (accModalShardsRate) accModalShardsRate.textContent = `(${pShardsRate || '+0/h'})`;

        // Skin image with fallback
        if (accModalSkinImg && pName && pName !== 'Chờ kết nối MultiMC' && pName !== 'Paladi Client') {
            accModalSkinImg.src = `https://minotar.net/helm/${encodeURIComponent(pName)}/64.png`;
            accModalSkinImg.style.display = 'block';
            if (accModalAvatarFallback) accModalAvatarFallback.style.display = 'none';
            accModalSkinImg.onerror = () => {
                accModalSkinImg.style.display = 'none';
                if (accModalAvatarFallback) accModalAvatarFallback.style.display = 'flex';
            };
        } else if (accModalAvatarFallback) {
            if (accModalSkinImg) accModalSkinImg.style.display = 'none';
            accModalAvatarFallback.style.display = 'flex';
        }

        // HP & Food
        const hp = parseFloat(playerObj.health ?? data.health) || 20;
        const maxHp = parseFloat(playerObj.maxHealth ?? data.maxHealth) || 20;
        if (accModalHp) accModalHp.textContent = `${hp.toFixed(1)} / ${maxHp.toFixed(0)}`;
        if (accModalHpBar) accModalHpBar.style.width = `${Math.min(100, (hp / maxHp) * 100)}%`;

        const food = parseInt(playerObj.food ?? data.food) || 20;
        if (accModalFood) accModalFood.textContent = `${food} / 20`;
        if (accModalFoodBar) accModalFoodBar.style.width = `${Math.min(100, (food / 20) * 100)}%`;

        // Uptime & Earned
        const uptime = data.earnings?.uptimeFormatted || data.uptime || '00h 00m 00s';
        const earned = data.earnings?.totalEarnedFormatted || data.totalEarned || '$0';
        const earnedShards = data.shards?.totalEarnedFormatted || data.totalShardsEarned || '0';
        if (accModalUptime) accModalUptime.textContent = uptime;
        if (accModalEarned) accModalEarned.textContent = `+${earned.replace(/^\+/, '')}`;
        if (accModalEarnedShards) accModalEarnedShards.textContent = `+${earnedShards.replace(/^\+/, '').replace('★', '').trim()} ★`;

        // Emeralds
        const emeralds = data.inventory?.emeralds ?? data.emeralds ?? 0;
        if (accModalEmeralds) accModalEmeralds.textContent = `${emeralds} 💎`;

        // Axe Sell
        const axeHits = data.earnings?.axeSell?.hits ?? data.axeSellHits ?? 0;
        const axeEarned = data.earnings?.axeSell?.earnedFormatted ?? data.axeSellEarned ?? '$0';
        if (accModalAxeSell) {
            accModalAxeSell.textContent = axeHits > 0 ? `+${axeEarned} (${axeHits} lần)` : '+$0 (0 lần)';
        }

        // Nearby players
        const nearbyCount = Array.isArray(data.nearbyEntities) ? data.nearbyEntities.length : (data.nearbyCount ?? 0);
        if (accModalNearby) accModalNearby.textContent = `${nearbyCount} người`;

        // Coords
        let coordStr = 'X: 0.0 | Y: 64.0 | Z: 0.0';
        if (playerObj.x !== undefined) {
            coordStr = `X: ${playerObj.x} | Y: ${playerObj.y} | Z: ${playerObj.z}`;
        } else if (data.x !== undefined) {
            coordStr = `X: ${data.x} | Y: ${data.y} | Z: ${data.z}`;
        }
        if (accModalCoords) accModalCoords.textContent = coordStr;
    };

    const openAccountDetailModal = (accId, fallbackData = null) => {
        if (!modalAccountDetail) return;
        if (fallbackData) {
            updateAccountDetailModalContent(fallbackData);
        } else if (state.lastStatusData) {
            updateAccountDetailModalContent(state.lastStatusData);
        }
        modalAccountDetail.classList.add('show');
    };

    if (btnOpenMyAcc) {
        btnOpenMyAcc.addEventListener('click', () => {
            playChime('click');
            vibratePhone(20);
            openAccountDetailModal(state.selectedAccId);
        });
    }

    if (btnCopyCoords && accModalCoords) {
        btnCopyCoords.addEventListener('click', async () => {
            playChime('click');
            vibratePhone(20);
            try {
                await navigator.clipboard.writeText(accModalCoords.textContent || '');
                showToast('Đã sao chép tọa độ vào bộ nhớ tạm!', '📋');
            } catch (err) {
                showToast(accModalCoords.textContent || 'Đã sao chép tọa độ', '📋');
            }
        });
    }

    if (btnCloseAccountModal && modalAccountDetail) {
        btnCloseAccountModal.addEventListener('click', () => {
            modalAccountDetail.classList.remove('show');
        });
        modalAccountDetail.addEventListener('click', (e) => {
            if (e.target === modalAccountDetail) {
                modalAccountDetail.classList.remove('show');
            }
        });
    }

    if (btnOpenDetailFromVitals) {
        btnOpenDetailFromVitals.addEventListener('click', () => {
            playChime('click');
            vibratePhone(20);
            openAccountDetailModal(state.selectedAccId);
        });
    }

    if (btnModalSelectPay && modalAccountDetail) {
        btnModalSelectPay.addEventListener('click', () => {
            playChime('click');
            vibratePhone(25);
            modalAccountDetail.classList.remove('show');
            switchTab('tab-pay');
            showToast(`Đã sẵn sàng lệnh pay từ acc: ${state.lastStatusData?.player?.name || 'Acc'}!`, '💸');
        });
    }

    if (btnModalSwitchChat && modalAccountDetail) {
        btnModalSwitchChat.addEventListener('click', () => {
            playChime('click');
            vibratePhone(25);
            modalAccountDetail.classList.remove('show');
            switchTab('tab-radar-chat');
            showToast(`Đang mở khung radar & chat`, '💬');
        });
    }

    // ----------------------------------------------------------------------
    // Live State Polling (From Minecraft via Python Flask Server)
    // ----------------------------------------------------------------------
    const pollStatus = async () => {
        try {
            const accParam = state.selectedAccId ? `?acc=${encodeURIComponent(state.selectedAccId)}` : '';
            const res = await fetch(getApiUrl(`/api/status${accParam}`), { cache: 'no-store' });
            const data = await res.json();

            // Cập nhật danh sách MultiMC & Dawn Accounts
            if (Array.isArray(data.accounts)) {
                state.accounts = data.accounts;
                const activeId = data.activeAccountId || (data.accounts.length > 0 ? data.accounts[0].id : null);
                if (!state.selectedAccId || !data.accounts.some(a => String(a.id) === String(state.selectedAccId))) {
                    state.selectedAccId = activeId;
                }
                renderAccountPills(data.accounts, state.selectedAccId);
            }

            // Cập nhật Link 4G từ Cloudflare Tunnel nếu có
            if (data.public_url) {
                current4gLink = data.public_url;
                if (remote4gUrl) {
                    remote4gUrl.textContent = data.public_url;
                }
            }

            state.connected = !!data.gameConnected;

            if (data.gameConnected && data.player) {
                state.lastStatusData = data;
                if (modalAccountDetail && modalAccountDetail.classList.contains('show')) {
                    updateAccountDetailModalContent(data);
                }

                // Online with Game
                if (liveIndicator) liveIndicator.classList.add('active');
                if (botStatusBadge) {
                    botStatusBadge.textContent = 'ONLINE';
                    botStatusBadge.classList.add('online');
                }

                if (botIgn) botIgn.textContent = data.player.name || 'Paladi Client';
                if (botServer) botServer.textContent = `🌐 Server: ${data.player.server || 'Đã kết nối'}`;

                // Balance
                if (botBalance) botBalance.textContent = data.player.balance || '$0';

                // Inventory
                if (data.inventory && botEmeralds) {
                    botEmeralds.textContent = `${data.inventory.emeralds || 0} Ngọc`;
                }

                // Vitals
                const hp = parseFloat(data.player.health) || 20;
                const maxHp = parseFloat(data.player.maxHealth) || 20;
                if (botHpVal) botHpVal.textContent = `${hp.toFixed(1)} / ${maxHp.toFixed(0)}`;
                if (botHpBar) botHpBar.style.width = `${Math.min(100, (hp / maxHp) * 100)}%`;

                const food = parseInt(data.player.food) || 20;
                if (botFoodVal) botFoodVal.textContent = `${food} / 20`;
                if (botFoodBar) botFoodBar.style.width = `${Math.min(100, (food / 20) * 100)}%`;

                // Coords
                if (botCoords) botCoords.textContent = `X: ${data.player.x || 0} | Y: ${data.player.y || 0} | Z: ${data.player.z || 0}`;

                // Entities (Radar)
                if (Array.isArray(data.nearbyEntities)) {
                    state.nearbyPlayers = data.nearbyEntities;
                    renderRadar();
                }

                // Online players list
                if (Array.isArray(data.onlinePlayers)) {
                    state.onlinePlayers = data.onlinePlayers;
                }

                if (senderIngameBadge) {
                    const isSenderInGame = !!(data.gameConnected && data.inGame !== false && data.player && data.player.name && data.player.server !== 'Offline (Chưa vào game)');
                    if (isSenderInGame) {
                        senderIngameBadge.textContent = '🟢 ĐANG TRONG SERVER (SẴN SÀNG PAY)';
                        senderIngameBadge.className = 'sender-ingame-badge online';
                    } else {
                        senderIngameBadge.textContent = '🔴 CHƯA VÀO GAME (OFFLINE)';
                        senderIngameBadge.className = 'sender-ingame-badge offline';
                    }
                }

                // Earnings & Rate per hour (/h)
                if (data.earnings) {
                    if (botRatePerHour) botRatePerHour.textContent = data.earnings.perHourFormatted || '$0/h';
                    if (botTotalEarned) botTotalEarned.textContent = `+${data.earnings.totalEarnedFormatted || '$0'}`;
                    if (botUptime) botUptime.textContent = data.earnings.uptimeFormatted || '00h 00m 00s';
                    if (botRateSubMoney) {
                        botRateSubMoney.textContent = `(${data.earnings.perHourFormatted ? '+' + data.earnings.perHourFormatted : '+$0/h'})`;
                    }
                    if (boxAxeSell && botAxeSell) {
                        if (data.earnings.axeSell && data.earnings.axeSell.hits > 0) {
                            boxAxeSell.style.display = 'flex';
                            botAxeSell.textContent = `+${data.earnings.axeSell.earnedFormatted || '$0'} (${data.earnings.axeSell.hits} lần)`;
                        } else {
                            boxAxeSell.style.display = 'none';
                        }
                    }
                }

                // Tiền Shard & Tốc độ Shard (/h)
                if (data.shards) {
                    if (botShards) botShards.textContent = data.shards.balanceFormatted || (data.shards.balance + ' ★');
                    if (botRateSubShards) botRateSubShards.textContent = `(${data.shards.perHourFormatted || '+0/h'})`;
                    if (botRatePerHourShards) botRatePerHourShards.textContent = data.shards.perHourFormatted || '+0/h';
                    if (botTotalEarnedShards) botTotalEarnedShards.textContent = `+${data.shards.totalEarnedFormatted || '0'} ★`;
                }

                // History
                if (Array.isArray(data.history) && data.history.length > 0 && state.history.length === 0) {
                    state.history = data.history;
                    renderHistory();
                }
            } else {
                // Offline fallback
                liveIndicator.classList.remove('active');
                botStatusBadge.textContent = 'CHỜ GAME...';
                botStatusBadge.classList.remove('online');
                botServer.textContent = '🌐 Đang chờ mở Minecraft Client...';

                if (senderIngameBadge) {
                    senderIngameBadge.textContent = '🔴 CHƯA VÀO GAME (OFFLINE)';
                    senderIngameBadge.className = 'sender-ingame-badge offline';
                }

                if (data.earnings) {
                    botRatePerHour.textContent = data.earnings.perHourFormatted || '$0/h';
                    botTotalEarned.textContent = `+${data.earnings.totalEarnedFormatted || '$0'}`;
                    botUptime.textContent = data.earnings.uptimeFormatted || '00h 00m 00s';
                    if (botRateSubMoney) botRateSubMoney.textContent = '(+$0/h)';
                }

                if (data.shards) {
                    if (botShards) botShards.textContent = data.shards.balanceFormatted || '0 ★';
                    if (botRateSubShards) botRateSubShards.textContent = '(+0/h)';
                    if (botRatePerHourShards) botRatePerHourShards.textContent = '+0/h';
                    if (botTotalEarnedShards) botTotalEarnedShards.textContent = '+0 ★';
                }
            }
        } catch (err) {
            liveIndicator.classList.remove('active');
            botStatusBadge.textContent = 'MẤT KẾT NỐI';
            botStatusBadge.classList.remove('online');
            botServer.textContent = '🌐 Không thể kết nối tới máy chủ PC';
        }
    };

    btnResetStats.addEventListener('click', async () => {
        playChime('click');
        vibratePhone(30);
        try {
            const resp = await fetch('/api/reset-stats', { method: 'POST' });
            const data = await resp.json();
            playChime('success');
            showToast('Đã đặt lại thống kê doanh thu /h!', '🔄');
            pollStatus();
        } catch (e) {
            showToast('Không thể đặt lại thống kê!', '⚠️', true);
        }
    });

    btnRefresh.addEventListener('click', () => {
        playChime('click');
        vibratePhone(20);
        showToast('Đang làm mới dữ liệu bot...', '🔄');
        pollStatus();
    });

    // ----------------------------------------------------------------------
    // Profit & Pricing Calculator (Eldorado 6.5% + Cổng rút + Bank VN)
    // ----------------------------------------------------------------------
    let calcTargetProfitPerM = 30; // Mặc định 30 đ/M
    let currentMinAmount = 10.0;

    const calcTargetDisplay = document.getElementById('calc-target-display');
    const targetPills = document.querySelectorAll('.btn-target-pill');
    const calcQty = document.getElementById('calc-qty');
    const calcCost = document.getElementById('calc-cost');
    const calcPlatformFee = document.getElementById('calc-platform-fee');
    const calcWithdrawFee = document.getElementById('calc-withdraw-fee');
    const calcFixedFee = document.getElementById('calc-fixed-fee');
    const calcBankFee = document.getElementById('calc-bank-fee');
    const calcRateVnd = document.getElementById('calc-rate-vnd');

    const methodCards = document.querySelectorAll('.btn-method-card');
    const methodActiveDisplay = document.getElementById('method-active-display');
    const minAmountWarning = document.getElementById('min-amount-warning');
    const minAmountVal = document.getElementById('min-amount-val');

    const resSellingUsd = document.getElementById('res-selling-usd');
    const resSellingVnd = document.getElementById('res-selling-vnd');
    const resUnitPriceVnd = document.getElementById('res-unit-price-vnd');
    const resUnitPriceUsd = document.getElementById('res-unit-price-usd');
    const resNetPayout = document.getElementById('res-net-payout');
    const resNetPayoutVnd = document.getElementById('res-net-payout-vnd');
    const resNetProfit = document.getElementById('res-net-profit');
    const resNetProfitVnd = document.getElementById('res-net-profit-vnd');
    const resProfitPerM = document.getElementById('res-profit-per-m');
    const resTotalFees = document.getElementById('res-total-fees');
    const resFeeDetail = document.getElementById('res-fee-detail');

    const barLabelPayout = document.getElementById('bar-label-payout');
    const barLabelFee = document.getElementById('bar-label-fee');
    const feeFillPayout = document.getElementById('fee-fill-payout');
    const feeFillFee = document.getElementById('fee-fill-fee');

    const tableProfitLabel = document.getElementById('table-profit-label');
    const pricingTableBody = document.getElementById('pricing-table-body');

    const computePricing = (qty, targetProfitPerM, costPerM, platPct, withPct, fixedFee, bankPct, rateVnd) => {
        // Tiền lời và vốn mong muốn thực nhận về tài khoản ngân hàng VN (VNĐ)
        const profitVnd = qty * targetProfitPerM;
        const costVnd = qty * costPerM;
        const targetVndInBank = profitVnd + costVnd;

        // Nếu rút từ ví về bank VN chịu phí bankPct (VD: 2% nếu bank wire, 0% nếu P2P)
        const bankMultiplier = 1.0 - (bankPct / 100.0);
        const requiredUsdFromWallet = (targetVndInBank / rateVnd) / Math.max(0.01, bankMultiplier);

        // Số USD cần có trong ví Eldorado trước khi rút về (trừ withPct % + fixedFee $)
        const withdrawMultiplier = 1.0 - (withPct / 100.0);
        const balanceInEldorado = (requiredUsdFromWallet + fixedFee) / Math.max(0.01, withdrawMultiplier);

        // Giá đăng bán trên sàn Eldorado trước khi trừ platPct (6.5% phí sàn)
        const platformMultiplier = 1.0 - (platPct / 100.0);
        const sellingPriceUsd = balanceInEldorado / Math.max(0.01, platformMultiplier);

        // Chi tiết các khoản phí
        const platformFeeUsd = sellingPriceUsd * (platPct / 100.0);
        const eldoradoBalance = sellingPriceUsd - platformFeeUsd;
        const walletWithdrawFeeUsd = (eldoradoBalance * (withPct / 100.0)) + fixedFee;
        const netInWalletUsd = eldoradoBalance - walletWithdrawFeeUsd;
        const bankFeeUsd = netInWalletUsd * (bankPct / 100.0);
        const finalNetUsd = netInWalletUsd - bankFeeUsd;
        const finalNetVnd = Math.round(finalNetUsd * rateVnd);
        const totalFeesUsd = platformFeeUsd + walletWithdrawFeeUsd + bankFeeUsd;

        const sellingPriceVnd = Math.round(sellingPriceUsd * rateVnd);
        const unitSellingVnd = (sellingPriceVnd / qty).toFixed(1);
        const unitSellingUsd = (sellingPriceUsd / qty).toFixed(5);

        return {
            sellingPriceUsd,
            sellingPriceVnd,
            unitSellingVnd,
            unitSellingUsd,
            netPayoutUsd: finalNetUsd,
            netPayoutVnd: finalNetVnd,
            netProfitUsd: (finalNetVnd - costVnd) / rateVnd,
            netProfitVnd: finalNetVnd - costVnd,
            totalFeesUsd,
            platformFeeUsd,
            walletWithdrawFeeUsd,
            bankFeeUsd,
            eldoradoBalance
        };
    };

    const updateCalculator = () => {
        const qty = Math.max(1, parseFloat(calcQty.value) || 1);
        const costPerM = Math.max(0, parseFloat(calcCost.value) || 0);
        const platPct = parseFloat(calcPlatformFee.value) || 6.5;
        const withPct = parseFloat(calcWithdrawFee.value) || 5.0;
        const fixedFee = parseFloat(calcFixedFee.value) || 1.0;
        const bankPct = parseFloat(calcBankFee ? calcBankFee.value : 0) || 0.0;
        const rateVnd = parseFloat(calcRateVnd.value) || 25500;

        calcTargetDisplay.textContent = `${calcTargetProfitPerM} đ / M`;
        tableProfitLabel.textContent = `${calcTargetProfitPerM}đ/M`;

        const res = computePricing(qty, calcTargetProfitPerM, costPerM, platPct, withPct, fixedFee, bankPct, rateVnd);

        resSellingUsd.textContent = `$${res.sellingPriceUsd.toFixed(2)}`;
        resSellingVnd.textContent = `(${res.sellingPriceVnd.toLocaleString('vi-VN')} đ)`;

        if (resUnitPriceVnd) resUnitPriceVnd.textContent = `${res.unitSellingVnd} đ / M`;
        if (resUnitPriceUsd) resUnitPriceUsd.textContent = `~$${res.unitSellingUsd} / M`;

        resNetPayout.textContent = `$${res.netPayoutUsd.toFixed(2)}`;
        resNetPayoutVnd.textContent = `~${res.netPayoutVnd.toLocaleString('vi-VN')} đ`;

        resNetProfit.textContent = `+$${res.netProfitUsd.toFixed(2)}`;
        resNetProfitVnd.textContent = `~${res.netProfitVnd.toLocaleString('vi-VN')} đ`;

        resProfitPerM.textContent = `+${calcTargetProfitPerM} đ / M`;

        resTotalFees.textContent = `-$${res.totalFeesUsd.toFixed(2)}`;
        resFeeDetail.textContent = `Sàn Eldo: $${res.platformFeeUsd.toFixed(2)} • Rút ví: $${res.walletWithdrawFeeUsd.toFixed(2)}${bankPct > 0 ? ` • Bank: $${res.bankFeeUsd.toFixed(2)}` : ''}`;

        // Kiểm tra mức rút tối thiểu
        if (minAmountWarning && minAmountVal) {
            if (res.eldoradoBalance < currentMinAmount) {
                minAmountWarning.style.display = 'block';
                minAmountVal.textContent = `$${currentMinAmount}.00`;
            } else {
                minAmountWarning.style.display = 'none';
            }
        }

        const payoutRatio = Math.max(0, Math.min(100, (res.netPayoutUsd / res.sellingPriceUsd) * 100));
        const feeRatio = 100 - payoutRatio;

        barLabelPayout.textContent = `Về Bank: ${payoutRatio.toFixed(1)}%`;
        barLabelFee.textContent = `Tổng phí: ${feeRatio.toFixed(1)}%`;
        feeFillPayout.style.width = `${payoutRatio}%`;
        feeFillFee.style.width = `${feeRatio}%`;

        // Render cheat-sheet table for common package sizes (500M to 10,000M)
        const packages = [500, 1000, 2000, 3000, 5000, 10000];
        pricingTableBody.innerHTML = packages.map(pkg => {
            const pRes = computePricing(pkg, calcTargetProfitPerM, costPerM, platPct, withPct, fixedFee, bankPct, rateVnd);
            const pkgLabel = pkg >= 1000 ? `${pkg / 1000}B (${pkg}M)` : `${pkg}M`;
            return `
                <tr>
                    <td class="col-pkg">${pkgLabel}</td>
                    <td class="col-sell"><strong>$${pRes.sellingPriceUsd.toFixed(2)}</strong><br><small style="color:var(--text-muted)">(${pRes.unitSellingVnd}đ/M)</small></td>
                    <td class="col-net">$${pRes.netPayoutUsd.toFixed(2)}</td>
                    <td class="col-profit">+${pRes.netProfitVnd.toLocaleString('vi-VN')} đ</td>
                    <td>
                        <button type="button" class="btn-apply-pkg" data-amt="${pkg}M">Áp dụng</button>
                    </td>
                </tr>
            `;
        }).join('');

        pricingTableBody.querySelectorAll('.btn-apply-pkg').forEach(btn => {
            btn.addEventListener('click', () => {
                const amt = btn.dataset.amt;
                inputAmount.value = amt;
                updateCommandPreview();
                playChime('success');
                vibratePhone([30, 40]);
                showToast(`Đã chọn gói ${amt} để chuyển tiền!`, '💰');
                switchTab('tab-pay');
            });
        });
    };

    // Chọn phương thức rút tiền Eldorado (Skrill, Payoneer, SEPA, USDC)
    methodCards.forEach(card => {
        card.addEventListener('click', () => {
            methodCards.forEach(c => c.classList.remove('active'));
            card.classList.add('active');

            const mName = card.querySelector('.method-name')?.textContent || 'Skrill';
            const pct = parseFloat(card.dataset.pct) || 5.0;
            const flat = parseFloat(card.dataset.flat) || 1.0;
            currentMinAmount = parseFloat(card.dataset.min) || 10.0;

            calcWithdrawFee.value = pct;
            calcFixedFee.value = flat;

            if (methodActiveDisplay) {
                methodActiveDisplay.textContent = `${card.querySelector('.method-icon')?.textContent || ''} ${mName} (${pct}% + $${flat})`;
            }

            playChime('click');
            vibratePhone(25);
            showToast(`Đã chọn cổng rút: ${mName} (${pct}% + $${flat})`, '💳');
            updateCalculator();
        });
    });

    targetPills.forEach(pill => {
        pill.addEventListener('click', () => {
            targetPills.forEach(p => p.classList.remove('active'));
            pill.classList.add('active');
            calcTargetProfitPerM = parseFloat(pill.dataset.profit) || 30;
            playChime('click');
            vibratePhone(25);
            updateCalculator();
        });
    });

    [calcQty, calcCost, calcPlatformFee, calcWithdrawFee, calcFixedFee, calcBankFee, calcRateVnd].forEach(el => {
        if (el) el.addEventListener('input', updateCalculator);
    });

    // ----------------------------------------------------------------------
    // Eldorado & MT Store Payout Tracker Integration
    // ----------------------------------------------------------------------
    let eldoradoOrdersData = [];
    let eldoradoFilterQuery = '';

    const formatCountdown = (seconds) => {
        if (seconds <= 0) return 'TIỀN ĐÃ VỀ VÍ!';
        const d = Math.floor(seconds / 86400);
        const h = Math.floor((seconds % 86400) / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = Math.floor(seconds % 60);

        if (d > 0) return `còn ${d} ngày ${h} giờ`;
        if (h > 0) return `còn ${h} giờ ${m} phút`;
        if (m > 0) return `còn ${m} phút ${s}s`;
        return `còn ${s} giây`;
    };

    const renderEldoradoPayouts = () => {
        const container = document.getElementById('eldorado-orders-container');
        if (!container) return;

        if (!eldoradoOrdersData || eldoradoOrdersData.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <span class="empty-icon">💰</span>
                    <p>Hiện không có đơn hàng nào đang chờ nhận tiền.</p>
                    <small>Bấm "Cấu hình MT Store" để kiểm tra kết nối với shop của bạn.</small>
                </div>
            `;
            return;
        }

        const query = (eldoradoFilterQuery || '').toLowerCase().trim();
        const filtered = eldoradoOrdersData.filter(item => {
            if (!query) return true;
            return (
                (item.orderId && item.orderId.toLowerCase().includes(query)) ||
                (item.buyer && item.buyer.toLowerCase().includes(query)) ||
                (item.product && item.product.toLowerCase().includes(query)) ||
                (item.amount && String(item.amount).includes(query))
            );
        });

        if (filtered.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <span class="empty-icon">🔍</span>
                    <p>Không tìm thấy đơn hàng nào khớp với từ khóa "${query}".</p>
                </div>
            `;
            return;
        }

        container.innerHTML = filtered.map(item => {
            const isOverdue = !!item.isOverdue;
            const remainingSec = item.remainingSeconds !== undefined ? item.remainingSeconds : 0;
            const isImminent = !isOverdue && remainingSec <= 3600;

            let countdownClass = 'payout-countdown-pill';
            let countdownText = item.countdownText || formatCountdown(remainingSec);
            if (isOverdue) {
                countdownClass += ' done';
                countdownText = '✅ ĐÃ MỞ KHÓA VỀ VÍ';
            } else if (isImminent) {
                countdownClass += ' imminent';
            }

            const formattedAmount = typeof item.amount === 'number' ? item.amount.toFixed(2) : item.amount;
            const cardClass = isOverdue ? 'payout-card is-overdue' : 'payout-card';

            return `
                <div class="${cardClass}">
                    <div class="payout-header">
                        <div class="payout-order-info">
                            <span class="payout-order-id">
                                📦 ${item.orderId || 'Đơn hàng'}
                            </span>
                            <span class="payout-buyer">
                                Khách: <strong>${item.buyer || 'Ẩn danh'}</strong>
                            </span>
                        </div>
                        <div class="payout-amount-box">
                            <span class="payout-amount">+${formattedAmount} USD</span>
                            <span class="payout-product-tag">${item.product || 'In-game Money'}</span>
                        </div>
                    </div>
                    <div class="payout-details-row">
                        <div class="payout-time-info">
                            <span>Giờ ước tính: <span class="time-highlight">${item.payoutDateFormatted || '—'}</span></span>
                            <span>Trạng thái: <b style="color: ${item.basis === 'received' ? '#a78bfa' : '#38bdf8'}">${item.basisText || 'Received +5 ngày'}</b></span>
                        </div>
                        <div>
                            <span class="${countdownClass}">
                                ⏱️ ${countdownText}
                            </span>
                        </div>
                    </div>
                </div>
            `;
        }).join('');
    };

    const fetchEldoradoPayouts = async (silent = false) => {
        const syncStatus = document.getElementById('eldorado-sync-status');
        const syncSub = document.getElementById('eldorado-sync-sub');
        const statTotalOrders = document.getElementById('el-stat-total-orders');
        const statTotalAmount = document.getElementById('el-stat-total-amount');
        const statOverdueCount = document.getElementById('el-stat-overdue-count');
        const statOverdueSub = document.getElementById('el-stat-overdue-sub');
        const statNextTime = document.getElementById('el-stat-next-time');
        const statNextCountdown = document.getElementById('el-stat-next-countdown');

        try {
            if (!silent && syncStatus) {
                syncStatus.textContent = '⏳ ĐANG ĐỒNG BỘ...';
                syncStatus.className = 'eldorado-sync-pill';
            }

            const res = await fetch(getApiUrl('/api/eldorado/payouts'), { cache: 'no-store' });
            const data = await res.json();

            if (data.ok) {
                eldoradoOrdersData = data.payouts || [];

                if (statTotalOrders) statTotalOrders.textContent = data.totalOrders || 0;
                if (statTotalAmount) statTotalAmount.textContent = `${(data.totalAmount || 0).toFixed(2)} USD`;
                if (statOverdueCount) statOverdueCount.textContent = data.overdueCount || 0;
                if (statOverdueSub) {
                    statOverdueSub.textContent = data.overdueCount > 0 ? `Đã có ${(data.overdueAmount || 0).toFixed(2)} USD về ví!` : 'Chờ mở khóa';
                }

                if (statNextTime) statNextTime.textContent = data.nextPayoutTime || '—';
                if (statNextCountdown) statNextCountdown.textContent = data.nextCountdownText || '—';

                if (syncStatus) {
                    syncStatus.textContent = `🟢 ĐÃ KẾT NỐI (${data.totalOrders} ĐƠN)`;
                    syncStatus.className = 'eldorado-sync-pill';
                }
                if (syncSub) {
                    syncSub.textContent = `Đồng bộ lúc ${new Date().toLocaleTimeString('vi-VN')} • Tự động báo khi có tiền`;
                }

                renderEldoradoPayouts();
            } else {
                if (syncStatus) {
                    syncStatus.textContent = '⚠️ CHƯA CẤU HÌNH';
                    syncStatus.className = 'eldorado-sync-pill warning';
                }
                if (syncSub) {
                    syncSub.textContent = data.error || 'Vui lòng bấm "Cấu hình MT Store" để nhập shop credentials';
                }
            }
        } catch (err) {
            if (syncStatus) {
                syncStatus.textContent = '🔴 MẤT KẾT NỐI';
                syncStatus.className = 'eldorado-sync-pill error';
            }
            if (syncSub) {
                syncSub.textContent = 'Không kết nối được với server local. Đang thử lại...';
            }
        }
    };

    const pollEldoradoEvents = async () => {
        try {
            const res = await fetch(getApiUrl('/api/eldorado/events'), { cache: 'no-store' });
            const data = await res.json();
            if (data.ok && Array.isArray(data.events) && data.events.length > 0) {
                data.events.forEach(evt => {
                    // Trigger sound & vibration on phone
                    playChime('payout');
                    vibratePhone([150, 80, 150, 80, 300]);

                    const msg = `TIỀN ĐÃ VỀ VÍ! Đơn ${evt.orderId} (+${evt.amount} USD)`;
                    showToast(msg, '💰');

                    // Trigger browser push notification if permitted
                    if (window.Notification && Notification.permission === 'granted') {
                        try {
                            new Notification('💰 TIỀN ĐÃ VỀ VÍ ELDORADO!', {
                                body: `Đơn ${evt.orderId} (${evt.amount} USD) từ khách ${evt.buyer || ''} đã mở khóa về ví của bạn!`,
                                icon: '/icon-192.png',
                                badge: '/icon-192.png'
                            });
                        } catch (e) {}
                    }
                });

                // Auto refresh payouts data
                fetchEldoradoPayouts(true);
            }
        } catch (e) {}
    };

    const initEldorado = () => {
        const btnRefresh = document.getElementById('btn-eldorado-refresh');
        const btnTestSound = document.getElementById('btn-eldorado-test-sound');
        const btnOpenCfg = document.getElementById('btn-eldorado-open-config');
        const modalCfg = document.getElementById('modal-eldorado-config');
        const btnCloseCfg = document.getElementById('btn-close-eldorado-modal');
        const btnSaveCfg = document.getElementById('btn-save-eldorado-cfg');
        const searchInput = document.getElementById('eldorado-search');
        const navBtnEldorado = document.getElementById('nav-btn-eldorado');

        // Request notification permission on first interaction
        const reqNotifyPerm = () => {
            if (window.Notification && Notification.permission === 'default') {
                Notification.requestPermission().catch(() => {});
            }
        };

        if (navBtnEldorado) {
            navBtnEldorado.addEventListener('click', () => {
                reqNotifyPerm();
                fetchEldoradoPayouts();
            });
        }

        if (btnRefresh) {
            btnRefresh.addEventListener('click', () => {
                playChime('click');
                vibratePhone(20);
                showToast('Đang làm mới dữ liệu Eldorado...', '🔄');
                fetchEldoradoPayouts();
            });
        }

        if (btnTestSound) {
            btnTestSound.addEventListener('click', async () => {
                reqNotifyPerm();
                playChime('payout');
                vibratePhone([150, 80, 150, 80, 300]);
                showToast('Đang test chuông báo tiền về...', '🔔');

                try {
                    await fetch(getApiUrl('/api/eldorado/test-notify'), { method: 'POST' });
                } catch (e) {}
            });
        }

        if (btnOpenCfg && modalCfg) {
            btnOpenCfg.addEventListener('click', async () => {
                playChime('click');
                vibratePhone(20);
                modalCfg.style.display = 'flex';

                // Load existing settings
                try {
                    const res = await fetch(getApiUrl('/api/eldorado/settings'), { cache: 'no-store' });
                    const cfg = await res.json();
                    if (cfg.store_url) document.getElementById('cfg-eldorado-store-url').value = cfg.store_url;
                    if (cfg.x_shop_creds) document.getElementById('cfg-eldorado-creds').value = cfg.x_shop_creds;
                    if (cfg.sound_alert !== undefined) document.getElementById('cfg-eldorado-sound').checked = !!cfg.sound_alert;
                    if (cfg.desktop_toast !== undefined) document.getElementById('cfg-eldorado-toast').checked = !!cfg.desktop_toast;
                } catch (e) {}
            });
        }

        if (btnCloseCfg && modalCfg) {
            btnCloseCfg.addEventListener('click', () => {
                modalCfg.style.display = 'none';
            });
            modalCfg.addEventListener('click', (e) => {
                if (e.target === modalCfg) modalCfg.style.display = 'none';
            });
        }

        if (btnSaveCfg && modalCfg) {
            btnSaveCfg.addEventListener('click', async () => {
                playChime('success');
                vibratePhone([30, 40]);
                const storeUrl = (document.getElementById('cfg-eldorado-store-url')?.value || '').trim();
                const creds = (document.getElementById('cfg-eldorado-creds')?.value || '').trim();
                const sound = !!document.getElementById('cfg-eldorado-sound')?.checked;
                const toast = !!document.getElementById('cfg-eldorado-toast')?.checked;

                try {
                    showToast('Đang lưu và kết nối với shop...', '⏳');
                    const res = await fetch(getApiUrl('/api/eldorado/settings'), {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            store_url: storeUrl,
                            x_shop_creds: creds,
                            sound_alert: sound,
                            desktop_toast: toast
                        })
                    });
                    const d = await res.json();
                    if (d.ok) {
                        showToast('Đã lưu cấu hình MT Store thành công!', '✅');
                        modalCfg.style.display = 'none';
                        fetchEldoradoPayouts();
                    } else {
                        showToast(d.error || 'Lỗi khi lưu cấu hình', '❌', true);
                    }
                } catch (err) {
                    showToast('Lỗi kết nối máy chủ', '❌', true);
                }
            });
        }

        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                eldoradoFilterQuery = e.target.value || '';
                renderEldoradoPayouts();
            });
        }

        // Live countdown decrement tick every second
        setInterval(() => {
            if (!eldoradoOrdersData || eldoradoOrdersData.length === 0) return;
            let changed = false;
            eldoradoOrdersData.forEach(item => {
                if (item.remainingSeconds > 0) {
                    item.remainingSeconds -= 1;
                    if (item.remainingSeconds <= 0) {
                        item.isOverdue = true;
                        changed = true;
                    }
                }
            });
            // Update countdown labels in DOM without replacing full cards
            document.querySelectorAll('.payout-card').forEach((card, idx) => {
                const item = eldoradoOrdersData[idx];
                if (!item) return;
                const pill = card.querySelector('.payout-countdown-pill');
                if (pill) {
                    if (item.isOverdue) {
                        pill.className = 'payout-countdown-pill done';
                        pill.textContent = '✅ ĐÃ MỞ KHÓA VỀ VÍ';
                        card.classList.add('is-overdue');
                    } else {
                        pill.textContent = `⏱️ ${formatCountdown(item.remainingSeconds)}`;
                    }
                }
            });
        }, 1000);

        // Fetch initially and poll events every 6 seconds
        fetchEldoradoPayouts(true);
        setInterval(() => fetchEldoradoPayouts(true), 60000);
        setInterval(pollEldoradoEvents, 6000);
    };

    // Initialize Eldorado & Payout alerts
    initEldorado();

    // Initialize calculator
    updateCalculator();

    // Start Real-time loop
    pollStatus();
    setInterval(pollStatus, 800);
});
