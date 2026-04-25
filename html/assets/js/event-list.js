/**
 * 演出列表页面逻辑
 * 文件：admin-events.html
 */

// 全局变量
let currentPage = 1;
const pageSize = 10;
let totalRecords = 0;
let totalPages = 0;

// 状态映射
const statusMap = {
    'draft': { text: '草稿', class: 'badge-secondary' },
    'on_sale': { text: '上架', class: 'badge-success' },
    'off_sale': { text: '下架', class: 'badge-danger' },
    'sold_out': { text: '已售完', class: 'badge-dark' }
};

// 类型映射
const typeMap = {
    'concert': '演唱会',
    'theatre': '话剧歌剧',
    'dance': '舞蹈芭蕾',
    'exhibition': '展览休闲',
    'sports': '体育',
    'kids': '儿童亲子',
    'music': '音乐'
};

// 页面加载时获取列表
window.addEventListener('DOMContentLoaded', loadEvents);

/**
 * 加载演出列表
 */
async function loadEvents() {
    try {
        const keyword = document.getElementById('searchInput').value.trim();
        const status = document.getElementById('statusSelect').value;
        const type = document.getElementById('typeSelect').value;

        const params = {
            page: currentPage,
            pageSize: pageSize
        };

        if (keyword) params.keyword = keyword;
        if (status) params.status = status;
        if (type) params.type = type;

        const result = await get('/api/admin/events', params);

        // 调试：检查返回的数据结构
        console.log('API返回数据:', result);

        // 确保数据格式正确
        const eventList = result.list || result.data || [];
        totalRecords = result.total || result.count || eventList.length || 0;
        totalPages = Math.ceil(totalRecords / pageSize);

        console.log('演出列表:', eventList);
        console.log('总记录数:', totalRecords);
        console.log('总页数:', totalPages);

        renderTable(eventList);
        renderPagination();
    } catch (error) {
        console.error('加载失败:', error);
        document.getElementById('eventTableBody').innerHTML =
            '<tr><td colspan="9" class="text-center text-muted">加载失败: ' + error.msg + '</td></tr>';
    }
}

/**
 * 渲染表格
 */
function renderTable(events) {
    const tbody = document.getElementById('eventTableBody');

    if (!events || events.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">暂无数据</td></tr>';
        return;
    }

    tbody.innerHTML = events.map(event => {
        const statusInfo = statusMap[event.status] || { text: event.status, class: 'badge-secondary' };
        const typeText = typeMap[event.type] || event.type;
        const actionButtons = renderActionButtons(event);

        return `
        <tr>
            <td class="col-id">${event.id}</td>
            <td class="col-name">${event.name}</td>
            <td class="col-type">${typeText}</td>
            <td class="col-artist">${event.artist}</td>
            <td class="col-city">${event.city}</td>
            <td class="col-status"><span class="badge ${statusInfo.class}">${statusInfo.text}</span></td>
            <td class="col-time">${event.createdAt || event.createTime}</td>
            <td class="col-action">
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
 * 根据状态渲染操作按钮
 * 状态流转规则：
 * draft(草稿) -> on_sale(上架) -> off_sale(下架) -> sold_out(已售完)
 * draft(草稿) 可以删除
 * on_sale(上架) -> off_sale(下架) 或 sold_out(已售完)
 * off_sale(下架) -> on_sale(上架)，不能直接跳到已售完
 * sold_out(已售完) 是最终状态，不可变更
 *
 * 所有状态统一显示3个按钮位置，用占位符保持对齐
 */
function renderActionButtons(event) {
    const status = event.status;
    let buttons = '';

    // 查看按钮（所有状态都有）- 第1个位置
    buttons += `<button class="action-btn action-btn-view" onclick="viewEvent(${event.id})">查看</button>`;

    switch (status) {
        case 'draft':
            // 草稿状态：编辑、发布、删除
            buttons += `<button class="action-btn action-btn-edit" onclick="editEvent(${event.id})">编辑</button>`;
            buttons += `<button class="action-btn action-btn-confirm" onclick="changeEventStatus(${event.id}, 'on_sale')">发布</button>`;
            buttons += `<button class="action-btn action-btn-delete" onclick="deleteEvent(${event.id})">删除</button>`;
            break;

        case 'on_sale':
            // 上架状态：编辑、下架、已售完
            buttons += `<button class="action-btn action-btn-edit" onclick="editEvent(${event.id})">编辑</button>`;
            buttons += `<button class="action-btn action-btn-delete" onclick="changeEventStatus(${event.id}, 'off_sale')">下架</button>`;
            buttons += `<button class="action-btn action-btn-warning" onclick="changeEventStatus(${event.id}, 'sold_out')">已售完</button>`;
            break;

        case 'off_sale':
            // 下架状态：编辑、上架、占位
            buttons += `<button class="action-btn action-btn-edit" onclick="editEvent(${event.id})">编辑</button>`;
            buttons += `<button class="action-btn action-btn-confirm" onclick="changeEventStatus(${event.id}, 'on_sale')">上架</button>`;
            buttons += `<button class="action-btn action-btn-placeholder" disabled></button>`;
            break;

        case 'sold_out':
            // 已售罄状态：最终状态，只有查看，其他位置占位
            buttons += `<button class="action-btn action-btn-placeholder" disabled></button>`;
            buttons += `<button class="action-btn action-btn-placeholder" disabled></button>`;
            buttons += `<button class="action-btn action-btn-placeholder" disabled></button>`;
            break;

        default:
            // 默认：编辑、下架、占位
            buttons += `<button class="action-btn action-btn-edit" onclick="editEvent(${event.id})">编辑</button>`;
            buttons += `<button class="action-btn action-btn-delete" onclick="changeEventStatus(${event.id}, 'off_sale')">下架</button>`;
            buttons += `<button class="action-btn action-btn-placeholder" disabled></button>`;
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
    loadEvents();
}

/**
 * 搜索按钮点击
 */
document.getElementById('searchBtn').addEventListener('click', () => {
    currentPage = 1;
    loadEvents();
});

/**
 * 回车搜索
 */
document.getElementById('searchInput').addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
        currentPage = 1;
        loadEvents();
    }
});

/**
 * 查看演出
 */
function viewEvent(id) {
    window.location.href = `admin-event-edit.html?id=${id}&readonly=true`;
}

/**
 * 编辑演出
 */
function editEvent(id) {
    window.location.href = `admin-event-edit.html?id=${id}`;
}

/**
 * 切换演出状态
 */
async function changeEventStatus(id, newStatus) {
    const statusText = {
        'on_sale': '上架',
        'off_sale': '下架',
        'sold_out': '已售完',
        'draft': '草稿'
    };

    const warningText = newStatus === 'sold_out' ?
        '设为已售完后，该演出将无法再恢复上架，确定要继续吗？' :
        `确定要将演出${statusText[newStatus]}吗？`;

    if (!confirm(warningText)) return;

    try {
        await put(`/api/admin/events/${id}/status`, { status: newStatus });
        alert('操作成功');
        loadEvents();
    } catch (error) {
        alert('操作失败: ' + error.msg);
    }
}

/**
 * 删除演出
 */
async function deleteEvent(id) {
    if (!confirm('确定要删除这个演出吗？删除后不可恢复！')) return;

    try {
        await del(`/api/admin/events/${id}`);
        alert('删除成功');
        loadEvents();
    } catch (error) {
        alert('删除失败: ' + error.msg);
    }
}
