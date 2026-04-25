/**
 * 演出编辑页面逻辑
 * 文件：admin-event-edit.html
 */

let eventId = null;
let isReadonly = false;
let coverImageUploading = false;

/**
 * 页面初始化
 */
window.addEventListener('DOMContentLoaded', async () => {
    // 获取URL参数
    const params = new URLSearchParams(window.location.search);
    eventId = params.get('id');
    isReadonly = params.get('readonly') === 'true';

    // 修改标题
    const title = document.querySelector('.admin-header-title');
    title.textContent = eventId ? (isReadonly ? '查看演出' : '编辑演出') : '新建演出';

    // 初始化文件上传
    initImageUpload();

    // 如果是只读模式，禁用表单
    if (isReadonly) {
        disableForm();
        // 隐藏保存按钮
        const submitBtn = document.querySelector('#submitBtn');
        if (submitBtn) submitBtn.style.display = 'none';
    }

    // 如果有ID（编辑或查看模式），加载演出数据
    if (eventId) {
        await loadEventData();
    }

    // 绑定取消按钮
    const cancelBtn = document.getElementById('cancelBtn');
    if (cancelBtn) {
        cancelBtn.addEventListener('click', () => {
            window.location.href = 'admin-events.html';
        });
    }

    // 绑定表单提交
    const eventForm = document.getElementById('eventForm');
    if (eventForm) {
        eventForm.addEventListener('submit', handleFormSubmit);
    }
});

/**
 * 初始化图片上传
 */
function initImageUpload() {
    const fileInput = document.getElementById('coverImageFile');
    const preview = document.getElementById('coverImagePreview');
    const placeholder = document.getElementById('coverPlaceholder');
    const img = document.getElementById('coverImageImg');
    const removeBtn = document.getElementById('removeCoverBtn');

    // 点击预览区域触发文件选择
    preview.addEventListener('click', () => {
        if (!isReadonly) {
            fileInput.click();
        }
    });

    // 文件选择变化
    fileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file) {
            // 验证文件类型
            if (!file.type.match('image.*')) {
                alert('请选择图片文件');
                return;
            }
            // 验证文件大小（5MB）
            if (file.size > 5 * 1024 * 1024) {
                alert('图片大小不能超过5MB');
                return;
            }
            // 预览本地图片
            const reader = new FileReader();
            reader.onload = (e) => {
                img.src = e.target.result;
                img.style.display = 'block';
                placeholder.style.display = 'none';
            };
            reader.readAsDataURL(file);
            // 上传图片
            uploadImage(file);
        }
    });

    // 拖拽上传
    preview.addEventListener('dragover', (e) => {
        e.preventDefault();
        preview.style.borderColor = '#1976d2';
    });

    preview.addEventListener('dragleave', (e) => {
        e.preventDefault();
        preview.style.borderColor = '';
    });

    preview.addEventListener('drop', (e) => {
        e.preventDefault();
        preview.style.borderColor = '';
        if (isReadonly) return;

        const file = e.dataTransfer.files[0];
        if (file && file.type.match('image.*')) {
            if (file.size > 5 * 1024 * 1024) {
                alert('图片大小不能超过5MB');
                return;
            }
            // 预览本地图片
            const reader = new FileReader();
            reader.onload = (e) => {
                img.src = e.target.result;
                img.style.display = 'block';
                placeholder.style.display = 'none';
            };
            reader.readAsDataURL(file);
            // 上传图片
            uploadImage(file);
        }
    });
}

/**
 * 上传图片 - 直接使用 XHR + FormData
 */
