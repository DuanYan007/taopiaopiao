/**
 * 场次列表页面逻辑
 * 文件：admin-sessions.html
 */

// 全局变量
let currentPage = 1;
const pageSize = 10;
let totalRecords = 0;
let totalPages = 0;
let currentEventId = null; // 当前选中的演出ID

// 状态映射
const statusMap = {
    'not_started': { text: '未开始', class: 'badge-info' },
    'on_sale': { text: '销售中', class: 'badge-success' },
    'sold_out': { text: '已售完', class: 'badge-gray' },
    'ended': { text: '已结束', class: 'badge-dark' },
    'off_sale': { text: '已下架', class: 'badge-danger' }
};

// 页面加载时获取列表
window.addEventListener('DOMContentLoaded', () => {
    loadSessions();
    loadEvents(); // 加载演出列表用于筛选

    // 搜索按钮点击
    document.getElementById('searchBtn').addEventListener('click', () => {
        currentPage = 1;
        loadSessions();
    });

    // 回车搜索
    document.getElementById('searchInput').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            currentPage = 1;
            loadSessions();
        }
    });
});

/**
 * 加载场次列表
 */
async function loadSessions() {
    try {
        const keyword = document.getElementById('searchInput').value.trim();
        const status = document.getElementById('statusSelect').value;
        const eventId = document.getElementById('eventSelect').value;

        const params = {
            page: currentPage,
            pageSize: pageSize
        };

        if (keyword) params.keyword = keyword;
        if (status) params.status = status;
        if (eventId) params.eventId = eventId;

        const result = await get('/api/admin/sessions', params);

        // 调试：检查返回的数据结构
        console.log('API返回数据:', result);

        // 确保数据格式正确
        const sessionList = result.list || result.data || [];
        totalRecords = result.total || result.count || sessionList.length || 0;
        totalPages = Math.ceil(totalRecords / pageSize);

        console.log('场次列表:', sessionList);
        console.log('总记录数:', totalRecords);
        console.log('总页数:', totalPages);

        renderTable(sessionList);
        renderPagination();
    } catch (error) {
        console.error('加载失败:', error);
        document.getElementById('sessionTableBody').innerHTML =
            '<tr><td colspan="7" class="text-center text-muted">加载失败: ' + error.msg + '</td></tr>';
    }
}

/**
 * 加载演出列表（用于筛选）
 */
async function loadEvents() {
    try {
        const result = await get('/api/admin/events', { pageSize: 1000 });
        const eventList = result.list || result.data || [];

        console.log('演出列表:', eventList);

        const eventSelect = document.getElementById('eventSelect');
        if (!eventSelect) return;

        // 保存当前选中的值
        const currentValue = eventSelect.value;

        // 清空现有选项，保留第一个"全部演出"
        eventSelect.innerHTML = '<option value="">全部演出</option>';

        // 添加演出选项
        eventList.forEach(event => {
            const option = document.createElement('option');
            option.value = event.id;
            option.textContent = event.name;
            eventSelect.appendChild(option);
        });

        // 恢复选中的值
        if (currentValue) {
            eventSelect.value = currentValue;
        }

        // 监听演出选择变化
        eventSelect.addEventListener('change', () => {
            currentPage = 1;
            loadSessions();
        });
    } catch (error) {
        console.error('加载演出列表失败:', error);
    }
}

/**
 * 渲染表格
 */
function renderTable(sessions) {
    const tbody = document.getElementById('sessionTableBody');

    if (!sessions || sessions.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">暂无数据</td></tr>';
        return;
    }

    tbody.innerHTML = sessions.map(session => {
        const statusInfo = statusMap[session.status] || { text: session.status, class: 'badge-secondary' };
        const actionButtons = renderActionButtons(session);
        const seatInfo = renderSeatInfo(session);

        // 格式化时间
        const startTime = formatDateTime(session.startTime);

        return `
        <tr>
            <td>${session.id}</td>
            <td>
                <div style="font-weight: 600;">${session.eventName || '-'}</div>
                <div class="text-small text-muted">${session.event?.subtitle || ''}</div>
            </td>
            <td>
                <div style="font-weight: 500;">${session.sessionName || '-'}</div>
            </td>
            <td>${startTime}</td>
            <td>${seatInfo}</td>
            <td><span class="badge ${statusInfo.class}">${statusInfo.text}</span></td>
            <td>
                <div class="action-buttons">
                    ${actionButtons}
                </div>
            </td>
        </tr>
    `;
    }).join('');

    document.getElementById('totalCount').textContent = `共 ${totalRecords} 条记录`;
}

/**
 * 渲染座位信息
 */
function renderSeatInfo(session) {
    const totalSeats = session.totalSeats || 0;
    const soldSeats = session.soldSeats || 0;
    const availableSeats = session.availableSeats || 0;

    const soldPercent = totalSeats > 0 ? Math.round((soldSeats / totalSeats) * 100) : 0;

    return `
        <div class="text-small">
            <div>可售: <span class="text-success">${formatNumber(availableSeats)}</span></div>
            <div>已售: <span class="text-muted">${formatNumber(soldSeats)}</span> (${soldPercent}%)</div>
            <div class="text-muted" style="font-size: 11px;">总计: ${formatNumber(totalSeats)}</div>
        </div>
    `;
}

/**
 * 根据状态渲染操作按钮
 */
