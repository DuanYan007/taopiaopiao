/**
 * 场次编辑页面逻辑
 * 文件：admin-session-edit.html
 */

let sessionId = null;
let eventId = null;
let eventData = null;
let sessionData = null;
let isReadonly = false;

/**
 * 页面初始化
 */
window.addEventListener('DOMContentLoaded', async () => {
    // 获取URL参数
    const params = new URLSearchParams(window.location.search);
    sessionId = params.get('id');
    eventId = params.get('eventId');
    isReadonly = params.get('readonly') === 'true';

    // 修改标题
    const title = document.querySelector('.admin-header-title');
    title.textContent = sessionId ? (isReadonly ? '查看场次' : '编辑场次') : '新建场次';

    // 加载演出列表（用于选择）
    await loadEvents();

    // 加载座位模板列表
    await loadSeatTemplates();

    // 如果是只读模式，禁用表单
    if (isReadonly) {
        disableForm();
        const submitBtn = document.querySelector('#submitBtn');
        if (submitBtn) submitBtn.style.display = 'none';
    }

    // 如果有sessionId（编辑或查看模式），加载场次数据
    if (sessionId) {
        await loadSessionData();
    } else {
        // 新建模式：初始化默认值
        initializeDefaultValues();

        // 如果URL中有eventId，自动选择该演出
        if (eventId) {
            const eventSelect = document.getElementById('eventSelect');
            if (eventSelect) {
                eventSelect.value = eventId;
                // 触发加载演出数据
                await loadEventData();
            }
        }
    }

    // 绑定取消按钮
    const cancelBtn = document.getElementById('cancelBtn');
    if (cancelBtn) {
        cancelBtn.addEventListener('click', () => {
            window.location.href = 'admin-sessions.html';
        });
    }

    // 绑定表单提交
    const sessionForm = document.getElementById('sessionForm');
    if (sessionForm) {
        sessionForm.addEventListener('submit', handleFormSubmit);
    }

    // 绑定座位模板选择变更
    const seatTemplateSelect = document.getElementById('seatTemplateId');
    if (seatTemplateSelect) {
        seatTemplateSelect.addEventListener('change', handleSeatTemplateChange);
    }
});

/**
 * 加载演出列表
 */
async function loadEvents() {
    try {
        const result = await get('/api/admin/events', { pageSize: 1000 });
        const eventList = result.list || result.data || [];

        console.log('演出列表数据:', eventList);

        const eventSelect = document.getElementById('eventSelect');
        if (!eventSelect) return;

        // 清空现有选项
        eventSelect.innerHTML = '<option value="">请选择演出</option>';

        // 添加演出选项
        eventList.forEach(event => {
            const option = document.createElement('option');
            option.value = event.id;
            option.textContent = event.name;
            option.dataset.type = event.type || '';
            option.dataset.city = event.city || '';
            eventSelect.appendChild(option);
        });

        // 只在非只读模式下监听演出选择变更
        if (!isReadonly) {
            // 监听演出选择变更
            eventSelect.addEventListener('change', async () => {
                const selectedEventId = eventSelect.value;
                if (selectedEventId) {
                    eventId = selectedEventId;
                    await loadEventData();
                } else {
                    // 清空演出信息
                    document.getElementById('eventDetails').style.display = 'none';
                }
            });
        }
    } catch (error) {
        console.error('加载演出列表失败:', error);
    }
}

/**
 * 加载座位模板列表
 */
async function loadSeatTemplates() {
    try {
        const result = await get('/api/admin/seat-templates', { pageSize: 1000, status: 1 });
        const templateList = result.records || result.list || result.data || [];

        console.log('座位模板列表数据:', templateList);

        const seatTemplateSelect = document.getElementById('seatTemplateId');
        if (!seatTemplateSelect) return;

        const currentValue = seatTemplateSelect.value;
        seatTemplateSelect.innerHTML = '<option value="">请选择座位模板</option>';

        templateList.forEach(template => {
            const option = document.createElement('option');
            option.value = template.id;
            option.textContent = template.name;
            option.dataset.seats = template.totalSeats || 0;
            option.dataset.venueName = template.venueName || '';
            seatTemplateSelect.appendChild(option);
        });

        if (currentValue) {
            seatTemplateSelect.value = currentValue;
            handleSeatTemplateChange();
        }
    } catch (error) {
        console.error('加载座位模板列表失败:', error);
    }
}