async function uploadImage(file) {
    if (coverImageUploading) return;

    coverImageUploading = true;

    console.log('[uploadImage] 开始上传:', file.name, file.size, 'bytes');

    // 获取 token
    const token = sessionStorage.getItem('admin_token') || localStorage.getItem('admin_token');
    console.log('[uploadImage] Token:', token ? '存在' : '不存在');

    // 创建 FormData
    const formData = new FormData();
    formData.append('file', file);
    console.log('[uploadImage] FormData 已创建');

    // 使用 XHR 直接上传
    const xhr = new XMLHttpRequest();

    xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
            console.log('[uploadImage] 上传进度:', (e.loaded / e.total * 100).toFixed(1) + '%');
        }
    };

    xhr.onload = () => {
        console.log('[uploadImage] 响应状态:', xhr.status);
        console.log('[uploadImage] 响应内容:', xhr.responseText);

        if (xhr.status === 401) {
            alert('登录已过期');
            window.location.href = 'admin-login.html';
            coverImageUploading = false;
            return;
        }

        try {
            const result = JSON.parse(xhr.responseText);
            if (result.code === 200 && result.data && result.data.url) {
                document.getElementById('coverImageInput').value = result.data.url;
                console.log('[uploadImage] 上传成功:', result.data.url);
                document.getElementById('removeCoverBtn').style.display = 'inline-block';
            } else {
                throw new Error(result.msg || '上传失败');
            }
        } catch (e) {
            console.error('[uploadImage] 解析响应失败:', e);
            alert('图片上传失败: ' + (e.message || xhr.responseText));
            document.getElementById('coverImageImg').style.display = 'none';
            document.getElementById('coverPlaceholder').style.display = 'block';
        } finally {
            coverImageUploading = false;
        }
    };

    xhr.onerror = () => {
        console.error('[uploadImage] 网络错误');
        alert('图片上传失败: 网络错误');
        document.getElementById('coverImageImg').style.display = 'none';
        document.getElementById('coverPlaceholder').style.display = 'block';
        coverImageUploading = false;
    };

    xhr.ontimeout = () => {
        console.error('[uploadImage] 请求超时');
        alert('图片上传失败: 请求超时');
        document.getElementById('coverImageImg').style.display = 'none';
        document.getElementById('coverPlaceholder').style.display = 'block';
        coverImageUploading = false;
    };

    // 配置请求
    xhr.open('POST', '/api/admin/files/upload', true); // async = true
    xhr.timeout = 60000; // 60秒
    if (token) {
        xhr.setRequestHeader('Authorization', 'Bearer ' + token);
    }
    // 不设置 Content-Type，让浏览器自动处理

    console.log('[uploadImage] 即将发送 FormData，包含文件:', file.name);
    console.log('[uploadImage] FormData 内容检查:');
    for (let pair of formData.entries()) {
        console.log('  ', pair[0], '=', pair[1]);
    }

    // 发送
    xhr.send(formData);
    console.log('[uploadImage] xhr.send() 已调用');
}

/**
 * 删除封面图片
 */
function removeCoverImage() {
    document.getElementById('coverImageFile').value = '';
    document.getElementById('coverImageInput').value = '';
    document.getElementById('coverImageImg').style.display = 'none';
    document.getElementById('coverPlaceholder').style.display = 'block';
    document.getElementById('removeCoverBtn').style.display = 'none';
}

/**
 * 加载演出数据
 */
async function loadEventData() {
    try {
        const event = await get(`/api/admin/events/${eventId}`);

        // 回填基本信息
        setFormValue('name', event.name);
        setFormValue('type', event.type);
        setFormValue('artist', event.artist);
        setFormValue('city', event.city);
        setFormValue('subtitle', event.subtitle);

        // 回填时间信息
        setFormValue('eventStartDate', formatDate(event.eventStartDate));
        setFormValue('eventEndDate', formatDate(event.eventEndDate));
        setFormValue('duration', event.duration);

        // 回填预售时间
        setFormValue('saleStartTime', formatDateTimeLocal(event.saleStartTime));
        setFormValue('saleEndTime', formatDateTimeLocal(event.saleEndTime));

        // 回填演出详情 - 封面图片
        if (event.coverImage) {
            setFormValue('coverImage', event.coverImage);
            showCoverImagePreview(event.coverImage);
        }

        setFormValue('images', Array.isArray(event.images) ? event.images.join(',') : event.images);
        setFormValue('description', event.description);
        setFormValue('tips', event.tips);
        setFormValue('refundPolicy', event.refundPolicy);

        // 回填状态
        setFormValue('status', event.status);

        // 回填标签
        if (event.tags && event.tags.length > 0) {
            event.tags.forEach(tag => {
                const checkbox = document.querySelector(`[name="tags[]"][value="${tag}"]`);
                if (checkbox) checkbox.checked = true;
            });
        }
    } catch (error) {
        console.error('加载失败:', error);
        alert('加载演出数据失败: ' + error.msg);
    }
}

