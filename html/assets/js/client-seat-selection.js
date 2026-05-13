/**
 * 淘票票客户端 - 选座页面逻辑
 * 文件：client-seat-selection.js
 * 页面：seat-selection.html
 */

// 全局变量
let currentSessionId = null;
let currentEventId = null;
let currentSessionData = null;
let selectedSeats = new Map(); // 使用 Map 存储选中的座位，key 为 "row-seat"
let seatLayoutData = null; // 座位布局数据
let seatStatusData = null; // 场次座位状态数据（实时）
let seatIdMap = new Map(); // 座位位置到座位ID的映射，key: "areaName-seatRow-seatColumn"（如 "A区-1排-01"），value: seatId

// 最大购票数量
const MAX_SEATS = 4;

// 锁座相关
let lockExpireTime = null;
let lockTimer = null;

// 缩放和拖拽相关变量
let zoomState = {
    scale: 1,
    minScale: 0.5,
    maxScale: 2,
    translateX: 0,
    translateY: 0,
    isDragging: false,
    startX: 0,
    startY: 0
};

/**
 * 页面初始化
 */
document.addEventListener('DOMContentLoaded', async () => {
    console.log('选座页面初始化');

    // 获取URL参数
    const urlParams = new URLSearchParams(window.location.search);
    currentSessionId = urlParams.get('sessionId');
    currentEventId = urlParams.get('eventId');

    // 更新用户信息显示
    updateUserInfo();

    if (currentSessionId) {
        await loadSessionData(currentSessionId);
    } else {
        showError('缺少场次ID参数');
    }

    // 绑定提交按钮事件
    const submitBtn = document.getElementById('submitBtn');
    if (submitBtn) {
        submitBtn.addEventListener('click', handleSubmit);
    }

    // 初始化缩放和拖拽功能
    initZoomAndPan();

    // 显示缩放提示（3秒后消失）
    setTimeout(() => {
        const hint = document.getElementById('zoomHint');
        if (hint) hint.classList.add('show');
        setTimeout(() => {
            if (hint) hint.classList.remove('show');
        }, 3000);
    }, 500);
});

/**
 * 更新用户信息显示
 */