/**
 * 加载演出数据
 */
async function loadEventData() {
    try {
        if (!eventId) {
            console.warn('演出ID为空，跳过加载演出数据');
            return;
        }

        eventData = await get(`/api/admin/events/${eventId}`);
        console.log('演出数据:', eventData);

        // 设置隐藏字段
        document.getElementById('eventId').value = eventId;

        // 更新演出信息展示
        updateEventInfoDisplay(eventData);
    } catch (error) {
        console.error('加载演出数据失败:', error);
        alert('加载演出数据失败: ' + error.msg);
    }
}

/**
 * 加载场次数据（编辑模式）
 */
async function loadSessionData() {
    try {
        sessionData = await get(`/api/admin/sessions/${sessionId}`);
        console.log('场次数据:', sessionData);

        // 设置演出ID
        eventId = sessionData.eventId;
        document.getElementById('eventId').value = eventId;

        // 设置演出下拉框的值
        const eventSelect = document.getElementById('eventSelect');
        if (eventSelect) {
            eventSelect.value = eventId;
        }

        // 加载演出数据
        await loadEventData();

        // 回填基本信息
        setFormValue('sessionName', sessionData.sessionName);

        // 回填时间信息
        setFormValue('startTime', formatDateTimeLocal(sessionData.startTime));
        setFormValue('endTime', formatDateTimeLocal(sessionData.endTime));

        // 回填地址
        setFormValue('address', sessionData.address);

        // 回填座位模板
        setFormValue('seatTemplateId', sessionData.seatTemplateId);
        handleSeatTemplateChange();

        // 回填选座方式
        const metadata = sessionData.metadata || {};
        if (metadata.seatSelectionMode) {
            setFormValue('seatSelectionMode', metadata.seatSelectionMode);
        }

        // 回填销售设置
        setFormValue('saleStartTime', formatDateTimeLocal(metadata.saleStartTime));
        setFormValue('saleEndTime', formatDateTimeLocal(metadata.saleEndTime));

        // 回填特殊限制
        if (metadata.requireRealName) setFormValue('requireRealName', 'on');
        if (metadata.limitOnePerPerson) setFormValue('limitOnePerPerson', 'on');
        if (metadata.noRefund) setFormValue('noRefund', 'on');

        // 回填扩展设置
        setFormValue('duration', metadata.duration || 120);
        setFormValue('sortOrder', metadata.sortOrder || 100);
        setFormValue('remark', metadata.remark || '');

        // 回填状态
        setFormValue('status', sessionData.status);
    } catch (error) {
        console.error('加载场次数据失败:', error);
        alert('加载场次数据失败: ' + error.msg);
    }
}

/**
 * 更新演出信息展示
 */
function updateEventInfoDisplay(event) {
    const eventDetails = document.getElementById('eventDetails');
    const eventName = document.getElementById('eventName');
    const eventInfo = document.getElementById('eventInfo');

    if (!eventDetails || !eventName || !eventInfo) return;

    // 显示演出详情区域
    eventDetails.style.display = 'block';

    // 更新演出名称
    eventName.textContent = event.name;

    // 更新演出信息
    const typeMap = {
        concert: '演唱会',
        theatre: '话剧歌剧',
        exhibition: '展览休闲',
        sports: '体育',
        kids: '儿童亲子',
        music: '音乐会',
        dance: '舞蹈芭蕾'
    };

    eventInfo.innerHTML = `
        <span>${typeMap[event.type] || event.type || '-'}</span>
        <span style="margin: 0 8px; color: #ddd;">|</span>
        <span>${event.city || '-'}</span>
        <span style="margin: 0 8px; color: #ddd;">|</span>
        <a href="admin-event-edit.html?id=${event.id}" target="_blank" style="color: #1976d2;">查看演出详情 →</a>
    `;
}

/**
 * 座位模板选择变更处理
 */
