document.addEventListener('DOMContentLoaded', () => {
    // ──────────────────────────────────────────────
    //  State
    // ──────────────────────────────────────────────
    let isRunning = false;
    let autoTradeInterval = null;
    let statEmeralds = 1420;
    let statBerries = 18432;
    let statOrders = 14;

    // Elements
    const navItems = document.querySelectorAll('.nav-item');
    const tabContents = document.querySelectorAll('.tab-content');
    const btnToggleMain = document.getElementById('btn-toggle-main');
    const powerIcon = document.getElementById('power-icon');
    const powerLabel = document.getElementById('power-label');
    const globalStatusDot = document.getElementById('global-status-dot');
    const globalStatusText = document.getElementById('global-status-text');

    const pageTitle = document.getElementById('page-title');
    const pageSubtitle = document.getElementById('page-subtitle');

    const statEmeraldsEl = document.getElementById('stat-emeralds');
    const statBerriesEl = document.getElementById('stat-berries');
    const statOrdersEl = document.getElementById('stat-orders');
    const terminalLog = document.getElementById('terminal-log');
    const btnClearLog = document.getElementById('btn-clear-log');

    // Controls
    const selectItem = document.getElementById('select-item');
    const tradeDelay = document.getElementById('trade-delay');
    const tradeDelayVal = document.getElementById('trade-delay-val');
    const villagerRange = document.getElementById('villager-range');
    const villagerRangeVal = document.getElementById('villager-range-val');

    const priceIncrement = document.getElementById('price-increment');
    const maxPrice = document.getElementById('max-price');
    const berryOverride = document.getElementById('berry-override');
    const berryPrice = document.getElementById('berry-price');
    const orderAmount = document.getElementById('order-amount');

    const testMarketPrice = document.getElementById('test-market-price');
    const calcOutputPrice = document.getElementById('calc-output-price');
    const calcStatusBadge = document.getElementById('calc-status-badge');

    const templateInput = document.getElementById('template-input');
    const previewCommand = document.getElementById('preview-command');
    const presetButtons = document.querySelectorAll('.btn-preset');

    // Simulation
    const btnSimNormal = document.getElementById('btn-sim-normal');
    const btnSimBerry = document.getElementById('btn-sim-berry');
    const btnSimKill = document.getElementById('btn-sim-kill');
    const simBubble = document.getElementById('sim-bubble');
    const simEventsList = document.getElementById('sim-events-list');

    // ──────────────────────────────────────────────
    //  Tab Navigation
    // ──────────────────────────────────────────────
    const tabHeaders = {
        dashboard: {
            title: 'Bảng Điều Khiển Tự Động Hóa',
            subtitle: 'Quản lý chu trình Dân Làng ⟷ Your Orders ⟷ Đặt Mua Giá Tối Ưu'
        },
        villager: {
            title: 'Thiết Lập Giao Dịch Dân Làng',
            subtitle: 'Cấu hình vật phẩm target, khoảng cách quét và tốc độ trade'
        },
        pricing: {
            title: 'Chiến Lược Định Giá & Thị Trường',
            subtitle: 'Quy tắc outbid +1 đồng, cố định Sweet Berry 25.01 và ngắt khẩn cấp > 100đ'
        },
        templates: {
            title: 'Tùy Biến Mẫu Lệnh Đặt Order',
            subtitle: 'Tương thích mọi server Minecraft SMP (DonutSMP, Paladi SMP)'
        },
        simulator: {
            title: 'Mô Phỏng Kịch Bản Thực Tế',
            subtitle: 'Kiểm tra quy trình xử lý thông minh trước khi chạy trong game'
        }
    };

    navItems.forEach(item => {
        item.addEventListener('click', () => {
            const targetTab = item.getAttribute('data-tab');

            navItems.forEach(n => n.classList.remove('active'));
            tabContents.forEach(t => t.classList.remove('active'));

            item.classList.add('active');
            const targetEl = document.getElementById(`tab-${targetTab}`);
            if (targetEl) targetEl.classList.add('active');

            if (tabHeaders[targetTab]) {
                pageTitle.textContent = tabHeaders[targetTab].title;
                pageSubtitle.textContent = tabHeaders[targetTab].subtitle;
            }
        });
    });

    // ──────────────────────────────────────────────
    //  Main Power Toggle
    // ──────────────────────────────────────────────
    btnToggleMain.addEventListener('click', () => {
        isRunning = !isRunning;
        updatePowerState();
    });

    function updatePowerState() {
        if (isRunning) {
            btnToggleMain.classList.add('running');
            powerIcon.textContent = '⏹';
            powerLabel.textContent = 'DỪNG AUTO APP';
            globalStatusDot.classList.add('active');
            globalStatusText.textContent = 'ĐANG CHẠY (RUNNING)';
            globalStatusText.style.color = '#34d399';
            addLog('info', '[AutoVillagerOrder] Đã BẬT hệ thống tự động.');
            startTradeSimulation();
        } else {
            btnToggleMain.classList.remove('running');
            powerIcon.textContent = '▶';
            powerLabel.textContent = 'KÍCH HOẠT AUTO';
            globalStatusDot.classList.remove('active');
            globalStatusText.textContent = 'ĐÃ DỪNG (STOPPED)';
            globalStatusText.style.color = '#f87171';
            addLog('warn', '[AutoVillagerOrder] Đã TẮT module.');
            stopTradeSimulation();
        }
    }

    function startTradeSimulation() {
        if (autoTradeInterval) clearInterval(autoTradeInterval);
        autoTradeInterval = setInterval(() => {
            statEmeralds += Math.floor(Math.random() * 4) + 1;
            statBerries += Math.floor(Math.random() * 24) + 12;
            statEmeraldsEl.textContent = statEmeralds.toLocaleString();
            statBerriesEl.textContent = statBerries.toLocaleString();
        }, 3000);
    }

    function stopTradeSimulation() {
        if (autoTradeInterval) {
            clearInterval(autoTradeInterval);
            autoTradeInterval = null;
        }
    }

    // ──────────────────────────────────────────────
    //  Log Helpers
    // ──────────────────────────────────────────────
    function addLog(type, message) {
        const entry = document.createElement('div');
        entry.className = `log-entry ${type}`;
        const now = new Date();
        const timeStr = `[${now.toTimeString().split(' ')[0]}]`;
        entry.innerHTML = `<span class="time">${timeStr}</span> ${escapeHtml(message)}`;
        terminalLog.appendChild(entry);
        terminalLog.scrollTop = terminalLog.scrollHeight;
    }

    btnClearLog.addEventListener('click', () => {
        terminalLog.innerHTML = '';
        addLog('info', '[System] Đã dọn sạch nhật ký.');
    });

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // ──────────────────────────────────────────────
    //  Sliders & Inputs
    // ──────────────────────────────────────────────
    tradeDelay.addEventListener('input', (e) => {
        const val = e.target.value;
        tradeDelayVal.textContent = `${val} ticks (${(val * 0.05).toFixed(2)}s)`;
    });

    villagerRange.addEventListener('input', (e) => {
        const val = e.target.value;
        villagerRangeVal.textContent = `${val} Blocks`;
    });

    // ──────────────────────────────────────────────
    //  Pricing Calculator
    // ──────────────────────────────────────────────
    function calculatePrice() {
        const marketVal = parseFloat(testMarketPrice.value) || 0;
        const currentItem = selectItem.value;
        const isBerry = currentItem === 'sweet_berries';
        const isBerryOverride = berryOverride.checked;
        const maxP = parseFloat(maxPrice.value) || 100;
        const inc = parseFloat(priceIncrement.value) || 1.0;

        let finalP;
        if (isBerry && isBerryOverride) {
            finalP = parseFloat(berryPrice.value) || 25.01;
        } else {
            finalP = marketVal > 0 ? (marketVal + inc) : 1.0;
        }

        calcOutputPrice.textContent = `${finalP.toFixed(2)} đồng`;

        if (finalP > maxP) {
            calcStatusBadge.className = 'calc-status danger';
            calcStatusBadge.textContent = `🛑 VƯỢT MỨC TRẦN (${maxP}đ) -> TỰ TẮT APP`;
            calcOutputPrice.style.color = '#f87171';
        } else {
            calcStatusBadge.className = 'calc-status safe';
            calcStatusBadge.textContent = `✅ An toàn (≤ ${maxP}đ)`;
            calcOutputPrice.style.color = '#34d399';
        }

        updatePreviewCommand(finalP);
    }

    testMarketPrice.addEventListener('input', calculatePrice);
    priceIncrement.addEventListener('input', calculatePrice);
    maxPrice.addEventListener('input', calculatePrice);
    berryPrice.addEventListener('input', calculatePrice);
    berryOverride.addEventListener('change', calculatePrice);
    selectItem.addEventListener('change', calculatePrice);

    // ──────────────────────────────────────────────
    //  Template & Command Preview
    // ──────────────────────────────────────────────
    function updatePreviewCommand(priceOverride = null) {
        const tpl = templateInput.value.trim();
        const item = selectItem.value;
        const amt = orderAmount.value || '2304';
        
        let p;
        if (priceOverride !== null) {
            p = priceOverride.toFixed(2);
        } else {
            const isBerry = item === 'sweet_berries';
            if (isBerry && berryOverride.checked) {
                p = parseFloat(berryPrice.value).toFixed(2);
            } else {
                p = (parseFloat(testMarketPrice.value) + parseFloat(priceIncrement.value)).toFixed(2);
            }
        }

        const cmd = tpl
            .replace('{item}', item)
            .replace('{amount}', amt)
            .replace('{price}', p);

        previewCommand.textContent = cmd;
    }

    templateInput.addEventListener('input', () => updatePreviewCommand());
    orderAmount.addEventListener('input', () => updatePreviewCommand());

    presetButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            presetButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            templateInput.value = btn.getAttribute('data-cmd');
            updatePreviewCommand();
        });
    });

    // ──────────────────────────────────────────────
    //  Simulation Scenarios
    // ──────────────────────────────────────────────
    btnSimNormal.addEventListener('click', () => {
        runSimulation([
            { time: 0, text: '👨‍🌾 [Villager] Đang trade lúa mì/cà rốt với Dân làng...', bubble: 'Trade thêm 5 lần nữa nhé!', type: '' },
            { time: 1000, text: '⚠️ [Inventory] Hết đồ trong túi! Đóng GUI Dân làng, gửi lệnh /order...', bubble: 'Ủa bạn hết đồ rồi à?', type: 'warn' },
            { time: 2000, text: '📦 [YourOrders] Mở Your Orders -> Không thấy còn đơn hàng sẵn.', bubble: 'Đang mở thị trường để kiểm tra giá...', type: '' },
            { time: 3000, text: '📊 [Market] Đọc thấy người thứ nhất (Top 1) đặt giá 73.00đ.', bubble: 'Người top 1 mua 73đ!', type: '' },
            { time: 4000, text: '💎 [Pricing] Tự động tính giá: 73.00 + 1.00 = 74.00 đồng (≤ 100đ).', bubble: 'Sẽ đặt 74.00đ để chiếm top 1!', type: '' },
            { time: 5000, text: '🚀 [Command] Đã gửi lệnh: /order create carrot 2304 74.00', bubble: 'Đã đặt order thành công!', type: '' },
            { time: 6000, text: '✅ [Success] Chuyển sang chế độ chờ nhận hàng từ thị trường.', bubble: 'Đang chờ hàng giao về Your Orders...', type: '' }
        ]);
    });

    btnSimBerry.addEventListener('click', () => {
        runSimulation([
            { time: 0, text: '🍓 [Villager] Đang trade Sweet Berries đổi Emerald...', bubble: 'Sweet Berry thơm ngon quá!', type: '' },
            { time: 1000, text: '⚠️ [Inventory] Hết Sweet Berries! Mở /order -> Your Orders.', bubble: 'Hết quả mọng rồi...', type: 'warn' },
            { time: 2000, text: '📦 [YourOrders] Không có Sweet Berry trong Your Orders.', bubble: 'Kiểm tra quy tắc Sweet Berry...', type: '' },
            { time: 3000, text: '🍓 [Sweet Berry Rule] Phát hiện Sweet Berry -> Áp dụng giá cố định: 25.01 đồng.', bubble: 'Auto set giá 25.01đ theo yêu cầu!', type: '' },
            { time: 4000, text: '🚀 [Command] Đã gửi lệnh: /order create sweet_berries 2304 25.01', bubble: 'Đã đặt order 25.01đ!', type: '' },
            { time: 5000, text: '✅ [Success] Đơn hàng 25.01đ đã lên sàn thị trường.', bubble: 'Chờ giao hàng và quay lại trade!', type: '' }
        ]);
    });

    btnSimKill.addEventListener('click', () => {
        runSimulation([
            { time: 0, text: '⚠️ [Inventory] Hết đồ trong túi! Mở /order -> Thị trường.', bubble: 'Đang kiểm tra giá thị trường...', type: 'warn' },
            { time: 1000, text: '📊 [Market] Người thứ nhất đặt giá: 105.00 đồng.', bubble: 'Ôi giá thị trường đang là 105đ!', type: 'warn' },
            { time: 2000, text: '🛑 [CIRCUIT BREAKER] Giá 106.00đ > 100.00đ! TỰ ĐỘNG TẮT APP ĐỂ BẢO VỆ TIỀN!', bubble: 'BÁO ĐỘNG: Giá > 100đ! Tự động ngắt module khẩn cấp!', type: 'danger' },
            { time: 3000, text: '⛔ [Module] AutoVillagerOrder đã tự động STOPPED an toàn.', bubble: 'Module đã tắt. Tiền của bạn được bảo vệ 100%.', type: 'danger' }
        ]);
    });

    function runSimulation(steps) {
        simEventsList.innerHTML = '';
        steps.forEach(step => {
            setTimeout(() => {
                const item = document.createElement('div');
                item.className = `sim-event-item ${step.type}`;
                item.textContent = step.text;
                simEventsList.appendChild(item);
                simBubble.textContent = `"${step.bubble}"`;
                addLog(step.type === 'danger' ? 'error' : (step.type === 'warn' ? 'warn' : 'info'), step.text);
            }, step.time);
        });
    }

    // Initial calculation
    calculatePrice();
});
