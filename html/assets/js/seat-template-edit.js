/**
 * 座位模板编辑页面逻辑
 * 文件：admin-seat-template-edit.html
 */

let templateId = null;
let isReadonly = false;
let areas = []; // 存储所有区域数据
let currentEditingAreaIndex = -1; // 当前编辑的区域索引

// 布局类型映射
const layoutTypeMap = {
    1: '普通',
    2: 'VIP分区',
    3: '混合',
    4: '自定义'
};

/**
 * 页面初始化
 */
window.addEventListener('DOMContentLoaded', async () => {
    // 获取URL参数
    const params = new URLSearchParams(window.location.search);
    templateId = params.get('id');
    isReadonly = params.get('readonly') === 'true';

    // 修改标题
    const title = document.getElementById('pageTitle');
    title.textContent = templateId ? (isReadonly ? '查看座位模板' : '编辑座位模板') : '新建座位模板';

    // 加载场馆列表
    await loadVenues();

    // 如果是只读模式，禁用表单
    if (isReadonly) {
        disableForm();
        // 隐藏保存按钮
        const submitBtn = document.getElementById('submitBtn');
        if (submitBtn) submitBtn.style.display = 'none';
        const addAreaBtn = document.getElementById('addAreaBtn');
        if (addAreaBtn) addAreaBtn.style.display = 'none';
    }

    // 如果有ID（编辑或查看模式），加载模板数据
    if (templateId) {
        await loadTemplateData();
    }

    // 绑定取消按钮
    const cancelBtn = document.getElementById('cancelBtn');
    const draftBtn = document.getElementById('draftBtn');
    const cancelHandler = () => {
        window.location.href = 'admin-seat-templates.html';
    };
    if (cancelBtn) cancelBtn.addEventListener('click', cancelHandler);
    if (draftBtn) draftBtn.addEventListener('click', cancelHandler);

    // 绑定表单提交
    const templateForm = document.getElementById('templateForm');
    if (templateForm) {
        templateForm.addEventListener('submit', handleFormSubmit);
    }

    // 绑定添加区域按钮
    const addAreaBtn = document.getElementById('addAreaBtn');
    if (addAreaBtn && !isReadonly) {
        addAreaBtn.addEventListener('click', openAddAreaModal);
    }

    // 初始化布局类型选择器
    updateLayoutTypeSelector();
});

/**
 * 加载场馆列表
 */
async function loadVenues() {
    try {
        const result = await get('/api/admin/venues/all');
        const venueList = result.list || result.data || result || [];

        const venueSelect = document.querySelector('[name="venueId"]');
        if (!venueSelect) return;

        const currentValue = venueSelect.value;
        venueSelect.innerHTML = '<option value="">请选择场馆</option>';

        venueList.forEach(venue => {
            const option = document.createElement('option');
            option.value = venue.id;
            option.textContent = venue.name;
            venueSelect.appendChild(option);
        });

        if (currentValue) {
            venueSelect.value = currentValue;
        }
    } catch (error) {
        console.error('加载场馆列表失败:', error);
    }
}

/**
 * 加载模板数据
 */
async function loadTemplateData() {
    try {
        const template = await get(`/api/admin/seat-templates/${templateId}`);

        // 回填基本信息
        setFormValue('name', template.name);
        setFormValue('templateCode', template.templateCode);
        setFormValue('venueId', template.venueId);
        setFormValue('layoutType', template.layoutType);
        setFormValue('totalRows', template.totalRows);
        setFormValue('totalSeats', template.totalSeats);
        setFormValue('status', template.status);

        // 解析布局数据
        if (template.layoutData) {
            try {
                const layoutData = JSON.parse(template.layoutData);
                areas = (layoutData.areas || []).map(area => {
                    // 为每个区域计算 totalSeats
                    const rowsWithCount = (area.rows || []).map(row => ({
                        ...row,
                        seatCount: (row.endSeat - row.startSeat + 1) || 0
                    }));
                    return {
                        ...area,
                        rows: rowsWithCount,
                        totalSeats: rowsWithCount.reduce((sum, row) => sum + row.seatCount, 0)
                    };
                });
                renderAreas();
                updateTotals(); // 确保加载后更新总数显示
            } catch (e) {
                console.error('解析布局数据失败:', e);
            }
        }

        updateLayoutTypeSelector();
    } catch (error) {
        console.error('加载失败:', error);
        alert('加载模板数据失败: ' + error.message);
    }
}

/**
 * 更新布局类型选择器
 */