function handleSeatTemplateChange() {
    const seatTemplateSelect = document.getElementById('seatTemplateId');
    const selectedOption = seatTemplateSelect.selectedOptions[0];
    const seatTemplateInfo = document.getElementById('seatTemplateInfo');
    const templateName = document.getElementById('templateName');
    const totalSeats = document.getElementById('totalSeats');

    if (selectedOption && selectedOption.value) {
        seatTemplateInfo.style.display = 'block';
        templateName.textContent = selectedOption.textContent;
        totalSeats.textContent = selectedOption.dataset.seats || '0';
    } else {
        seatTemplateInfo.style.display = 'none';
    }
}

/**
 * 初始化默认值
 */
function initializeDefaultValues() {
    // 设置默认演出时长为120分钟
    setFormValue('duration', '120');

    // 设置默认排序权重
    setFormValue('sortOrder', '100');

    // 设置默认选座方式
    setFormValue('seatSelectionMode', 'online');
}

/**
 * 表单提交
 */
async function handleFormSubmit(e) {
    e.preventDefault();

    const submitBtn = document.getElementById('submitBtn');
    const originalText = submitBtn ? submitBtn.textContent : '';
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = '保存中...';
    }

    try {
        const formData = new FormData(e.target);

        // 验证必填字段
        if (!eventId) {
            throw new Error('请选择所属演出');
        }

        const startTime = formData.get('startTime');
        const endTime = formData.get('endTime');
        const seatTemplateId = formData.get('seatTemplateId');

        if (!startTime || !endTime) {
            throw new Error('请选择场次开始时间和结束时间');
        }

        if (!seatTemplateId) {
            throw new Error('请选择座位模板');
        }

        // 构建metadata
        const metadata = {
            duration: formData.get('duration') ? parseInt(formData.get('duration')) : 120,
            saleStartTime: formatDateTimeForAPI(formData.get('saleStartTime')),
            saleEndTime: formatDateTimeForAPI(formData.get('saleEndTime')),
            seatSelectionMode: formData.get('seatSelectionMode') || 'online',
            requireRealName: formData.get('requireRealName') === 'on',
            limitOnePerPerson: formData.get('limitOnePerPerson') === 'on',
            noRefund: formData.get('noRefund') === 'on',
            sortOrder: formData.get('sortOrder') ? parseInt(formData.get('sortOrder')) : 100,
            remark: formData.get('remark')?.trim() || ''
        };

        // 构建完整数据对象
        const data = {
            eventId: parseInt(eventId),
            sessionName: formData.get('sessionName')?.trim(),
            startTime: formatDateTimeForAPI(startTime),
            endTime: formatDateTimeForAPI(endTime),
            address: formData.get('address')?.trim() || '',
            seatTemplateId: parseInt(seatTemplateId),
            status: formData.get('status') || 'not_started',
            metadata: metadata
        };

        console.log('提交数据:', data);

        // 调用API
        if (sessionId) {
            await put(`/api/admin/sessions/${sessionId}`, data);
            alert('更新成功');
        } else {
            await post('/api/admin/sessions', data);
            alert('创建成功');
        }

        // 返回列表页
        window.location.href = 'admin-sessions.html';
    } catch (error) {
        console.error('保存失败:', error);
        alert('保存失败: ' + error.msg);
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = originalText;
        }
    }
}

/**
 * 禁用表单（查看模式）
 */
function disableForm() {
    document.querySelectorAll('#sessionForm input, #sessionForm select, #sessionForm textarea, #sessionForm button').forEach(el => {
        if (el.type !== 'hidden' && el.id !== 'cancelBtn') {
            el.disabled = true;
        }
    });
}

/**
 * 辅助函数：设置表单值
 */
function setFormValue(name, value) {
    const element = document.querySelector(`[name="${name}"]`);
    if (element && value !== undefined && value !== null) {
        if (element.type === 'radio' || element.type === 'checkbox') {
            element.checked = true;
        } else {
            element.value = value;
        }
    }
}

/**
 * 辅助函数：格式化日期时间为 datetime-local 格式
 */
function formatDateTimeLocal(dateString) {
    if (!dateString) return '';
    const date = new Date(dateString);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
}

/**
 * 辅助函数：格式化datetime-local值为API需要的格式 (ISO 8601)
 */
function formatDateTimeForAPI(dateTimeLocalString) {
    if (!dateTimeLocalString) return null;
    // datetime-local格式: 2025-02-01T10:00
    // API需要格式: 2025-02-01T10:00:00
    return dateTimeLocalString + ':00';
}
