/**
 * 淘票票客户端 - 订单确认页面逻辑
 * 文件：client-order-confirm.js
 * 页面：order-confirm.html
 */

// 全局变量
let sessionId = null;
let eventId = null;
let sessionData = null;
let selectedSeats = [];
let totalPrice = 0;
let lockId = null;
let orderNo = null;   // 订单号
let payUrl = null;
let paymentStatus = 'NOT_READY';
let orderStatus = 'PROCESSING';
let pollTimer = null;
let orderExpireTime = null;
let nextPollMs = 1200;
const MIN_POLL_INTERVAL_MS = 800;
const MAX_POLL_INTERVAL_MS = 5000;
const DEFAULT_POLL_INTERVAL_MS = 1200;
const POLL_MAX_WINDOW_MS = 120000;

/**
 * 页面初始化
 */
document.addEventListener('DOMContentLoaded', async () => {
    console.log('=== 订单确认页面初始化 ===');

    // 更新用户信息显示
    updateUserInfo();

    // 【调试】打印当前 URL 和参数
    const currentUrl = window.location.href;
    const queryString = window.location.search;

    // 从 URL 参数获取 orderNo（新逻辑）
    const urlParams = new URLSearchParams(window.location.search);
    const urlOrderNo = urlParams.get('orderNo');

    // 从 sessionStorage 获取选座信息
    const storedSessionId = sessionStorage.getItem('sessionId');
    const storedSeats = sessionStorage.getItem('selectedSeats');
    const storedEventId = sessionStorage.getItem('eventId');
    const storedSessionData = sessionStorage.getItem('sessionData');
    const storedTotalPrice = sessionStorage.getItem('totalPrice');
    const storedLockId = sessionStorage.getItem('lockId');
    const storedOrderNo = sessionStorage.getItem('orderNo');
    const storedPayUrl = sessionStorage.getItem('payUrl');    // 【新增】支付URL
    const storedOrderExpireTime = sessionStorage.getItem('orderExpireTime');
    const storedNextPollMs = Number(sessionStorage.getItem('nextPollMs') || DEFAULT_POLL_INTERVAL_MS);

    if (!storedSessionId || !storedSeats) {
        showError('订单信息已过期，请重新选择座位');
        setTimeout(() => {
            window.location.href = 'index.html';
        }, 3000);
        return;
    }

    // 恢复数据
    sessionId = storedSessionId;
    eventId = storedEventId;
    selectedSeats = JSON.parse(storedSeats);
    totalPrice = parseFloat(storedTotalPrice) || 0;
    lockId = storedLockId;
    payUrl = storedPayUrl;
    paymentStatus = sessionStorage.getItem('paymentStatus') || 'NOT_READY';
    orderStatus = sessionStorage.getItem('orderStatus') || 'PROCESSING';
    orderExpireTime = storedOrderExpireTime;
    nextPollMs = normalizePollMs(storedNextPollMs);

    orderNo = urlOrderNo || storedOrderNo;

    if (!orderNo) {
        console.error('【错误】订单号缺失！');
        showError('订单号缺失，请重新选择座位');
        setTimeout(() => {
            window.location.href = 'seat-selection.html?sessionId=' + sessionId;
        }, 3000);
        return;
    }

    if (storedSessionData) {
        try {
            sessionData = JSON.parse(storedSessionData);
        } catch (e) {
            console.error('解析场次数据失败:', e);
        }
    }

    console.log('支付页面数据恢复:', { orderNo, sessionId, eventId, selectedSeats, totalPrice });

    // 更新页面信息
    updateOrderInfo();
    setPaymentHint('正在准备支付信息...');
    setPayButtonState(false, '订单准备中...');

    // 绑定支付按钮事件
    const payBtn = document.getElementById('payBtn');
    if (payBtn) {
        payBtn.addEventListener('click', handlePayment);
    }

    startCountdown(resolveCountdownSeconds(orderExpireTime));
    await waitForPaymentReady();
});

/**
 * 更新用户信息显示
 */
function updateUserInfo() {
    const userInfoDiv = document.querySelector('.header-actions .user-info, .header-actions > div:last-child');
    if (!userInfoDiv) return;

    const user = getCurrentUser();
    if (user) {
        userInfoDiv.innerHTML = `
            <div style="width: 32px; height: 32px; border-radius: 50%; background: #e3f2fd; display: flex; align-items: center; justify-content: center;">
                ${user.nickname ? user.nickname.charAt(0) : '用'}
            </div>
            <span>${user.nickname || '用户'}</span>
        `;
    } else {
        userInfoDiv.innerHTML = `
            <a href="login.html" class="btn btn-outline btn-small">登录</a>
        `;
    }
}