/**
 * 显示封面图片预览
 */
function showCoverImagePreview(url) {
    const img = document.getElementById('coverImageImg');
    const placeholder = document.getElementById('coverPlaceholder');
    const removeBtn = document.getElementById('removeCoverBtn');

    img.src = url;
    img.style.display = 'block';
    placeholder.style.display = 'none';
    removeBtn.style.display = 'inline-block';
}

/**
 * 禁用表单（查看模式）
 */
function disableForm() {
    document.querySelectorAll('#eventForm input, #eventForm select, #eventForm textarea, #eventForm button').forEach(el => {
        if (el.type !== 'hidden' && el.id !== 'cancelBtn') {
            el.disabled = true;
        }
    });
}

/**
 * 表单提交
 */
async function handleFormSubmit(e) {
    e.preventDefault();

    // 如果正在上传图片，等待上传完成
    if (coverImageUploading) {
        alert('图片正在上传中，请稍候...');
        return;
    }

    // 禁用提交按钮
    const submitBtn = document.getElementById('submitBtn');
    const originalText = submitBtn ? submitBtn.textContent : '';
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = '保存中...';
    }

    try {
        // 收集表单数据
        const formData = new FormData(e.target);

        // 收集标签
        const tags = [];
        document.querySelectorAll('[name="tags[]"]:checked').forEach(cb => {
            tags.push(cb.value);
        });

        // 构建完整的数据对象
        const data = {
            name: formData.get('name').trim(),
            type: formData.get('type'),
            artist: formData.get('artist').trim(),
            city: formData.get('city').trim(),
            subtitle: formData.get('subtitle')?.trim() || '',
            eventStartDate: formData.get('eventStartDate') || null,
            eventEndDate: formData.get('eventEndDate') || null,
            duration: formData.get('duration') ? parseInt(formData.get('duration')) : null,
            saleStartTime: formatDateTimeForAPI(formData.get('saleStartTime')),
            saleEndTime: formatDateTimeForAPI(formData.get('saleEndTime')),
            coverImage: formData.get('coverImage')?.trim() || '',
            images: formData.get('images')?.trim() || '',
            description: formData.get('description')?.trim() || '',
            tips: formData.get('tips')?.trim() || '',
            refundPolicy: formData.get('refundPolicy')?.trim() || '',
            status: formData.get('status') || 'draft',
            tags: tags
        };

        // 调用API
        if (eventId) {
            // 更新
            await put(`/api/admin/events/${eventId}`, data);
            alert('更新成功');
        } else {
            // 创建
            await post('/api/admin/events', data);
            alert('创建成功');
        }

        // 返回列表页
        window.location.href = 'admin-events.html';
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
 * 辅助函数：格式化日期为 date 类型输入框格式 (YYYY-MM-DD)
 * 处理后端返回的各种时间格式（ISO 8601、时间戳等）
 */
function formatDate(dateString) {
    if (!dateString) return '';

    let date;
    // 处理时间戳（毫秒）
    if (typeof dateString === 'number') {
        date = new Date(dateString);
    }
    // 处理 ISO 8601 字符串
    else {
        date = new Date(dateString);
    }

    // 检查日期是否有效
    if (isNaN(date.getTime())) {
        console.warn('无效的日期格式:', dateString);
        return '';
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
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