function updateLayoutTypeSelector() {
    const layoutType = parseInt(document.querySelector('[name="layoutType"]')?.value) || 1;
    document.querySelectorAll('.layout-type-option').forEach(el => {
        el.classList.remove('active');
        if (parseInt(el.dataset.type) === layoutType) {
            el.classList.add('active');
        }
    });
}

/**
 * 选择布局类型
 */
function selectLayoutType(type) {
    const select = document.querySelector('[name="layoutType"]');
    if (select) {
        select.value = type;
        updateLayoutTypeSelector();
    }
}

/**
 * 禁用表单（查看模式）
 */
function disableForm() {
    document.querySelectorAll('#templateForm input, #templateForm select, #templateForm button').forEach(el => {
        if (el.type !== 'hidden' && el.id !== 'cancelBtn' && el.id !== 'draftBtn') {
            el.disabled = true;
        }
    });
}

/**
 * 打开添加区域弹窗
 */
function openAddAreaModal() {
    currentEditingAreaIndex = -1;

    // 清空表单
    document.getElementById('areaCode').value = '';
    document.getElementById('areaName').value = '';
    document.getElementById('areaColor').value = '#FF6B6B';
    document.getElementById('areaPrice').value = '';

    // 清空行配置
    document.getElementById('rowsConfigContainer').innerHTML = '';
    addRowConfig(); // 默认添加一行

    // 显示弹窗
    document.getElementById('areaModal').style.display = 'flex';
}

/**
 * 关闭区域弹窗
 */
function closeAreaModal() {
    document.getElementById('areaModal').style.display = 'none';
}

/**
 * 添加行配置
 */
function addRowConfig() {
    const container = document.getElementById('rowsConfigContainer');
    const index = container.children.length;

    const rowHtml = `
        <div class="admin-form-row" style="align-items: flex-end; background: #f5f5f5; padding: 8px; border-radius: 4px; margin-bottom: 8px;">
            <div class="form-group" style="flex: 0 0 100px;">
                <label class="form-label text-small">行号</label>
                <input type="number" class="form-input row-num-input" value="${index + 1}" min="1" placeholder="行号">
            </div>
            <div class="form-group" style="flex: 0 0 120px;">
                <label class="form-label text-small">行标签</label>
                <input type="text" class="form-input row-label-input" value="第${index + 1}排" placeholder="如：1排">
            </div>
            <div class="form-group" style="flex: 0 0 100px;">
                <label class="form-label text-small">起始座位</label>
                <input type="number" class="form-input seat-start-input" value="1" min="1" placeholder="起始号">
            </div>
            <div class="form-group" style="flex: 0 0 100px;">
                <label class="form-label text-small">结束座位</label>
                <input type="number" class="form-input seat-end-input" value="20" min="1" placeholder="结束号">
            </div>
            <div class="form-group" style="flex: 0 0 80px;">
                <label class="form-label text-small">间隔</label>
                <input type="number" class="form-input seat-gap-input" value="0" min="0" placeholder="0">
            </div>
            <div style="padding-bottom: 14px;">
                <button type="button" class="btn btn-outline btn-small" onclick="this.closest('.admin-form-row').remove()">删除</button>
            </div>
        </div>
    `;

    container.insertAdjacentHTML('beforeend', rowHtml);
}

/**
 * 保存区域
 */
function saveAreaModal() {
    const areaCode = document.getElementById('areaCode').value.trim();
    const areaName = document.getElementById('areaName').value.trim();
    const areaColor = document.getElementById('areaColor').value;
    const areaPrice = document.getElementById('areaPrice').value;

    // 验证
    if (!areaCode) {
        alert('请输入区域编码');
        return;
    }
    if (!areaName) {
        alert('请输入区域名称');
        return;
    }

    // 检查编码重复
    const isDuplicate = areas.some((area, index) =>
        area.areaCode === areaCode && index !== currentEditingAreaIndex
    );
    if (isDuplicate) {
        alert('区域编码已存在，请使用其他编码');
        return;
    }

    // 收集行配置
    const rows = [];
    document.querySelectorAll('#rowsConfigContainer .admin-form-row').forEach(rowEl => {
        const rowNum = parseInt(rowEl.querySelector('.row-num-input')?.value) || 1;
        const rowLabel = rowEl.querySelector('.row-label-input')?.value.trim() || `${rowNum}排`;
        const startSeat = parseInt(rowEl.querySelector('.seat-start-input')?.value) || 1;
        const endSeat = parseInt(rowEl.querySelector('.seat-end-input')?.value) || 1;
        const seatGap = parseInt(rowEl.querySelector('.seat-gap-input')?.value) || 0;

        if (endSeat >= startSeat) {
            rows.push({
                rowNum,
                rowLabel,
                startSeat,
                endSeat,
                seatGap,
                seatCount: endSeat - startSeat + 1
            });
        }
    });

    if (rows.length === 0) {
        alert('请至少添加一行座位配置');
        return;
    }

    // 构建区域数据
    const area = {
        areaCode,
        areaName,
        color: areaColor,
        price: areaPrice ? parseFloat(areaPrice) : null,
        rows,
        totalSeats: rows.reduce((sum, row) => sum + row.seatCount, 0)
    };

    if (currentEditingAreaIndex >= 0) {
        areas[currentEditingAreaIndex] = area;
    } else {
        areas.push(area);
    }

    renderAreas();
    updateTotals();
    closeAreaModal();
}