function renderActionButtons(session) {
    const status = session.status;
    const soldSeats = session.soldSeats || 0;
    let buttons = '';

    // 查看按钮（所有状态都有）
    buttons += `<button class="action-btn action-btn-view" onclick="viewSession(${session.id}, ${session.eventId})">查看</button>`;

    switch (status) {
        case 'not_started':
            // 草稿状态：编辑、上架、删除
            buttons += `<button class="action-btn action-btn-edit" onclick="editSession(${session.id}, ${session.eventId})">编辑</button>`;
            buttons += `<button class="action-btn action-btn-confirm" onclick="changeSessionStatus(${session.id}, 'on_sale')">上架</button>`;
            buttons += `<button class="action-btn action-btn-delete" onclick="deleteSession(${session.id})">删除</button>`;
            break;

        case 'on_sale':
            // 销售中状态：编辑、下架
            buttons += `<button class="action-btn action-btn-edit" onclick="editSession(${session.id}, ${session.eventId})">编辑</button>`;
            buttons += `<button class="action-btn action-btn-delete" onclick="changeSessionStatus(${session.id}, 'off_sale')">下架</button>`;
            break;

        case 'off_sale':
            // 下架状态：编辑、上架
            buttons += `<button class="action-btn action-btn-edit" onclick="editSession(${session.id}, ${session.eventId})">编辑</button>`;
            buttons += `<button class="action-btn action-btn-confirm" onclick="changeSessionStatus(${session.id}, 'on_sale')">上架</button>`;
            // 如果没有订单，可以删除
            if (soldSeats === 0) {
                buttons += `<button class="action-btn action-btn-delete" onclick="deleteSession(${session.id})">删除</button>`;
            }
            break;

        case 'sold_out':
            // 已售罄状态：只查看
            break;

        case 'ended':
            // 已结束状态：只查看
            break;

        default:
            // 默认：编辑、下架
            buttons += `<button class="action-btn action-btn-edit" onclick="editSession(${session.id}, ${session.eventId})">编辑</button>`;
            buttons += `<button class="action-btn action-btn-delete" onclick="changeSessionStatus(${session.id}, 'off_sale')">下架</button>`;
    }

    return buttons;
}

/**
 * 渲染分页
 */
function renderPagination() {
    // 计算当前页的起始和结束
    const start = totalRecords > 0 ? (currentPage - 1) * pageSize + 1 : 0;
    const end = Math.min(currentPage * pageSize, totalRecords);

    // 更新分页信息
    document.getElementById('pageInfo').textContent =
        `显示 ${start}-${end} 条，共 ${totalRecords} 条`;

    const paginationContainer = document.getElementById('paginationBtns');

    // 如果没有数据或只有1页，不显示分页按钮
    if (totalRecords === 0 || totalPages <= 1) {
        paginationContainer.innerHTML = '';
        return;
    }

    let buttons = '';

    // 上一页
    buttons += `<button class="btn btn-secondary btn-small" ${currentPage === 1 ? 'disabled' : ''} onclick="goToPage(${currentPage - 1})">上一页</button>`;

    // 页码按钮
    for (let i = 1; i <= totalPages; i++) {
        // 显示：第一页、最后一页、当前页前后2页
        if (i === 1 || i === totalPages || (i >= currentPage - 2 && i <= currentPage + 2)) {
            const btnClass = i === currentPage ? 'btn-primary' : 'btn-secondary';
            buttons += `<button class="btn ${btnClass} btn-small" onclick="goToPage(${i})">${i}</button>`;
        } else if (i === currentPage - 3 || i === currentPage + 3) {
            // 显示省略号
            buttons += `<span style="padding: 0 4px; color: #666;">...</span>`;
        }
    }

    // 下一页
    buttons += `<button class="btn btn-secondary btn-small" ${currentPage === totalPages ? 'disabled' : ''} onclick="goToPage(${currentPage + 1})">下一页</button>`;

    paginationContainer.innerHTML = buttons;

    console.log('分页按钮已渲染，共', totalPages, '页');
}

/**
 * 跳转到指定页
 */
function goToPage(page) {
    if (page < 1 || page > totalPages) return;
    currentPage = page;
    loadSessions();
}

/**
 * 查看场次
 */
function viewSession(id, eventId) {
    window.location.href = `admin-session-edit.html?id=${id}&eventId=${eventId}&readonly=true`;
}

/**
 * 编辑场次
 */
function editSession(id, eventId) {
    window.location.href = `admin-session-edit.html?id=${id}&eventId=${eventId}`;
}

/**
 * 切换场次状态
 */
async function changeSessionStatus(id, newStatus) {
    const statusText = {
        'on_sale': '上架',
        'off_sale': '下架',
        'not_started': '设为草稿'
    };

    const confirmMessages = {
        'on_sale': '确定要上架该场次吗？上架后将开始销售。',
        'off_sale': '确定要下架该场次吗？下架后将停止销售，已售出的订单不受影响。',
        'not_started': '确定要将场次设为草稿吗？'
    };

    if (!confirm(confirmMessages[newStatus] || `确定要将场次${statusText[newStatus]}吗？`)) return;

    try {
        await put(`/api/admin/sessions/${id}/status`, { status: newStatus });
        alert('操作成功');
        loadSessions();
    } catch (error) {
        alert('操作失败: ' + error.msg);
    }
}

/**
 * 删除场次
 */
async function deleteSession(id) {
    if (!confirm('确定要删除这个场次吗？删除后不可恢复！\n注意：已有订单的场次无法删除。')) return;

    try {
        await del(`/api/admin/sessions/${id}`);
        alert('删除成功');
        loadSessions();
    } catch (error) {
        alert('删除失败: ' + error.msg);
    }
}

/**
 * 辅助函数：格式化日期时间
 */
function formatDateTime(dateString) {
    if (!dateString) return '-';

    const date = new Date(dateString);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');

    return `${year}-${month}-${day} ${hours}:${minutes}`;
}

/**
 * 辅助函数：格式化数字（添加千位分隔符）
 */
function formatNumber(num) {
    if (num == null) return '0';
    return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}