function updateUserInfo() {
    const userInfoDiv = document.querySelector('.user-info');
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
 * 加载场次数据
 */
async function loadSessionData(sessionId) {
    const loadingEl = document.getElementById('loadingState');
    const errorEl = document.getElementById('errorState');
    const contentEl = document.getElementById('mainContent');

    try {
        if (loadingEl) loadingEl.style.display = 'flex';
        if (errorEl) errorEl.style.display = 'none';

        // 获取场次详情
        currentSessionData = await getSessionDetail(sessionId);
        console.log('场次数据:', currentSessionData);

        // 如果URL参数中没有eventId，从场次数据中获取
        if (!currentEventId && currentSessionData.eventId) {
            currentEventId = currentSessionData.eventId;
            console.log('从场次数据中获取eventId:', currentEventId);
        }

        // 更新页面信息
        updatePageInfo(currentSessionData);

        // 加载座位布局（使用秒杀接口，直接通过sessionId获取）
        await loadSeatLayout();

        if (loadingEl) loadingEl.style.display = 'none';
        if (contentEl) contentEl.style.display = 'block';

    } catch (error) {
        console.error('加载场次数据失败:', error);
        if (loadingEl) loadingEl.style.display = 'none';
        if (errorEl) {
            errorEl.style.display = 'flex';
            const errorText = errorEl.querySelector('.error-state-text');
            if (errorText) errorText.textContent = error.message || '加载失败，请重试';
        }
    }
}

/**
 * 更新页面信息显示
 */
function updatePageInfo(session) {
    // 更新标题
    document.title = `选座购票 - ${session.eventName} - 淘票票`;

    // 更新演出名称
    const eventNameEl = document.getElementById('eventName');
    if (eventNameEl) {
        eventNameEl.textContent = session.eventName || '演出名称';
    }

    // 更新场次信息
    const sessionInfoEl = document.getElementById('sessionInfo');
    if (sessionInfoEl) {
        const startTime = formatDateTime(session.startTime);
        sessionInfoEl.textContent = `${startTime} | ${session.address || '地址待定'}`;
    }

    // 更新面包屑
    const breadcrumbEvent = document.getElementById('breadcrumbEvent');
    if (breadcrumbEvent && session.eventId) {
        breadcrumbEvent.innerHTML = `<a href="event-detail.html?id=${session.eventId}">${session.eventName}</a>`;
    }

    // 更新观演人
    const attendeeEl = document.getElementById('attendeeName');
    if (attendeeEl) {
        const user = getCurrentUser();
        attendeeEl.textContent = user?.nickname || '待填写';
    }
}

/**
 * 加载座位布局（使用秒杀接口）
 * 直接从Redis获取整合后的座位数据
 */
async function loadSeatLayout() {
    try {
        // 使用新的秒杀接口，一次性获取座位布局、状态和价格
        console.log('调用秒杀座位布局接口，sessionId:', currentSessionId);
        const seckillData = await getSeckillLayout(currentSessionId);

        console.log('秒杀座位数据:', seckillData);

        // 保存原始数据（用于刷新等操作）
        seatLayoutData = seckillData;

        // 渲染座位图
        renderSeatMapFromSeckillData(seckillData);

    } catch (error) {
        console.error('加载座位布局失败:', error);
        renderEmptySeatMap();
    }
}

/**
 * 构建座位位置到ID的映射
 * 修复：使用 areaName + seatRow + seatColumn 作为唯一键，确保三者一致才匹配
 */
function buildSeatIdMap() {
    seatIdMap.clear();
    if (!seatStatusData || seatStatusData.length === 0) return;

    seatStatusData.forEach(seat => {
        // 后端数据格式: seatRow="1排", seatColumn="01", seatNumber="1排01座", area="A区"
        // 必须三者(区域、行、列)都一致才能匹配成功

        const areaName = seat.area;           // "A区"
        const seatRow = seat.seatRow;         // "1排"
        const seatColumn = seat.seatColumn;   // "01"

        if (!areaName || !seatRow || !seatColumn) {
            console.warn('座位数据不完整，跳过:', seat);
            return;
        }

        // 主键格式: "A区-1排-01" (区域名-行-列)
        // 这是唯一准确的匹配方式，三者必须一致
        const primaryKey = `${areaName}-${seatRow}-${seatColumn}`;
        seatIdMap.set(primaryKey, seat.id);

        // 辅助格式: "A区-1排01座" (区域名-完整座位号)
        if (seat.seatNumber) {
            seatIdMap.set(`${areaName}-${seat.seatNumber}`, seat.id);
        }

        // 辅助格式: 提取行号数字 "A区-1-01"
        const rowNumNum = seatRow.replace(/\D/g, ''); // "1排" -> "1"
        if (rowNumNum) {
            seatIdMap.set(`${areaName}-${rowNumNum}-${seatColumn}`, seat.id);
        }
    });

    console.log('座位ID映射表构建完成，映射数量:', seatIdMap.size);
}

/**
 * 根据位置获取座位状态
 * 修复：使用 areaName + rowNum + seatNum 三者匹配，确保准确对应
 */
function getSeatStatusByPosition(areaName, rowNum, seatNum) {
    if (!seatStatusData || seatStatusData.length === 0) {
        return SEAT_STATUS.AVAILABLE; // 默认可售
    }

    // 构建匹配条件：三者必须一致
    // areaName: "A区"
    // rowNum: 1 (模板中的行号数字)
    // seatNum: 1 (模板中的座位号数字)

    const seatRowLabel = `${rowNum}排`;  // "1排"
    const seatColumn = String(seatNum).padStart(2, '0');  // "01"

    // 尝试匹配：area + seatRow + seatColumn 三者一致
    let seat = seatStatusData.find(s => {
        return s.area === areaName &&
               s.seatRow === seatRowLabel &&
               s.seatColumn === seatColumn;
    });

    if (seat) {
        // 转换后端状态到前端状态
        return convertBackendStatus(seat.status);
    }

    // 如果没找到，尝试提取后端数据的行号数字再匹配（兼容处理）
    seat = seatStatusData.find(s => {
        const backendRowNum = parseInt(s.seatRow?.replace(/\D/g, ''), 10);
        const backendSeatNum = parseInt(s.seatColumn, 10);
        return s.area === areaName &&
               backendRowNum === rowNum &&
               backendSeatNum === seatNum;
    });

    if (seat) {
        return convertBackendStatus(seat.status);
    }

    // 未找到匹配座位，默认可售
    return SEAT_STATUS.AVAILABLE;
}

/**
 * 转换后端座位状态到前端状态
 * 新接口状态码: 0=空闲, 1=锁定, 2=售出
 */
function convertBackendStatus(backendStatus) {
    // 新接口使用数字状态码
    if (typeof backendStatus === 'number') {
        const statusMap = {
            0: SEAT_STATUS.AVAILABLE,   // 空闲
            1: SEAT_STATUS.LOCKED,       // 锁定
            2: SEAT_STATUS.SOLD          // 售出
        };
        return statusMap[backendStatus] || SEAT_STATUS.AVAILABLE;
    }

    // 旧接口兼容（字符串状态）
    const statusMap = {
        'available': SEAT_STATUS.AVAILABLE,
        'sold': SEAT_STATUS.SOLD,
        'locked': SEAT_STATUS.LOCKED,
        'unavailable': SEAT_STATUS.UNAVAILABLE
    };
    return statusMap[backendStatus] || SEAT_STATUS.AVAILABLE;
}

/**
 * 渲染座位图（从秒杀接口数据）
 * 数据结构: { meta: { areaNames, areaPrices }, areas: [[座位数组], [座位数组]] }
 */
function renderSeatMapFromSeckillData(seckillData) {
    const container = document.getElementById('seatsContent');
    if (!container) return;

    // 清空容器
    container.innerHTML = '';

    const { meta, areas } = seckillData;

    // 清空并重新构建座位ID映射
    seatIdMap.clear();

    // 遍历每个区域
    areas.forEach((areaSeats, areaIndex) => {
        const areaName = meta.areaNames[areaIndex] || `区域${areaIndex + 1}`;
        const areaPrice = meta.areaPrices[areaIndex] || 0;

        // 创建区域容器
        const areaDiv = document.createElement('div');
        areaDiv.className = 'seat-area';
        areaDiv.style.marginBottom = '16px';

        // 区域标题（显示区域名称和价格）
        const areaTitle = document.createElement('div');
        areaTitle.className = 'seat-area-title';
        areaTitle.innerHTML = `${areaName} <span style="color: #d32f2f; font-weight: bold;">¥${areaPrice}</span>`;
        areaDiv.appendChild(areaTitle);

        // 按行分组座位
        const rowsMap = new Map();
        areaSeats.forEach(seat => {
            if (!rowsMap.has(seat.row)) {
                rowsMap.set(seat.row, []);
            }
            rowsMap.get(seat.row).push(seat);

            // 构建座位ID映射
            seatIdMap.set(`${areaIndex}-${seat.row}-${seat.col}`, seat.id);
        });

        // 按行号排序并渲染
        const sortedRows = Array.from(rowsMap.entries()).sort((a, b) => a[0] - b[0]);

        sortedRows.forEach(([rowNum, seats]) => {
            const rowDiv = document.createElement('div');
            rowDiv.className = 'seat-row';
            rowDiv.style.cssText = `
                display: flex;
                gap: 8px;
                justify-content: center;
            `;

            // 按列号排序
            seats.sort((a, b) => a.col - b.col);

            // 渲染该行的座位
            seats.forEach(seat => {
                const seatEl = createSeatElementFromSeckill(seat, areaIndex, areaName, areaPrice);
                rowDiv.appendChild(seatEl);
            });

            areaDiv.appendChild(rowDiv);
        });

        container.appendChild(areaDiv);
    });

    console.log('座位ID映射表构建完成，映射数量:', seatIdMap.size);

    // 绑定座位点击事件
    bindSeatEvents();

    // 自动调整缩放以适应内容
    setTimeout(() => autoFitZoom(), 100);
}

/**
 * 从秒杀数据创建座位元素
 */
function createSeatElementFromSeckill(seat, areaIndex, areaName, areaPrice) {
    const seatEl = document.createElement('div');

    // 转换状态码 (0=空闲, 1=锁定, 2=售出)
    const realTimeStatus = convertBackendStatus(seat.status);

    // 构建座位数据标识
    const seatKey = `${areaIndex}-${seat.row}-${seat.col}`;
    seatEl.dataset.key = seatKey;
    seatEl.dataset.seatId = seat.id;
    seatEl.dataset.areaIndex = areaIndex;
    seatEl.dataset.areaCode = String.fromCharCode(65 + areaIndex); // 兼容: A, B, C...
    seatEl.dataset.areaName = areaName;
    seatEl.dataset.row = seat.row;
    seatEl.dataset.col = seat.col;
    seatEl.dataset.rowNum = seat.row;     // 兼容原有字段名
    seatEl.dataset.rowLabel = `${seat.row}排`;
    seatEl.dataset.seatNum = seat.col;
    seatEl.dataset.price = areaPrice;
    seatEl.dataset.realTimeStatus = realTimeStatus;

    // 设置CSS类（根据实时状态）
    seatEl.className = `seat ${getSeatStatusClass(realTimeStatus)}`;

    // 设置座位标签（显示列号）
    seatEl.textContent = seat.col;

    return seatEl;
}

/**
 * 渲染座位图（含实时状态）
 */
function renderSeatMapWithStatus(layoutData, seatStatus) {
    const container = document.getElementById('seatsContent');
    if (!container) return;

    // 清空容器
    container.innerHTML = '';

    // 遍历区域
    layoutData.areas.forEach((area, areaIndex) => {
        // 创建区域容器
        const areaDiv = document.createElement('div');
        areaDiv.className = 'seat-area';
        areaDiv.style.marginBottom = '16px';

        // 区域标题
        const areaTitle = document.createElement('div');
        areaTitle.className = 'seat-area-title';
        areaTitle.textContent = area.areaName || area.areaCode || `区域${areaIndex + 1}`;
        areaDiv.appendChild(areaTitle);

        // 遍历行
        area.rows.forEach((row) => {
            const rowDiv = document.createElement('div');
            rowDiv.className = 'seat-row';
            rowDiv.style.cssText = `
                display: flex;
                gap: 8px;
                justify-content: center;
            `;

            // 渲染该行的座位（传入区域和行信息用于状态查询）
            renderRowSeatsWithStatus(rowDiv, row, area);

            areaDiv.appendChild(rowDiv);
        });

        container.appendChild(areaDiv);
    });

    // 绑定座位点击事件
    bindSeatEvents();

    // 自动调整缩放以适应内容
    setTimeout(() => autoFitZoom(), 100);
}

/**
 * 渲染一行的座位（含状态）
 */
function renderRowSeatsWithStatus(rowDiv, row, area) {
    // 如果有详细的座位列表，使用它
    if (row.seats && row.seats.length > 0) {
        row.seats.forEach((seat) => {
            const seatEl = createSeatElementWithStatus(seat, row, area);
            rowDiv.appendChild(seatEl);
        });
    } else {
        // 否则根据 startSeat 和 endSeat 生成座位
        for (let i = row.startSeat; i <= row.endSeat; i++) {
            const seat = {
                seatNum: i
            };
            const seatEl = createSeatElementWithStatus(seat, row, area);
            rowDiv.appendChild(seatEl);
        }
    }
}

/**
 * 创建座位元素（含实时状态）
 * 修复：使用 areaName 进行精确匹配，确保区域、行、列三者一致
 */
function createSeatElementWithStatus(seat, row, area) {
    const seatEl = document.createElement('div');

    // 使用 areaName 获取实时座位状态
    const realTimeStatus = getSeatStatusByPosition(area.areaName, row.rowNum, seat.seatNum);

    // 查找座位ID - 使用 areaName + rowLabel + seatColumn 格式匹配
    // 后端数据: area="A区", seatRow="1排", seatColumn="01", seatNumber="1排01座"
    // 前端模板: areaName="A区", rowNum=1, rowLabel="1排", seatNum=1
    let seatId = '';

    const seatNumStr = String(seat.seatNum).padStart(2, '0'); // 1 -> "01"
    const rowLabel = row.rowLabel || `${row.rowNum}排`;  // "1排"

    // 主键格式: "A区-1排-01" (区域名-行标签-列号补零)
    let key = `${area.areaName}-${rowLabel}-${seatNumStr}`;
    seatId = seatIdMap.get(key);

    // 辅助格式1: "A区-1-01" (区域名-行号数字-列号补零)
    if (!seatId) {
        key = `${area.areaName}-${row.rowNum}-${seatNumStr}`;
        seatId = seatIdMap.get(key);
    }

    // 辅助格式2: "A区-1排01座" (区域名-完整座位号)
    if (!seatId) {
        key = `${area.areaName}-${rowLabel}${seatNumStr}座`;
        seatId = seatIdMap.get(key);
    }

    // 构建座位数据标识
    const seatKey = `${area.areaCode}-${row.rowNum}-${seat.seatNum}`;
    seatEl.dataset.key = seatKey;
    seatEl.dataset.seatId = seatId || '';  // 即使找不到ID也继续渲染
    seatEl.dataset.areaCode = area.areaCode;
    seatEl.dataset.areaName = area.areaName;  // 保存区域名称
    seatEl.dataset.rowNum = row.rowNum;
    seatEl.dataset.rowLabel = rowLabel;
    seatEl.dataset.seatNum = seat.seatNum;
    seatEl.dataset.price = area.price || '';
    seatEl.dataset.realTimeStatus = realTimeStatus;

    // 设置CSS类（根据实时状态）
    seatEl.className = `seat ${getSeatStatusClass(realTimeStatus)}`;

    // 设置座位标签（只显示座位号）
    seatEl.textContent = seat.seatNum;

    // 调试：记录无法匹配ID的座位
    if (!seatId) {
        console.warn('无法匹配座位ID:', {
            areaName: area.areaName,
            rowNum: row.rowNum,
            rowLabel: rowLabel,
            seatNum: seat.seatNum,
            attemptedKeys: [
                `${area.areaName}-${rowLabel}-${seatNumStr}`,
                `${area.areaName}-${row.rowNum}-${seatNumStr}`,
                `${area.areaName}-${rowLabel}${seatNumStr}座`
            ]
        });
    }

    return seatEl;
}

/**
 * 渲染座位图（旧版本，保留兼容）
 */
function renderSeatMap(layoutData) {
    return renderSeatMapWithStatus(layoutData, seatStatusData);
}

/**
 * 自动调整缩放以适应内容
 */
function autoFitZoom() {
    const canvas = document.getElementById('seatCanvas');
    const content = document.getElementById('seatsContent');
    if (!canvas || !content) return;

    const wrapper = document.getElementById('canvasWrapper');
    if (!wrapper) return;

    const wrapperRect = wrapper.getBoundingClientRect();
    const contentRect = content.getBoundingClientRect();

    // 计算内容相对于画布的比例
    const scaleX = (wrapperRect.width - 80) / contentRect.width;
    const scaleY = (wrapperRect.height - 80) / contentRect.height;
    const autoScale = Math.min(scaleX, scaleY, 1);

    // 限制在最小和最大缩放之间
    zoomState.scale = Math.max(zoomState.minScale, Math.min(zoomState.maxScale, autoScale));
    zoomState.translateX = 0;
    zoomState.translateY = 0;

    updateCanvasTransform();
    updateZoomControls();
}

/**
 * 渲染一行的座位
 */
function renderRowSeats(rowDiv, row, area) {
    // 如果有详细的座位列表，使用它
    if (row.seats && row.seats.length > 0) {
        row.seats.forEach((seat) => {
            const seatEl = createSeatElement(seat, row, area);
            rowDiv.appendChild(seatEl);
        });
    } else {
        // 否则根据 startSeat 和 endSeat 生成座位
        for (let i = row.startSeat; i <= row.endSeat; i++) {
            const seat = {
                seatNum: i,
                seatLabel: `${row.rowLabel.replace('排', '')}排${i}座`,
                available: true,
                status: SEAT_STATUS.AVAILABLE
            };
            const seatEl = createSeatElement(seat, row, area);
            rowDiv.appendChild(seatEl);
        }
    }
}

/**
 * 创建座位元素
 */
function createSeatElement(seat, row, area) {
    const seatEl = document.createElement('div');

    // 确定座位状态
    let status = seat.status || (seat.available ? SEAT_STATUS.AVAILABLE : SEAT_STATUS.SOLD);

    // 构建座位数据标识
    const seatKey = `${area.areaCode}-${row.rowNum}-${seat.seatNum}`;
    seatEl.dataset.key = seatKey;
    seatEl.dataset.areaCode = area.areaCode;
    seatEl.dataset.rowNum = row.rowNum;
    seatEl.dataset.seatNum = seat.seatNum;
    seatEl.dataset.price = area.price || '';
    seatEl.dataset.areaName = area.areaName;

    // 设置CSS类
    seatEl.className = `seat ${getSeatStatusClass(status)}`;

    // 设置座位标签（只显示座位号，不显示行号）
    seatEl.textContent = seat.seatNum;

    return seatEl;
}

/**
 * 绑定座位点击事件
 */
function bindSeatEvents() {
    const seats = document.querySelectorAll('.seat');
    seats.forEach(seat => {
        seat.addEventListener('click', handleSeatClick);
    });
}

/**
 * 初始化缩放和拖拽功能
 */
function initZoomAndPan() {
    const canvas = document.getElementById('seatCanvas');
    if (!canvas) return;

    // 鼠标滚轮缩放
    canvas.addEventListener('wheel', handleWheel, { passive: false });

    // 拖拽事件
    canvas.addEventListener('mousedown', handleMouseDown);
    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);

    // 触摸事件支持
    canvas.addEventListener('touchstart', handleTouchStart, { passive: false });
    canvas.addEventListener('touchmove', handleTouchMove, { passive: false });
    canvas.addEventListener('touchend', handleTouchEnd);

    // 缩放按钮
    const zoomInBtn = document.getElementById('zoomInBtn');
    const zoomOutBtn = document.getElementById('zoomOutBtn');
    const zoomResetBtn = document.getElementById('zoomResetBtn');

    if (zoomInBtn) zoomInBtn.addEventListener('click', () => zoomBy(0.1));
    if (zoomOutBtn) zoomOutBtn.addEventListener('click', () => zoomBy(-0.1));
    if (zoomResetBtn) zoomResetBtn.addEventListener('click', resetZoom);

    updateZoomControls();
}