/**
 * 渲染区域列表
 */
function renderAreas() {
    const container = document.getElementById('areasContainer');

    if (!areas || areas.length === 0) {
        container.innerHTML = `
            <div class="text-center text-muted" style="padding: 32px;">
                暂无区域，请点击上方"添加区域"按钮添加
            </div>
        `;
        return;
    }

    container.innerHTML = areas.map((area, areaIndex) => {
        const rowsHtml = area.rows.map((row, rowIndex) => {
            // 生成座位预览点
            let seatDots = '';
            const dotCount = Math.min(row.seatCount, 30); // 最多显示30个点
            for (let i = 0; i < dotCount; i++) {
                seatDots += `<div class="seat-dot available">${i + 1}</div>`;
            }
            if (row.seatCount > 30) {
                seatDots += `<span style="color: #999; font-size: 12px; align-self: center;">...等${row.seatCount}座</span>`;
            }

            return `
                <div class="row-item">
                    <span class="row-label">${row.rowLabel}</span>
                    <span class="text-small text-muted">第${row.startSeat}-${row.endSeat}座</span>
                    <div class="seat-preview">${seatDots}</div>
                </div>
            `;
        }).join('');

        return `
            <div class="area-item">
                <div class="area-header">
                    <div>
                        <span class="area-title">${escapeHtml(area.areaName)}</span>
                        <code style="margin-left: 8px;">${escapeHtml(area.areaCode)}</code>
                        <span class="area-color-indicator" style="background: ${area.color};"></span>
                    </div>
                    <div>
                        ${area.price ? `<span class="badge badge-success">¥${area.price}</span>` : ''}
                        <span class="text-small text-muted" style="margin-left: 8px;">共 ${area.totalSeats} 座</span>
                        ${!isReadonly ? `
                            <button type="button" class="btn btn-secondary btn-small" style="margin-left: 8px;" onclick="editArea(${areaIndex})">编辑</button>
                            <button type="button" class="btn btn-outline btn-small" onclick="deleteArea(${areaIndex})">删除</button>
                        ` : ''}
                    </div>
                </div>
                <div class="row-list">
                    ${rowsHtml}
                </div>
            </div>
        `;
    }).join('');

    // 更新区域统计
    const totalAreas = areas.length;
    const totalSeats = areas.reduce((sum, area) => sum + (area.totalSeats || 0), 0);
    document.getElementById('areaSummary').textContent = `共 ${totalAreas} 个区域，${totalSeats} 个座位`;
}

/**
 * 编辑区域
 */
