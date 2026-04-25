/**
 * 座位模板列表页面逻辑
 * 文件：admin-seat-templates.html
 */

// 全局变量
let currentPage = 1;
const pageSize = 10;
let totalRecords = 0;
let totalPages = 0;

// 布局类型映射
const layoutTypeMap = {
    1: { text: '普通', class: 'badge-primary' },
    2: { text: 'VIP分区', class: 'badge-warning' },
    3: { text: '混合', class: 'badge-info' },
    4: { text: '自定义', class: 'badge-secondary' }
};

// 状态映射
const statusMap = {
    0: { text: '禁用', class: 'badge-gray' },
    1: { text: '启用', class: 'badge-success' }
};

// 页面加载时获取列表
window.addEventListener('DOMContentLoaded', () => {
    loadTemplates();
    loadVenues();
});

/**
 * 加载场馆列表（用于筛选）
 */
async function loadVenues() {
    try {
        const result = await get('/api/admin/venues/all');
        const venueList = result.list || result.data || result || [];

        const venueSelect = document.getElementById('venueSelect');
        if (!venueSelect) return;

        // 保存当前选中的值
        const currentValue = venueSelect.value;

        // 清空现有选项，保留第一项
        venueSelect.innerHTML = '<option value="">全部场馆</option>';

        // 添加场馆选项
        venueList.forEach(venue => {
            const option = document.createElement('option');
            option.value = venue.id;
            option.textContent = venue.name;
            venueSelect.appendChild(option);
        });

        // 恢复选中的值
        if (currentValue) {
            venueSelect.value = currentValue;
        }
    } catch (error) {
        console.error('加载场馆列表失败:', error);
    }
}

/**
 * 加载座位模板列表
 */
async function loadTemplates() {
    try {
        const keyword = document.getElementById('searchInput').value.trim();
        const venueId = document.getElementById('venueSelect').value;
        const layoutType = document.getElementById('layoutTypeSelect').value;
        const status = document.getElementById('statusSelect').value;

        const params = {
            pageNum: currentPage,
            pageSize: pageSize
        };

        if (keyword) params.name = keyword;
        if (venueId) params.venueId = venueId;
        if (layoutType) params.layoutType = layoutType;
        if (status !== '') params.status = status;

        const result = await get('/api/admin/seat-templates', params);

        // 调试：检查返回的数据结构
        console.log('API返回数据:', result);

        // 确保数据格式正确
        const templateList = result.records || result.list || result.data || [];
        totalRecords = result.total || 0;
        totalPages = Math.ceil(totalRecords / pageSize);

        console.log('模板列表:', templateList);
        console.log('总记录数:', totalRecords);
        console.log('总页数:', totalPages);

        renderTable(templateList);
        renderPagination();
    } catch (error) {
        console.error('加载失败:', error);
        const tbody = document.getElementById('templateTableBody');
        tbody.innerHTML =
            '<tr><td colspan="9" class="text-center text-muted">加载失败: ' + error.message + '</td></tr>';
    }
}

/**
 * 渲染表格
 */
function renderTable(templates) {
    const tbody = document.getElementById('templateTableBody');

    if (!templates || templates.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center text-muted">暂无数据</td></tr>';
        return;
    }

    tbody.innerHTML = templates.map(template => {
        const layoutTypeInfo = layoutTypeMap[template.layoutType] || { text: '未知', class: 'badge-secondary' };
        const statusInfo = statusMap[template.status] || { text: '未知', class: 'badge-secondary' };

        return `
        <tr>
            <td class="col-id">${template.id}</td>
            <td class="col-name">${escapeHtml(template.name)}</td>
            <td class="col-code"><code style="font-size: 12px; background: #f5f5f5; padding: 2px 6px; border-radius: 3px;">${escapeHtml(template.templateCode)}</code></td>
            <td class="col-venue">${escapeHtml(template.venueName || '-')}</td>
            <td class="col-layout"><span class="badge ${layoutTypeInfo.class}">${layoutTypeInfo.text}</span></td>
            <td class="col-seats">${template.totalSeats || 0} 座</td>
            <td class="col-status"><span class="badge ${statusInfo.class}">${statusInfo.text}</span></td>
            <td class="col-created">${formatDate(template.createdAt)}</td>
            <td class="col-action">
                <div class="action-buttons">
                    <button class="action-btn action-btn-view" onclick="viewTemplate(${template.id})">查看</button>
                    <button class="action-btn action-btn-edit" onclick="editTemplate(${template.id})">编辑</button>
                    <button class="action-btn action-btn-delete" onclick="deleteTemplate(${template.id})">删除</button>
                </div>
            </td>
        </tr>
    `;
    }).join('');
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
}

/**
 * 跳转到指定页
 */
function goToPage(page) {
    if (page < 1 || page > totalPages) return;
    currentPage = page;
    loadTemplates();
}

/**
 * 搜索按钮点击
 */
document.getElementById('searchBtn')?.addEventListener('click', () => {
    currentPage = 1;
    loadTemplates();
});

/**
 * 回车搜索
 */
document.getElementById('searchInput')?.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
        currentPage = 1;
        loadTemplates();
    }
});

/**
 * 重置按钮点击
 */
document.getElementById('resetBtn')?.addEventListener('click', () => {
    document.getElementById('searchInput').value = '';
    document.getElementById('venueSelect').value = '';
    document.getElementById('layoutTypeSelect').value = '';
    document.getElementById('statusSelect').value = '';
    currentPage = 1;
    loadTemplates();
});

/**
 * 查看模板
 */
function viewTemplate(id) {
    window.location.href = `admin-seat-template-edit.html?id=${id}&readonly=true`;
}

/**
 * 编辑模板
 */
function editTemplate(id) {
    window.location.href = `admin-seat-template-edit.html?id=${id}`;
}

/**
 * 删除模板
 */
async function deleteTemplate(id) {
    if (!confirm('确定要删除这个座位模板吗？删除后不可恢复！')) return;

    try {
        await del(`/api/admin/seat-templates/${id}`);
        alert('删除成功');
        loadTemplates();
    } catch (error) {
        alert('删除失败: ' + error.message);
    }
}

/**
 * 辅助函数：格式化日期
 */
function formatDate(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return '-';
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

/**
 * 辅助函数：转义HTML
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
