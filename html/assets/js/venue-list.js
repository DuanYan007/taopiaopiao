/**
 * 场馆列表页面逻辑
 * 文件：admin-venues.html
 */

// 全局变量
let currentPage = 1;
const pageSize = 10;
let totalRecords = 0;
let totalPages = 0;

// 页面加载时获取列表
window.addEventListener('DOMContentLoaded', loadVenues);

/**
 * 加载场馆列表
 */
async function loadVenues() {
    try {
        const keyword = document.getElementById('searchInput').value.trim();
        const city = document.getElementById('citySelect').value;

        const params = {
            page: currentPage,
            pageSize: pageSize
        };

        if (keyword) params.keyword = keyword;
        if (city) params.city = city;

        const result = await get('/api/admin/venues', params);

        // 调试：检查返回的数据结构
        console.log('API返回数据:', result);

        // 确保数据格式正确
        const venueList = result.list || result.data || [];
        totalRecords = result.total || result.count || venueList.length || 0;
        totalPages = Math.ceil(totalRecords / pageSize);

        console.log('场馆列表:', venueList);
        console.log('总记录数:', totalRecords);
        console.log('总页数:', totalPages);

        renderTable(venueList);
        renderPagination();
    } catch (error) {
        console.error('加载失败:', error);
        document.getElementById('venueTableBody').innerHTML =
            '<tr><td colspan="8" class="text-center text-muted">加载失败: ' + error.msg + '</td></tr>';
    }
}

/**
 * 渲染表格
 */
function renderTable(venues) {
    const tbody = document.getElementById('venueTableBody');

    if (!venues || venues.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">暂无数据</td></tr>';
        return;
    }

    tbody.innerHTML = venues.map(venue => `
        <tr>
            <td class="col-id">${venue.id}</td>
            <td class="col-name">${venue.name}</td>
            <td class="col-city">${venue.city}</td>
            <td class="col-district">${venue.district || '-'}</td>
            <td class="col-address">${venue.address}</td>
            <td class="col-capacity">${venue.capacity ? venue.capacity.toLocaleString() : '-'}</td>
            <td class="col-created">${venue.createdAt || venue.createTime}</td>
            <td class="col-action">
                <div class="action-buttons">
                    <button class="action-btn action-btn-view" onclick="viewVenue(${venue.id})">查看</button>
                    <button class="action-btn action-btn-edit" onclick="editVenue(${venue.id})">编辑</button>
                    <button class="action-btn action-btn-delete" onclick="deleteVenue(${venue.id})">删除</button>
                </div>
            </td>
        </tr>
    `).join('');

    document.getElementById('totalCount').textContent = `共 ${totalRecords} 条记录`;
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
    loadVenues();
}

/**
 * 搜索按钮点击
 */
document.getElementById('searchBtn').addEventListener('click', () => {
    currentPage = 1;
    loadVenues();
});

/**
 * 回车搜索
 */
document.getElementById('searchInput').addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
        currentPage = 1;
        loadVenues();
    }
});

/**
 * 查看场馆
 */
function viewVenue(id) {
    window.location.href = `admin-venue-edit.html?id=${id}&readonly=true`;
}

/**
 * 编辑场馆
 */
function editVenue(id) {
    window.location.href = `admin-venue-edit.html?id=${id}`;
}

/**
 * 删除场馆
 */
async function deleteVenue(id) {
    if (!confirm('确定要删除这个场馆吗？删除后不可恢复！')) return;

    try {
        await del(`/api/admin/venues/${id}`);
        alert('删除成功');
        loadVenues();
    } catch (error) {
        alert('删除失败: ' + error.msg);
    }
}