function editArea(index) {
    currentEditingAreaIndex = index;
    const area = areas[index];

    // 填充表单
    document.getElementById('areaCode').value = area.areaCode;
    document.getElementById('areaName').value = area.areaName;
    document.getElementById('areaColor').value = area.color;
    document.getElementById('areaPrice').value = area.price || '';

    // 填充行配置
    const container = document.getElementById('rowsConfigContainer');
    container.innerHTML = '';

    area.rows.forEach(row => {
        const rowHtml = `
            <div class="admin-form-row" style="align-items: flex-end; background: #f5f5f5; padding: 8px; border-radius: 4px; margin-bottom: 8px;">
                <div class="form-group" style="flex: 0 0 100px;">
                    <label class="form-label text-small">行号</label>
                    <input type="number" class="form-input row-num-input" value="${row.rowNum}" min="1" placeholder="行号">
                </div>
                <div class="form-group" style="flex: 0 0 120px;">
                    <label class="form-label text-small">行标签</label>
                    <input type="text" class="form-input row-label-input" value="${escapeHtml(row.rowLabel)}" placeholder="如：1排">
                </div>
                <div class="form-group" style="flex: 0 0 100px;">
                    <label class="form-label text-small">起始座位</label>
                    <input type="number" class="form-input seat-start-input" value="${row.startSeat}" min="1" placeholder="起始号">
                </div>
                <div class="form-group" style="flex: 0 0 100px;">
                    <label class="form-label text-small">结束座位</label>
                    <input type="number" class="form-input seat-end-input" value="${row.endSeat}" min="1" placeholder="结束号">
                </div>
                <div class="form-group" style="flex: 0 0 80px;">
                    <label class="form-label text-small">间隔</label>
                    <input type="number" class="form-input seat-gap-input" value="${row.seatGap || 0}" min="0" placeholder="0">
                </div>
                <div style="padding-bottom: 14px;">
                    <button type="button" class="btn btn-outline btn-small" onclick="this.closest('.admin-form-row').remove()">删除</button>
                </div>
            </div>
        `;
        container.insertAdjacentHTML('beforeend', rowHtml);
    });

    // 显示弹窗
    document.getElementById('areaModal').style.display = 'flex';
}

/**
 * 删除区域
 */
function deleteArea(index) {
    if (!confirm('确定要删除这个区域吗？')) return;
    areas.splice(index, 1);
    renderAreas();
    updateTotals();
}

/**
 * 更新总数
 */
function updateTotals() {
    const totalRows = areas.reduce((sum, area) => sum + (area.rows?.length || 0), 0);
    const totalSeats = areas.reduce((sum, area) => sum + (area.totalSeats || 0), 0);

    setFormValue('totalRows', totalRows);
    setFormValue('totalSeats', totalSeats);
}

/**
 * 表单提交
 */
async function handleFormSubmit(e) {
    e.preventDefault();

    // 禁用提交按钮
    const submitBtn = document.getElementById('submitBtn');
    const originalText = submitBtn ? submitBtn.textContent : '';
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = '保存中...';
    }

    try {
        const formData = new FormData(e.target);

        // 验证区域配置
        if (areas.length === 0) {
            alert('请至少添加一个区域');
            return;
        }

        // 构建布局数据
        const layoutData = {
            version: '1.0',
            areas: areas.map(area => ({
                areaCode: area.areaCode,
                areaName: area.areaName,
                tierId: null,
                price: area.price,
                color: area.color,
                rows: area.rows.map(row => ({
                    rowNum: row.rowNum,
                    rowLabel: row.rowLabel,
                    startSeat: row.startSeat,
                    endSeat: row.endSeat,
                    seatGap: row.seatGap
                }))
            })),
            stage: {
                type: 'standard',
                position: 'top'
            }
        };

        // 计算总行数和总座位数
        const totalRows = areas.reduce((sum, area) => sum + (area.rows?.length || 0), 0);
        const totalSeats = areas.reduce((sum, area) => sum + (area.totalSeats || 0), 0);

        // 构建完整数据对象
        const data = {
            name: formData.get('name').trim(),
            templateCode: formData.get('templateCode').trim(),
            venueId: parseInt(formData.get('venueId')),
            layoutType: parseInt(formData.get('layoutType')),
            totalRows: totalRows,
            totalSeats: totalSeats,
            layoutData: JSON.stringify(layoutData),
            status: parseInt(formData.get('status'))
        };

        // 验证必填字段
        if (!data.name) {
            alert('请输入模板名称');
            return;
        }
        if (!data.templateCode) {
            alert('请输入模板编码');
            return;
        }
        if (!data.venueId) {
            alert('请选择关联场馆');
            return;
        }

        // 调用API
        if (templateId) {
            await put(`/api/admin/seat-templates/${templateId}`, data);
            alert('更新成功');
        } else {
            const newId = await post('/api/admin/seat-templates', data);
            alert('创建成功');
        }

        // 返回列表页
        window.location.href = 'admin-seat-templates.html';
    } catch (error) {
        console.error('保存失败:', error);
        alert('保存失败: ' + error.message);
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = originalText;
        }
    }
}

/**
 * 辅助函数：设置表单值
 */
function setFormValue(name, value) {
    const element = document.querySelector(`[name="${name}"]`);
    if (element && value !== undefined && value !== null) {
        element.value = value;
    }
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

// 监听布局类型变化
document.querySelector('[name="layoutType"]')?.addEventListener('change', updateLayoutTypeSelector);