/**
 * 处理鼠标滚轮缩放
 */
function handleWheel(e) {
    e.preventDefault();

    const delta = e.deltaY > 0 ? -0.1 : 0.1;
    zoomBy(delta);
}

/**
 * 缩放指定增量
 */
function zoomBy(delta) {
    const newScale = Math.max(zoomState.minScale, Math.min(zoomState.maxScale, zoomState.scale + delta));

    if (newScale !== zoomState.scale) {
        zoomState.scale = newScale;
        updateCanvasTransform();
        updateZoomControls();
    }
}

/**
 * 重置缩放
 */
function resetZoom() {
    zoomState.scale = 1;
    zoomState.translateX = 0;
    zoomState.translateY = 0;
    updateCanvasTransform();
    updateZoomControls();
}

/**
 * 更新画布变换
 */
function updateCanvasTransform() {
    const canvas = document.getElementById('seatCanvas');
    if (!canvas) return;

    canvas.style.transform = `translate(${zoomState.translateX}px, ${zoomState.translateY}px) scale(${zoomState.scale})`;
}

/**
 * 更新缩放控制按钮状态
 */
function updateZoomControls() {
    const zoomLevelEl = document.getElementById('zoomLevel');
    const zoomInBtn = document.getElementById('zoomInBtn');
    const zoomOutBtn = document.getElementById('zoomOutBtn');

    if (zoomLevelEl) {
        zoomLevelEl.textContent = Math.round(zoomState.scale * 100) + '%';
    }

    if (zoomInBtn) {
        zoomInBtn.disabled = zoomState.scale >= zoomState.maxScale;
    }

    if (zoomOutBtn) {
        zoomOutBtn.disabled = zoomState.scale <= zoomState.minScale;
    }
}