/**
 * 更新订单信息显示
 */
function updateOrderInfo() {
    // 更新页面标题
    document.title = `确认订单 - ${sessionData?.eventName || '演出'} - 淘票票`;

    // 更新订单摘要
    updateOrderSummary();

    // 更新观演人信息
    updateAttendeeInfo();

    // 更新价格明细
    updatePriceBreakdown();

    // 更新支付按钮
    const payBtn = document.getElementById('payBtn');
    if (payBtn) {
        payBtn.textContent = `确认支付 ¥${totalPrice.toFixed(2)}`;
    }
}

function setPaymentHint(message, tone = 'muted') {
    const hintEl = document.getElementById('paymentHint');
    if (!hintEl) return;

    hintEl.textContent = message;
    hintEl.style.color = tone === 'error' ? '#d32f2f' : tone === 'success' ? '#2e7d32' : '#666';
}

function setPayButtonState(enabled, text) {
    const payBtn = document.getElementById('payBtn');
    if (!payBtn) return;

    payBtn.disabled = !enabled;
    payBtn.textContent = text || `确认支付 ¥${totalPrice.toFixed(2)}`;
    payBtn.style.opacity = enabled ? '1' : '0.65';
    payBtn.style.cursor = enabled ? 'pointer' : 'not-allowed';
}

