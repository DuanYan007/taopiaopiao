/**
 * 淘票票客户端 - 支付结果页面逻辑
 * 文件：client-reservation-result.js
 * 页面：reservation-result.html
 * 说明：该页面是支付成功后返回的页面，根据订单号查询完整信息进行渲染
 */

// 全局变量
let orderData = null;
let eventData = null;   // 演出信息
let sessionData = null; // 场次信息
let orderNo = null;
let localOrderContext = null;

/**
 * 页面初始化
 */
document.addEventListener('DOMContentLoaded', async () => {
    console.log('支付结果页面初始化');

    restoreLocalOrderContext();

    // 更新用户信息显示
    updateUserInfo();

    // 从 URL 参数获取订单号和状态
    const urlParams = new URLSearchParams(window.location.search);
    const status = urlParams.get('status') || 'success';
    orderNo = urlParams.get('orderNo');

    // 必须有订单号才能显示内容
    if (!orderNo) {
        showFailStatus('订单号缺失，请从订单中心进入');
        return;
    }

    // 根据状态加载不同内容
    if (status === 'success') {
        // 支付成功 - 从后端获取订单详情，然后查询演出和场次信息
        await loadOrderAndEventInfo(orderNo);
    } else if (status === 'pending') {
        // 待支付状态 - 显示待支付信息
        await loadPendingOrderInfo(orderNo);
    } else {
        // 失败状态
        showFailStatus('操作失败，请重试');
    }
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
 * 加载订单、演出和场次信息
 */
async function loadOrderAndEventInfo(orderNo) {
    const container = document.querySelector('.main-content .container > div');
    if (!container) return;

    try {
        // 显示加载中
        container.innerHTML = `
            <div class="loading-state" style="padding: 60px 0;">
                <div class="loading-spinner"></div>
                <div class="loading-text">加载订单信息...</div>
            </div>
        `;

        // 1. 获取订单详情
        console.log('获取订单详情:', orderNo);
        orderData = await getOrderDetail(orderNo);
        console.log('订单详情:', orderData);

        // 从订单和本地缓存中获取演出ID和场次ID
        const eventId = orderData.eventId || localOrderContext?.eventId;
        const sessionId = orderData.sessionId || localOrderContext?.sessionId;

        if (!eventId || !sessionId) {
            console.warn('订单中缺少eventId或sessionId，仅使用订单数据渲染');
            renderSuccessStatus(orderData, null, null);
            return;
        }

        // 2. 并行获取演出和场次详情
        console.log('获取演出和场次信息:', eventId, sessionId);
        const [eventResult, sessionResult] = await Promise.allSettled([
            getEventDetail(eventId),
            getSessionDetail(sessionId)
        ]);

        eventData = eventResult.status === 'fulfilled' ? eventResult.value : null;
        sessionData = sessionResult.status === 'fulfilled' ? sessionResult.value : null;

        console.log('演出信息:', eventData);
        console.log('场次信息:', sessionData);

        // 3. 渲染完整信息
        renderSuccessStatus(orderData, eventData, sessionData);

    } catch (error) {
        console.error('加载信息失败:', error);
        // 如果获取演出/场次信息失败，尝试只渲染订单信息
        if (orderData) {
            renderSuccessStatus(orderData, null, null);
        } else {
            showFailStatus('加载订单信息失败，请刷新页面');
        }
    }
}

/**
 * 加载未完成支付订单信息。
 */
async function loadPendingOrderInfo(orderNo) {
    const container = document.querySelector('.main-content .container > div');
    if (!container) return;

    try {
        container.innerHTML = `
            <div class="loading-state" style="padding: 60px 0;">
                <div class="loading-spinner"></div>
                <div class="loading-text">加载中...</div>
            </div>
        `;

        // 获取订单详情
        orderData = await getOrderDetail(orderNo);
        console.log('订单详情:', orderData);

        // 未完成支付的订单继续回到支付确认页，由该页负责轮询支付准备状态。
        window.location.href = `order-confirm.html?orderNo=${orderNo}`;

    } catch (error) {
        console.error('加载订单详情失败:', error);
        showFailStatus('加载订单信息失败，请刷新页面');
    }
}

/**
 * 渲染支付成功状态
 * @param {object} order - 订单信息
 * @param {object} event - 演出信息（可能为null）
 * @param {object} session - 场次信息（可能为null）
 */
function renderSuccessStatus(order, event, session) {
    const container = document.querySelector('.main-content .container > div');
    if (!container) return;

    // 根据订单状态判断
    const status = order.status; // 1=未支付, 2=已支付, 3=已取消, 4=已退款
    const statusDesc = order.statusDesc || (status === 2 ? '支付成功' : '操作成功');
    const isPaid = status === 2;  // 2=已支付

    const localSession = localOrderContext?.sessionData || null;
    const localSeats = localOrderContext?.selectedSeats || [];

    // 优先使用从API获取的完整信息，否则使用订单中的字段或本地缓存
    const eventName = event?.eventName
        || event?.name
        || localSession?.eventName
        || order.eventName
        || '演出信息待补充';
    const startTime = session?.startTime
        || session?.eventStartTime
        || localSession?.startTime
        || order.startTime
        || order.sessionStartTime
        || '';
    const formattedTime = formatDateTime(startTime);
    const venueName = session?.venueName
        || session?.address
        || session?.hallName
        || localSession?.address
        || order.venueName
        || order.address
        || '场馆信息待补充';
    const seatInfo = order.seatInfo || buildSeatInfoFromLocalSeats(localSeats);

    // 单价
    const unitPrice = order.unitPrice || order.price || localSeats[0]?.price || 0;
    const seatCount = order.seatCount || localSeats.length || 0;
    const totalAmount = order.totalAmount || localOrderContext?.totalPrice || 0;

    // 生成模拟二维码
    const qrCodeUrl = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${order.orderNo || orderNo}`;

    const containerHTML = `
        <style>
            /* 支付结果页专用样式 */
            .result-page {
                max-width: 900px;
                margin: 0 auto;
                padding: 40px 20px;
            }

            /* 成功状态头部 */
            .result-header {
                text-align: center;
                padding: 40px 20px;
                background: linear-gradient(135deg, #4caf50 0%, #2e7d32 100%);
                border-radius: 16px;
                color: white;
                margin-bottom: 32px;
                position: relative;
                overflow: hidden;
            }

            .result-header::before {
                content: '';
                position: absolute;
                top: -50%;
                right: -50%;
                width: 200%;
                height: 200%;
                background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%);
                animation: rotate 20s linear infinite;
            }

            @keyframes rotate {
                from { transform: rotate(0deg); }
                to { transform: rotate(360deg); }
            }

            .result-icon {
                width: 80px;
                height: 80px;
                background: white;
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                margin: 0 auto 20px;
                position: relative;
                z-index: 1;
            }

            .result-icon svg {
                width: 48px;
                height: 48px;
            }

            .result-title {
                font-size: 32px;
                font-weight: 700;
                margin-bottom: 8px;
                position: relative;
                z-index: 1;
            }

            .result-subtitle {
                font-size: 16px;
                opacity: 0.9;
                position: relative;
                z-index: 1;
            }

            /* 电子票样式 */
            .ticket-card {
                display: flex;
                background: white;
                border-radius: 16px;
                box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
                overflow: hidden;
                margin-bottom: 24px;
            }

            .ticket-left {
                flex: 1;
                padding: 32px;
                border-right: 2px dashed #ddd;
                position: relative;
            }

            .ticket-left::after {
                content: '';
                position: absolute;
                right: -10px;
                top: 50%;
                transform: translateY(-50%);
                width: 20px;
                height: 20px;
                background: #f5f5f5;
                border-radius: 50%;
            }

            .ticket-left::before {
                content: '';
                position: absolute;
                right: -10px;
                top: -20px;
                width: 20px;
                height: 20px;
                background: #f5f5f5;
                border-radius: 50%;
            }

            .ticket-right {
                width: 280px;
                padding: 32px 24px;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                background: linear-gradient(135deg, #f5f5f5 0%, #eeeeee 100%);
                position: relative;
            }

            .ticket-right::after {
                content: '';
                position: absolute;
                left: -10px;
                top: 50%;
                transform: translateY(-50%);
                width: 20px;
                height: 20px;
                background: #f5f5f5;
                border-radius: 50%;
            }

            .ticket-right::before {
                content: '';
                position: absolute;
                left: -10px;
                top: -20px;
                width: 20px;
                height: 20px;
                background: #f5f5f5;
                border-radius: 50%;
            }

            .ticket-event-name {
                font-size: 22px;
                font-weight: 700;
                color: #333;
                margin-bottom: 20px;
                line-height: 1.4;
            }

            .ticket-info-row {
                display: flex;
                margin-bottom: 16px;
                align-items: flex-start;
            }

            .ticket-info-icon {
                width: 20px;
                height: 20px;
                margin-right: 12px;
                flex-shrink: 0;
                margin-top: 2px;
            }

            .ticket-info-icon svg {
                width: 100%;
                height: 100%;
            }

            .ticket-info-content {
                flex: 1;
            }

            .ticket-info-label {
                font-size: 12px;
                color: #999;
                margin-bottom: 4px;
            }

            .ticket-info-value {
                font-size: 15px;
                color: #333;
                font-weight: 500;
            }

            .ticket-qr {
                width: 160px;
                height: 160px;
                background: white;
                border-radius: 12px;
                padding: 12px;
                margin-bottom: 12px;
            }

            .ticket-qr img {
                width: 100%;
                height: 100%;
                object-fit: contain;
            }

            .ticket-qr-hint {
                font-size: 12px;
                color: #666;
                text-align: center;
            }

            .ticket-price {
                margin-top: 24px;
                text-align: center;
            }

            .ticket-price-label {
                font-size: 14px;
                color: #666;
                margin-bottom: 4px;
            }

            .ticket-price-value {
                font-size: 36px;
                font-weight: 700;
                color: #d32f2f;
            }

            /* 提示卡片 */
            .tips-card {
                background: #e8f5e9;
                border-radius: 12px;
                padding: 20px 24px;
                margin-bottom: 24px;
            }

            .tips-card-title {
                font-size: 16px;
                font-weight: 600;
                color: #2e7d32;
                margin-bottom: 12px;
                display: flex;
                align-items: center;
                gap: 8px;
            }

            .tips-card-content {
                font-size: 14px;
                color: #2e7d32;
                line-height: 1.8;
            }

            .tips-card-content p {
                margin-bottom: 8px;
                display: flex;
                align-items: flex-start;
            }

            .tips-card-content p::before {
                content: '•';
                margin-right: 8px;
                font-weight: bold;
            }

            /* 操作按钮 */
            .action-buttons {
                display: flex;
                gap: 16px;
                margin-bottom: 24px;
            }

            .action-buttons a {
                flex: 1;
                padding: 14px 24px;
                border-radius: 8px;
                text-align: center;
                font-size: 16px;
                font-weight: 600;
                text-decoration: none;
                transition: all 0.3s;
            }

            .action-btn-primary {
                background: #1976d2;
                color: white;
            }

            .action-btn-primary:hover {
                background: #1565c0;
                transform: translateY(-2px);
                box-shadow: 0 4px 12px rgba(25, 118, 210, 0.4);
            }

            .action-btn-secondary {
                background: #f5f5f5;
                color: #333;
                border: 1px solid #ddd;
            }

            .action-btn-secondary:hover {
                background: #eeeeee;
            }

            /* 订单号显示 */
            .order-no-display {
                text-align: center;
                padding: 16px;
                background: #f9f9f9;
                border-radius: 8px;
                margin-bottom: 24px;
            }

            .order-no-label {
                font-size: 14px;
                color: #666;
                margin-bottom: 4px;
            }

            .order-no-value {
                font-size: 18px;
                font-weight: 600;
                color: #333;
                font-family: 'Courier New', monospace;
            }

            /* 快捷链接 */
            .quick-links {
                display: flex;
                justify-content: center;
                gap: 32px;
                padding-top: 16px;
                border-top: 1px solid #eee;
            }

            .quick-links a {
                color: #1976d2;
                text-decoration: none;
                font-size: 14px;
                transition: color 0.3s;
            }

            .quick-links a:hover {
                color: #1565c0;
                text-decoration: underline;
            }

            /* 响应式 */
            @media (max-width: 768px) {
                .ticket-card {
                    flex-direction: column;
                }

                .ticket-left {
                    border-right: none;
                    border-bottom: 2px dashed #ddd;
                }

                .ticket-left::after,
                .ticket-left::before {
                    display: none;
                }

                .ticket-right::before {
                    top: auto;
                    bottom: -10px;
                }

                .ticket-right {
                    width: 100%;
                }

                .action-buttons {
                    flex-direction: column;
                }

                .quick-links {
                    flex-direction: column;
                    gap: 12px;
                }
            }
        </style>

        <div class="result-page">
            <!-- 成功状态头部 -->
            <div class="result-header">
                <div class="result-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#4caf50" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                        <polyline points="20 6 9 17 4 12"></polyline>
                    </svg>
                </div>
                <div class="result-title">${statusDesc}</div>
                <div class="result-subtitle">恭喜您，购票成功！电子票已生成</div>
            </div>

            <!-- 订单号 -->
            <div class="order-no-display">
                <div class="order-no-label">订单号</div>
                <div class="order-no-value">${order.orderNo || orderNo}</div>
            </div>

            <!-- 电子票 -->
            <div class="ticket-card">
                <div class="ticket-left">
                    <div class="ticket-event-name">${eventName}</div>

                    <div class="ticket-info-row">
                        <div class="ticket-info-icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#666" stroke-width="2">
                                <circle cx="12" cy="12" r="10"></circle>
                                <polyline points="12 6 12 12 16 14"></polyline>
                            </svg>
                        </div>
                        <div class="ticket-info-content">
                            <div class="ticket-info-label">演出时间</div>
                            <div class="ticket-info-value">${formattedTime}</div>
                        </div>
                    </div>

                    <div class="ticket-info-row">
                        <div class="ticket-info-icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#666" stroke-width="2">
                                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                                <circle cx="12" cy="10" r="3"></circle>
                            </svg>
                        </div>
                        <div class="ticket-info-content">
                            <div class="ticket-info-label">演出场馆</div>
                            <div class="ticket-info-value">${venueName}</div>
                        </div>
                    </div>

                    ${seatInfo ? `
                    <div class="ticket-info-row">
                        <div class="ticket-info-icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#666" stroke-width="2">
                                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                                <line x1="3" y1="10" x2="21" y2="10"></line>
                                <line x1="9" y1="15" x2="15" y2="15"></line>
                            </svg>
                        </div>
                        <div class="ticket-info-content">
                            <div class="ticket-info-label">座位信息</div>
                            <div class="ticket-info-value">${seatInfo}</div>
                        </div>
                    </div>
                    ` : ''}

                    <div class="ticket-info-row">
                        <div class="ticket-info-icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#666" stroke-width="2">
                                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                                <line x1="16" y1="2" x2="16" y2="6"></line>
                                <line x1="8" y1="2" x2="8" y2="6"></line>
                                <line x1="3" y1="10" x2="21" y2="10"></line>
                            </svg>
                        </div>
                        <div class="ticket-info-content">
                            <div class="ticket-info-label">票数 / 单价</div>
                            <div class="ticket-info-value">${seatCount}张 × ¥${unitPrice}</div>
                        </div>
                    </div>
                </div>

                <div class="ticket-right">
                    <div class="ticket-qr">
                        <img src="${qrCodeUrl}" alt="入场二维码" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                        <div style="display:none; width:100%; height:100%; align-items:center; justify-content:center; background:#f5f5f5; border-radius:8px;">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#999" stroke-width="1" style="width:60px; height:60px;">
                                <rect x="3" y="3" width="18" height="18" rx="2"></rect>
                                <rect x="7" y="7" width="10" height="10"></rect>
                            </svg>
                        </div>
                    </div>
                    <div class="ticket-qr-hint">演出当天扫码入场</div>
                    <div class="ticket-price">
                        <div class="ticket-price-label">实付金额</div>
                        <div class="ticket-price-value">¥${totalAmount}</div>
                    </div>
                </div>
            </div>

            <!-- 购票提示 -->
            <div class="tips-card">
                <div class="tips-card-title">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <circle cx="12" cy="12" r="10"></circle>
                        <line x1="12" y1="16" x2="12" y2="12"></line>
                        <line x1="12" y1="8" x2="12.01" y2="8"></line>
                    </svg>
                    购票提示
                </div>
                <div class="tips-card-content">
                    <p>电子票已发送至您的账户，可前往"我的订单"查看</p>
                    <p>演出当天凭电子票二维码扫码入场</p>
                    <p>建议提前30分钟到达场馆，有序检票入场</p>
                    <p>如需退票，请在演出前24小时在线申请</p>
                </div>
            </div>

            <!-- 操作按钮 -->
            <div class="action-buttons">
                <a href="order-detail.html?orderNo=${order.orderNo || orderNo}" class="action-btn-primary">查看订单详情</a>
                <a href="index.html" class="action-btn-secondary">返回首页</a>
            </div>

            <!-- 快捷链接 -->
            <div class="quick-links">
                <a href="order-center.html">查看我的订单 →</a>
                <a href="event-detail.html?id=${order.eventId || ''}">查看演出详情 →</a>
            </div>
        </div>
    `;

    container.innerHTML = containerHTML;
}

function restoreLocalOrderContext() {
    try {
        const storedSessionId = sessionStorage.getItem('sessionId');
        const storedEventId = sessionStorage.getItem('eventId');
        const storedSessionData = sessionStorage.getItem('sessionData');
        const storedSeats = sessionStorage.getItem('selectedSeats');
        const storedTotalPrice = sessionStorage.getItem('totalPrice');

        localOrderContext = {
            sessionId: storedSessionId ? Number(storedSessionId) : null,
            eventId: storedEventId ? Number(storedEventId) : null,
            sessionData: storedSessionData ? JSON.parse(storedSessionData) : null,
            selectedSeats: storedSeats ? JSON.parse(storedSeats) : [],
            totalPrice: storedTotalPrice ? Number(storedTotalPrice) : null
        };
    } catch (error) {
        console.warn('恢复本地订单上下文失败:', error);
        localOrderContext = null;
    }
}

function buildSeatInfoFromLocalSeats(seats) {
    if (!Array.isArray(seats) || seats.length === 0) {
        return '';
    }
    return seats.map(seat => {
        const areaName = seat.areaName || seat.areaCode || '';
        const rowNum = seat.rowNum ? `${seat.rowNum}排` : '';
        const seatNum = seat.seatNum ? `${String(seat.seatNum).padStart(2, '0')}座` : '';
        return [areaName, rowNum, seatNum].filter(Boolean).join(' ');
    }).join(' / ');
}

/**
 * 显示失败状态
 */
function showFailStatus(message) {
    const container = document.querySelector('.main-content .container > div');
    if (!container) return;

    container.innerHTML = `
        <style>
            .result-page-error {
                max-width: 600px;
                margin: 0 auto;
                padding: 60px 20px;
            }

            .error-header {
                text-align: center;
                padding: 40px 20px;
                background: linear-gradient(135deg, #ef5350 0%, #c62828 100%);
                border-radius: 16px;
                color: white;
                margin-bottom: 32px;
            }

            .error-icon {
                width: 80px;
                height: 80px;
                background: white;
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                margin: 0 auto 20px;
            }

            .error-icon svg {
                width: 48px;
                height: 48px;
            }

            .error-title {
                font-size: 28px;
                font-weight: 700;
                margin-bottom: 8px;
            }

            .error-subtitle {
                font-size: 16px;
                opacity: 0.9;
            }

            .error-actions {
                display: flex;
                gap: 16px;
            }

            .error-actions a {
                flex: 1;
                padding: 14px 24px;
                border-radius: 8px;
                text-align: center;
                font-size: 16px;
                font-weight: 600;
                text-decoration: none;
                transition: all 0.3s;
            }

            .error-btn-primary {
                background: #1976d2;
                color: white;
            }

            .error-btn-primary:hover {
                background: #1565c0;
            }

            .error-btn-secondary {
                background: #f5f5f5;
                color: #333;
                border: 1px solid #ddd;
            }

            .error-btn-secondary:hover {
                background: #eeeeee;
            }

            @media (max-width: 768px) {
                .error-actions {
                    flex-direction: column;
                }
            }
        </style>

        <div class="result-page-error">
            <div class="error-header">
                <div class="error-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#ef5350" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                        <line x1="18" y1="6" x2="6" y2="18"></line>
                        <line x1="6" y1="6" x2="18" y2="18"></line>
                    </svg>
                </div>
                <div class="error-title">操作失败</div>
                <div class="error-subtitle">${message}</div>
            </div>

            <div class="error-actions">
                <a href="index.html" class="error-btn-primary">返回首页</a>
                <a href="order-center.html" class="error-btn-secondary">我的订单</a>
            </div>
        </div>
    `;
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

console.log('client-reservation-result.js 文件已加载');