/**
 * 鼠标按下开始拖拽
 */
function handleMouseDown(e) {
    // 如果点击的是座位，不触发拖拽
    if (e.target.classList.contains('seat')) {
        return;
    }

    zoomState.isDragging = true;
    zoomState.startX = e.clientX - zoomState.translateX;
    zoomState.startY = e.clientY - zoomState.translateY;

    const canvas = document.getElementById('seatCanvas');
    if (canvas) {
        canvas.classList.add('dragging');
    }
}

/**
 * 鼠标移动拖拽
 */
function handleMouseMove(e) {
    if (!zoomState.isDragging) return;

    e.preventDefault();
    zoomState.translateX = e.clientX - zoomState.startX;
    zoomState.translateY = e.clientY - zoomState.startY;

    updateCanvasTransform();
}

/**
 * 鼠标松开结束拖拽
 */
function handleMouseUp() {
    if (zoomState.isDragging) {
        zoomState.isDragging = false;

        const canvas = document.getElementById('seatCanvas');
        if (canvas) {
            canvas.classList.remove('dragging');
        }
    }
}

/**
 * 触摸开始（支持移动端）
 */
let touchStartDistance = 0;
let touchStartScale = 1;

function handleTouchStart(e) {
    if (e.touches.length === 1) {
        // 单指拖拽
        if (e.target.classList.contains('seat')) {
            return;
        }

        zoomState.isDragging = true;
        zoomState.startX = e.touches[0].clientX - zoomState.translateX;
        zoomState.startY = e.touches[0].clientY - zoomState.translateY;

        const canvas = document.getElementById('seatCanvas');
        if (canvas) {
            canvas.classList.add('dragging');
        }
    } else if (e.touches.length === 2) {
        // 双指缩放
        const touch1 = e.touches[0];
        const touch2 = e.touches[1];
        touchStartDistance = Math.hypot(
            touch2.clientX - touch1.clientX,
            touch2.clientY - touch1.clientY
        );
        touchStartScale = zoomState.scale;
    }
}