async function waitForPaymentReady() {
    const deadlineMs = resolvePollDeadlineMs(orderExpireTime);
    let attempt = 0;

    while (Date.now() < deadlineMs) {
        attempt++;
        try {
            const orderDetail = await getOrderDetail(orderNo);
            console.log('订单轮询结果:', orderDetail);

            if (orderDetail) {
                orderStatus = orderDetail.statusDesc || orderStatus;
                paymentStatus = orderDetail.paymentStatus || paymentStatus;
                orderExpireTime = orderDetail.expireTime || orderExpireTime;
                nextPollMs = normalizePollMs(orderDetail.nextPollMs);

                if (orderDetail.payUrl) {
                    payUrl = orderDetail.payUrl;
                    sessionStorage.setItem('payUrl', payUrl);
                }

                sessionStorage.setItem('paymentStatus', paymentStatus);
                sessionStorage.setItem('orderStatus', orderStatus);
                sessionStorage.setItem('nextPollMs', String(nextPollMs));

                if (orderDetail.expireTime) {
                    sessionStorage.setItem('orderExpireTime', orderDetail.expireTime);
                }
            }

            if (payUrl && paymentStatus === 'READY') {
                setPaymentHint('支付信息已准备完成，可以继续支付。', 'success');
                setPayButtonState(true, `确认支付 ¥${totalPrice.toFixed(2)}`);
                return;
            }

            if (paymentStatus === 'SUCCESS') {
                setPaymentHint('订单已支付，请勿重复操作。', 'success');
                setPayButtonState(false, '订单已支付');
                return;
            }

            if (paymentStatus === 'NOT_AVAILABLE') {
                setPaymentHint(`${orderStatus || '订单状态已变更'}，当前无需继续轮询。`, 'error');
                setPayButtonState(false, orderStatus || '订单不可支付');
                return;
            }
        } catch (error) {
            console.error('轮询订单支付信息失败:', error);
            nextPollMs = computeFallbackPollMs(attempt, nextPollMs);
            sessionStorage.setItem('nextPollMs', String(nextPollMs));
            setPaymentHint(`查询订单信息失败，${Math.ceil(nextPollMs / 1000)}秒后重试...`, 'error');
            await sleep(nextPollMs);
            continue;
        }

        const delayMs = resolveNextPollDelay(attempt, nextPollMs, orderExpireTime);
        nextPollMs = delayMs;
        sessionStorage.setItem('nextPollMs', String(nextPollMs));
        setPaymentHint(`正在准备支付信息，${Math.ceil(delayMs / 1000)}秒后自动重试...`);
        await sleep(delayMs);
    }

    setPaymentHint('支付信息准备时间过长，请稍后在订单中心重试。', 'error');
    setPayButtonState(false, '支付信息未就绪');
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function normalizePollMs(value) {
    if (value === 0) {
        return 0;
    }
    if (!Number.isFinite(value) || value < 0) {
        return DEFAULT_POLL_INTERVAL_MS;
    }
    return Math.max(MIN_POLL_INTERVAL_MS, Math.min(MAX_POLL_INTERVAL_MS, Math.floor(value)));
}

function resolveNextPollDelay(attempt, suggestedMs, expireTimeValue) {
    const fallbackMs = computeFallbackPollMs(attempt, suggestedMs);
    const expireAtMs = parseDateTimeMs(expireTimeValue);

    if (!expireAtMs) {
        return fallbackMs;
    }

    const remainingMs = expireAtMs - Date.now();
    if (remainingMs <= 0) {
        return MIN_POLL_INTERVAL_MS;
    }
    return Math.min(fallbackMs, Math.max(MIN_POLL_INTERVAL_MS, remainingMs));
}

function computeFallbackPollMs(attempt, baseMs) {
    const safeBase = normalizePollMs(baseMs || DEFAULT_POLL_INTERVAL_MS);
    const multiplier = Math.min(4, Math.max(0, attempt - 1));
    return Math.min(MAX_POLL_INTERVAL_MS, safeBase + multiplier * 500);
}

function resolvePollDeadlineMs(expireTimeValue) {
    const fallbackDeadlineMs = Date.now() + POLL_MAX_WINDOW_MS;
    const expireAtMs = parseDateTimeMs(expireTimeValue);
    if (!expireAtMs) {
        return fallbackDeadlineMs;
    }
    return Math.min(fallbackDeadlineMs, expireAtMs);
}

function resolveCountdownSeconds(expireTimeValue) {
    const expireAtMs = parseDateTimeMs(expireTimeValue);
    if (!expireAtMs) {
        return 300;
    }
    return Math.max(0, Math.ceil((expireAtMs - Date.now()) / 1000));
}

function parseDateTimeMs(value) {
    if (!value) {
        return null;
    }
    const timestamp = new Date(value).getTime();
    return Number.isNaN(timestamp) ? null : timestamp;
}

/**
 * 更新订单摘要
 */
function updateOrderSummary() {
    const orderItem = document.querySelector('.order-item');
    if (!orderItem) return;

    const eventName = sessionData?.eventName || '演出名称';
    const startTime = formatDateTime(sessionData?.startTime);
    const address = sessionData?.address || '地址待定';

    // 构建座位信息
    const seatInfo = selectedSeats.map(seat => {
        return `${seat.areaName || seat.areaCode} ${seat.rowNum}排${seat.seatNum}座`;
    }).join('、');

    const orderInfo = `
        <div class="order-item-cover" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);"></div>
        <div class="order-item-info">
            <div class="order-item-title">${eventName}</div>
            <div class="order-item-meta">${startTime}</div>
            <div class="order-item-meta">${address}</div>
            <div class="order-item-meta">${seatInfo} × ${selectedSeats.length}张</div>
        </div>
        <div class="price price-large">¥${totalPrice.toFixed(2)}</div>
    `;

    orderItem.innerHTML = orderInfo;

    // 更新面包屑
    const breadcrumbEvent = document.querySelector('.breadcrumb-item:nth-child(2) a');
    if (breadcrumbEvent && eventId) {
        breadcrumbEvent.href = `event-detail.html?id=${eventId}`;
        breadcrumbEvent.textContent = eventName.length > 10 ? eventName.substring(0, 10) + '...' : eventName;
    }
}

/**
 * 更新观演人信息
 */
function updateAttendeeInfo() {
    const user = getCurrentUser();
    if (!user) return;

    // 姓名输入框
    const nameInput = document.querySelector('input[placeholder*="姓名"]') || document.querySelector('input[type="text"]');
    if (nameInput && nameInput.value === '张三') {
        nameInput.value = user.nickname || '';
    }

    // 手机号输入框
    const phoneInputs = document.querySelectorAll('input[type="text"]');
    phoneInputs.forEach(input => {
        if (input.value.includes('138')) {
            input.value = user.phone || '';
        }
    });

    // 身份证号（如果有）
    const idCardInput = document.querySelector('input[value="310101199001011234"]');
    if (idCardInput && user.idCard) {
        idCardInput.value = user.idCard;
    }
}

/**
 * 更新价格明细
 */
function updatePriceBreakdown() {
    // 票价总计
    const ticketPriceEl = document.querySelector('.price-row:nth-child(1) span:last-child');
    if (ticketPriceEl) {
        ticketPriceEl.textContent = `¥${totalPrice.toFixed(2)}`;
    }

    // 应付金额
    const totalAmountEl = document.querySelector('.price-row-total .price');
    if (totalAmountEl) {
        totalAmountEl.textContent = `¥${totalPrice.toFixed(2)}`;
    }

    // 更新所有价格显示
    const priceEls = document.querySelectorAll('.price-large');
    priceEls.forEach(el => {
        if (el.textContent.startsWith('¥') && el.textContent !== `¥${totalPrice.toFixed(2)}`) {
            el.textContent = `¥${totalPrice.toFixed(2)}`;
        }
    });
}

/**
 * 启动倒计时
 */
function startCountdown(seconds) {
    if (pollTimer) {
        clearInterval(pollTimer);
    }

    let remaining = seconds;
    const countdownEl = document.getElementById('countdown');

    const updateDisplay = () => {
        const minutes = Math.floor(remaining / 60);
        const secs = remaining % 60;
        const text = `${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;

        if (countdownEl) {
            countdownEl.textContent = text;
        }

        // 最后30秒变红提醒
        if (remaining <= 30 && countdownEl) {
            countdownEl.style.color = '#d32f2f';
            countdownEl.style.fontWeight = '600';
        }

        if (remaining <= 0) {
            handleTimeout();
        }
    };

    updateDisplay();

    pollTimer = setInterval(() => {
        remaining--;
        updateDisplay();

        if (remaining <= 0) {
            clearInterval(pollTimer);
            pollTimer = null;
        }
    }, 1000);
}

/**
 * 处理超时
 */
function handleTimeout() {
    showToast('订单已超时，请重新下单', 'warning');
    setTimeout(() => {
        window.location.href = 'seat-selection.html?sessionId=' + sessionId;
    }, 2000);
}

/**
 * 处理支付
 */
async function handlePayment() {
    // 验证协议勾选
    const agreementCheckbox = document.querySelector('input[type="checkbox"]');
    if (agreementCheckbox && !agreementCheckbox.checked) {
        showToast('请先阅读并同意《购票服务协议》', 'warning');
        return;
    }

    // 验证联系方式
    const contactInput = document.querySelector('.card:nth-child(3) input:first-of-type');
    if (contactInput && !contactInput.value.trim()) {
        showToast('请填写联系人信息', 'warning');
        return;
    }

    // 执行支付流程
    await processPayment();
}

/**
 * 处理支付流程
 * 新逻辑：重定向到支付系统页面
 */
async function processPayment() {
    const payBtn = document.getElementById('payBtn');
    if (payBtn) {
        payBtn.disabled = true;
        payBtn.textContent = '跳转中...';
    }

    try {
        console.log('=== 支付流程开始 ===');
        console.log('订单号:', orderNo);
        console.log('支付URL:', payUrl);

        // 验证订单号
        if (!orderNo) {
            console.error('【错误】订单号缺失！');
            throw new Error('订单号缺失，请重新下单');
        }

        // 验证支付URL
        if (!payUrl) {
            console.error('【错误】支付URL缺失！');
            throw new Error('支付信息尚未准备完成，请稍后重试');
        }

        console.log('【跳转到支付系统】');
        console.log('  payUrl:', payUrl);

        // 清除存储的订单信息
        sessionStorage.removeItem('selectedSeats');
        sessionStorage.removeItem('sessionId');
        sessionStorage.removeItem('eventId');
        sessionStorage.removeItem('sessionData');
        sessionStorage.removeItem('totalPrice');
        sessionStorage.removeItem('lockId');
        sessionStorage.removeItem('lockExpireTime');
        sessionStorage.removeItem('orderNo');
        sessionStorage.removeItem('payUrl');
        sessionStorage.removeItem('paymentStatus');
        sessionStorage.removeItem('orderStatus');
        sessionStorage.removeItem('orderExpireTime');
        sessionStorage.removeItem('nextPollMs');

        // 第一步：新开窗口访问模拟支付URL
        window.open(payUrl, '_blank');

        // 第二步：当前页面跳转到订单中心
        window.location.href = 'order-center.html';

    } catch (error) {
        console.error('【跳转失败】', error);
        console.error('【跳转失败】错误堆栈:', error.stack);
        showToast('跳转失败: ' + error.message, 'error');
        setPayButtonState(Boolean(payUrl), `确认支付 ¥${totalPrice.toFixed(2)}`);
    }
}

/**
 * 显示错误信息
 */
function showError(message) {
    const mainContent = document.querySelector('.main-content .container');
    if (mainContent) {
        const errorDiv = document.createElement('div');
        errorDiv.className = 'empty-state';
        errorDiv.style.cssText = 'padding: 60px 20px;';
        errorDiv.innerHTML = `
            <div class="empty-state-icon">⚠️</div>
            <div class="empty-state-text">${message}</div>
            <a href="index.html" class="btn btn-primary" style="margin-top: 16px;">返回首页</a>
        `;
        mainContent.innerHTML = '';
        mainContent.appendChild(errorDiv);
    }
}

/**
 * 显示提示消息
 */
function showToast(message, type = 'info') {
    alert(message);
}

/**
 * 格式化日期时间
 */
function formatDateTime(dateTime) {
    if (!dateTime) return '时间待定';
    const date = new Date(dateTime);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const weekDays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
    const weekDay = weekDays[date.getDay()];
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}年${month}月${day}日 ${weekDay} ${hours}:${minutes}`;
}

console.log('client-order-confirm.js 文件已加载');