/**
 * 触摸移动
 */
function handleTouchMove(e) {
    e.preventDefault();

    if (e.touches.length === 1 && zoomState.isDragging) {
        // 单指拖拽
        zoomState.translateX = e.touches[0].clientX - zoomState.startX;
        zoomState.translateY = e.touches[0].clientY - zoomState.startY;
        updateCanvasTransform();
    } else if (e.touches.length === 2) {
        // 双指缩放
        const touch1 = e.touches[0];
        const touch2 = e.touches[1];
        const distance = Math.hypot(
            touch2.clientX - touch1.clientX,
            touch2.clientY - touch1.clientY
        );

        const scaleDelta = (distance - touchStartDistance) / 200;
        const newScale = Math.max(
            zoomState.minScale,
            Math.min(zoomState.maxScale, touchStartScale + scaleDelta)
        );

        if (newScale !== zoomState.scale) {
            zoomState.scale = newScale;
            updateCanvasTransform();
            updateZoomControls();
        }
    }
}

/**
 * 触摸结束
 */
function handleTouchEnd() {
    zoomState.isDragging = false;

    const canvas = document.getElementById('seatCanvas');
    if (canvas) {
        canvas.classList.remove('dragging');
    }
}

/**
 * 处理座位点击
 */
function handleSeatClick(e) {
    // 阻止事件冒泡，避免触发拖拽
    e.stopPropagation();

    const seat = e.currentTarget;

    // 获取座位状态
    const currentStatus = getSeatStatusFromElement(seat);

    // 已售/已锁定/不可用座位不可选
    if ([SEAT_STATUS.SOLD, SEAT_STATUS.LOCKED, SEAT_STATUS.UNAVAILABLE].includes(currentStatus)) {
        return;
    }

    const seatKey = seat.dataset.key;

    // 检查是否已选中
    if (selectedSeats.has(seatKey)) {
        // 取消选择
        selectedSeats.delete(seatKey);
        seat.classList.remove('seat-selected');
        seat.classList.add('seat-available');
    } else {
        // 检查是否超过最大数量
        if (selectedSeats.size >= MAX_SEATS) {
            showToast(`每单最多选择${MAX_SEATS}个座位`, 'warning');
            return;
        }

        // 检查是否连续座位（同一行相邻）
        if (!checkSeatContinuity(seat)) {
            showToast('请选择连续的座位', 'warning');
            return;
        }

        // 选中座位
        selectedSeats.set(seatKey, {
            areaCode: seat.dataset.areaCode,
            areaName: seat.dataset.areaName,
            rowNum: seat.dataset.rowNum,
            seatNum: seat.dataset.seatNum,
            price: parseFloat(seat.dataset.price) || 0
        });

        seat.classList.remove('seat-available');
        seat.classList.add('seat-selected');
    }

    // 更新选中座位显示
    updateSelectedSeatsDisplay();
}

/**
 * 从元素获取座位状态
 */
function getSeatStatusFromElement(el) {
    if (el.classList.contains('seat-sold')) return SEAT_STATUS.SOLD;
    if (el.classList.contains('seat-locked')) return SEAT_STATUS.LOCKED;
    if (el.classList.contains('seat-unavailable')) return SEAT_STATUS.UNAVAILABLE;
    if (el.classList.contains('seat-selected')) return SEAT_STATUS.SELECTED;
    return SEAT_STATUS.AVAILABLE;
}

/**
 * 检查座位连续性
 */
function checkSeatContinuity(seat) {
    // 如果没有选中座位，任何座位都可以选
    if (selectedSeats.size === 0) return true;

    const newAreaCode = seat.dataset.areaCode;
    const newRowNum = seat.dataset.rowNum;
    const newSeatNum = parseInt(seat.dataset.seatNum);

    // 获取所有已选座位
    const selectedInSameRow = Array.from(selectedSeats.values())
        .filter(s => s.areaCode === newAreaCode && s.rowNum === newRowNum)
        .map(s => parseInt(s.seatNum))
        .sort((a, b) => a - b);

    // 如果同一行没有已选座位，允许选择
    if (selectedInSameRow.length === 0) return true;

    // 检查新座位是否与已选座位相邻
    const minSeat = Math.min(...selectedInSameRow);
    const maxSeat = Math.max(...selectedInSameRow);

    // 新座位必须在已选座位的相邻位置
    return (newSeatNum === minSeat - 1) || (newSeatNum === maxSeat + 1);
}

/**
 * 更新选中座位显示
 */
function updateSelectedSeatsDisplay() {
    const container = document.getElementById('selectedSeatsContainer');
    const countEl = document.getElementById('selectedCount');
    const totalEl = document.getElementById('totalPrice');

    if (!container) return;

    // 清空当前显示
    container.innerHTML = '';

    if (selectedSeats.size === 0) {
        container.innerHTML = '<div class="text-muted text-small text-center" style="padding: 20px;">请点击座位图选择座位</div>';
        if (countEl) countEl.textContent = '0个';
        if (totalEl) totalEl.textContent = '¥0';

        // 更新合计
        const totalAmountEl = document.getElementById('totalAmount');
        if (totalAmountEl) totalAmountEl.textContent = '¥0';
        return;
    }

    let totalPrice = 0;

    // 按区域分组显示
    const groupedByArea = {};
    selectedSeats.forEach((seat) => {
        const areaName = seat.areaName || seat.areaCode;
        if (!groupedByArea[areaName]) {
            groupedByArea[areaName] = [];
        }
        groupedByArea[areaName].push(seat);
        totalPrice += seat.price || 0;
    });

    // 渲染选中座位
    Object.keys(groupedByArea).forEach(areaName => {
        const seats = groupedByArea[areaName];

        seats.forEach(seat => {
            const seatDiv = document.createElement('div');
            seatDiv.className = 'selected-seat-item';
            seatDiv.style.cssText = `
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 8px 0;
                border-bottom: 1px solid #eee;
            `;
            seatDiv.innerHTML = `
                <div>
                    <div style="font-weight: 600; margin-bottom: 4px;">${areaName}</div>
                    <div style="color: #666; font-size: 13px;">${seat.rowNum}排${seat.seatNum}座</div>
                </div>
                <div class="price price-large">¥${seat.price || '-'}</div>
            `;
            container.appendChild(seatDiv);
        });
    });

    // 更新数量和总价
    if (countEl) countEl.textContent = `${selectedSeats.size}个`;
    if (totalEl) totalEl.textContent = `¥${totalPrice}`;

    // 更新合计价格
    const totalAmountEl = document.getElementById('totalAmount');
    if (totalAmountEl) totalAmountEl.textContent = `¥${totalPrice}`;
}

/**
 * 渲染空座位图
 */
function renderEmptySeatMap() {
    const container = document.getElementById('seatsContent');
    if (!container) return;

    container.innerHTML = `
        <div class="empty-state" style="padding: 40px 0;">
            <div class="empty-state-icon">🎭</div>
            <div class="empty-state-text">座位图暂未开放</div>
        </div>
    `;
}

/**
 * 处理提交
 */
async function handleSubmit() {
    if (selectedSeats.size === 0) {
        showToast('请先选择座位', 'warning');
        return;
    }

    const submitBtn = document.getElementById('submitBtn');
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = '处理中...';
    }

    try {
        // 收集座位ID
        const seatIds = [];
        const seatsData = [];

        // 从DOM获取选中的座位元素
        const selectedSeatElements = document.querySelectorAll('.seat.seat-selected');
        console.log('=== 提交选座 ===');
        console.log('选中的座位元素数量:', selectedSeatElements.length);
        selectedSeatElements.forEach(el => {
            const seatId = el.dataset.seatId;
            console.log('座位:', {
                seatId: seatId,
                rowLabel: el.dataset.rowLabel,
                rowNum: el.dataset.rowNum,
                seatNum: el.dataset.seatNum,
                areaCode: el.dataset.areaCode,
                price: el.dataset.price
            });
            if (seatId) {
                seatIds.push(seatId);
            }
            seatsData.push({
                seatId: seatId,
                areaCode: el.dataset.areaCode,
                areaName: el.dataset.areaName,
                rowNum: el.dataset.rowNum,
                seatNum: el.dataset.seatNum,
                price: parseFloat(el.dataset.price) || 0
            });
        });

        if (seatIds.length === 0) {
            showToast('无法获取座位信息，请刷新页面重试', 'error');
            return;
        }

        // 获取用户ID
        const user = getCurrentUser();
        const userId = user?.id || 1; // 默认测试用户ID为1

        // 获取座位单价（所有选中座位价格应一致）
        const unitPrice = seatsData.length > 0 ? seatsData[0].price : 0;

        // 验证所有座位价格一致
        const prices = new Set(seatsData.map(s => s.price));
        if (prices.size > 1) {
            showToast('选中的座位价格不一致，请重新选择', 'error');
            return;
        }

        // 调用锁座接口。锁座成功后会立即返回 orderNo，正式订单与支付信息异步收敛。
        console.log('=== 调用锁座接口 ===');
        console.log('请求参数:', { sessionId: currentSessionId, eventId: currentEventId, userId, seatIds, unitPrice });
        const lockResult = await lockSeats(currentSessionId, currentEventId, userId, seatIds, 900, unitPrice);

        console.log('=== 锁座结果 ===');
        console.log('完整响应:', lockResult);
        console.log('响应类型:', typeof lockResult);
        console.log('是否有 orderNo:', 'orderNo' in lockResult);
        console.log('orderNo 值:', lockResult?.orderNo);

        // clientPost 已解包，lockResult 就是 { success, code, message, lockedSeats, orderNo, expireTime, orderStatus, paymentStatus, nextPollMs }
        if (lockResult.success === true || lockResult.code === 0) {
            // 锁座成功，前端进入订单准备页并轮询支付信息
            lockExpireTime = lockResult.expireTime;
            const orderNo = lockResult.orderNo;
            const payUrl = lockResult.payUrl || '';

            console.log('=== 解析订单信息 ===');
            console.log('orderNo:', orderNo);
            console.log('payUrl(初始可能为空，后续由订单轮询补齐):', payUrl);

            if (!orderNo) {
                console.error('【错误】后端未返回 orderNo！');
                console.error('lockResult 原始数据:', JSON.stringify(lockResult));
                showToast('订单创建失败，请重试', 'error');
                return;
            }

            // 启动倒计时（5分钟 = 300秒）
            startLockCountdown(300);

            // 将选中的座位信息存储到 sessionStorage
            sessionStorage.setItem('selectedSeats', JSON.stringify(seatsData));
            sessionStorage.setItem('sessionId', currentSessionId);
            sessionStorage.setItem('eventId', currentEventId);
            sessionStorage.setItem('sessionData', JSON.stringify(currentSessionData));
            sessionStorage.setItem('lockExpireTime', lockExpireTime);
            sessionStorage.setItem('orderNo', orderNo);
            sessionStorage.setItem('payUrl', payUrl);
            sessionStorage.setItem('paymentStatus', lockResult.paymentStatus || 'NOT_READY');
            sessionStorage.setItem('orderStatus', lockResult.orderStatus || 'PROCESSING');
            sessionStorage.setItem('nextPollMs', String(lockResult.nextPollMs || 1200));
            if (lockResult.expireTime) {
                sessionStorage.setItem('orderExpireTime', lockResult.expireTime);
            }

            // 计算总价
            const totalPrice = seatsData.reduce((sum, seat) => sum + (seat.price || 0), 0);
            sessionStorage.setItem('totalPrice', totalPrice);

            // 【关键】跳转到支付确认页面，使用 orderNo 参数
            const targetUrl = `order-confirm.html?orderNo=${orderNo}`;
            console.log('=== 准备跳转到支付确认页面 ===');
            console.log('  目标 URL:', targetUrl);
            console.log('  orderNo:', orderNo);
            console.log('  payUrl:', payUrl);

            window.location.href = targetUrl;
        } else {
            // 锁座失败
            const errorMsg = lockResult.message || '锁座失败，请选择其他座位';
            showToast(errorMsg, 'error');

            // 刷新座位状态
            await refreshSeatStatus();
        }
    } catch (error) {
        console.error('锁座失败:', error);
        showToast('锁座失败: ' + error.message, 'error');
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = '创建订单并锁定座位';
        }
    }
}

/**
 * 刷新座位状态（使用秒杀接口）
 */
async function refreshSeatStatus() {
    try {
        const seckillData = await getSeckillLayout(currentSessionId);
        seatLayoutData = seckillData;
        renderSeatMapFromSeckillData(seckillData);
    } catch (error) {
        console.error('刷新座位状态失败:', error);
    }
}

/**
 * 启动锁座倒计时
 */
function startLockCountdown(seconds) {
    if (lockTimer) {
        clearInterval(lockTimer);
    }

    let remaining = seconds;

    lockTimer = setInterval(() => {
        remaining--;

        // 可以在这里更新倒计时显示

        if (remaining <= 0) {
            clearInterval(lockTimer);
            handleLockExpire();
        }
    }, 1000);
}

/**
 * 处理锁座过期
 */
function handleLockExpire() {
    showToast('座位锁定已过期，请重新选择', 'warning');
    // 刷新页面或跳转回选座页
    location.reload();
}

/**
 * 显示错误信息
 */
function showError(message) {
    const mainContent = document.querySelector('.main-content .container');
    if (mainContent) {
        mainContent.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">⚠️</div>
                <div class="empty-state-text">${message}</div>
                <a href="index.html" class="btn btn-primary" style="margin-top: 16px;">返回首页</a>
            </div>
        `;
    }
}

/**
 * 显示提示消息
 */
function showToast(message, type = 'info') {
    // 简单的提示实现，后续可以优化为 Toast 组件
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
    return `${year}-${month}-${day} ${weekDay} ${hours}:${minutes}`;
}

console.log('client-seat-selection.js 文件已加载');
