/**
 * 场馆编辑页面逻辑
 * 文件：admin-venue-edit.html
 */

let venueId = null;
let isReadonly = false;

/**
 * 页面初始化
 */
window.addEventListener('DOMContentLoaded', async () => {
    // 获取URL参数
    const params = new URLSearchParams(window.location.search);
    venueId = params.get('id');
    isReadonly = params.get('readonly') === 'true';

    // 修改标题
    const title = document.querySelector('.admin-header-title');
    title.textContent = venueId ? (isReadonly ? '查看场馆' : '编辑场馆') : '新建场馆';

    // 如果是只读模式，禁用表单
    if (isReadonly) {
        disableForm();
        // 隐藏保存按钮
        document.getElementById('submitBtn').style.display = 'none';
    }

    // 如果有ID（编辑或查看模式），加载场馆数据
    if (venueId) {
        await loadVenueData();
    }

    // 绑定取消按钮
    document.getElementById('cancelBtn').addEventListener('click', () => {
        window.location.href = 'admin-venues.html';
    });

    // 绑定表单提交
    document.getElementById('venueForm').addEventListener('submit', handleFormSubmit);
});

/**
 * 加载场馆数据
 */
async function loadVenueData() {
    try {
        const venue = await get(`/api/admin/venues/${venueId}`);

        // 回填表单
        document.querySelector('[name="name"]').value = venue.name || '';
        document.querySelector('[name="city"]').value = venue.city || '';
        document.querySelector('[name="district"]').value = venue.district || '';
        document.querySelector('[name="address"]').value = venue.address || '';
        document.querySelector('[name="capacity"]').value = venue.capacity || '';
        document.querySelector('[name="latitude"]').value = venue.latitude || '';
        document.querySelector('[name="longitude"]').value = venue.longitude || '';
        document.querySelector('[name="images"]').value = venue.images || '';
        document.querySelector('[name="description"]').value = venue.description || '';

        // 回填设施标签
        if (venue.facilities && venue.facilities.length > 0) {
            venue.facilities.forEach(facility => {
                const checkbox = document.querySelector(`[name="facilities[]"][value="${facility}"]`);
                if (checkbox) checkbox.checked = true;
            });
        }
    } catch (error) {
        console.error('加载失败:', error);
        alert('加载场馆数据失败: ' + error.msg);
    }
}

/**
 * 禁用表单（查看模式）
 */
function disableForm() {
    document.querySelectorAll('#venueForm input, #venueForm select, #venueForm textarea').forEach(el => {
        el.disabled = true;
    });
}

/**
 * 表单提交
 */
async function handleFormSubmit(e) {
    e.preventDefault();

    // 禁用提交按钮
    const submitBtn = document.getElementById('submitBtn');
    const originalText = submitBtn.textContent;
    submitBtn.disabled = true;
    submitBtn.textContent = '保存中...';

    try {
        // 收集表单数据
        const formData = new FormData(e.target);

        // 收集设施标签
        const facilities = [];
        document.querySelectorAll('[name="facilities[]"]:checked').forEach(cb => {
            facilities.push(cb.value);
        });

        // 构建完整的数据对象（所有字段都必须发送，即使为空）
        const data = {
            name: formData.get('name').trim(),
            city: formData.get('city').trim(),
            district: formData.get('district').trim(),
            address: formData.get('address').trim(),
            capacity: formData.get('capacity') ? parseInt(formData.get('capacity')) : null,
            latitude: formData.get('latitude') ? parseFloat(formData.get('latitude')) : null,
            longitude: formData.get('longitude') ? parseFloat(formData.get('longitude')) : null,
            images: formData.get('images').trim(),
            description: formData.get('description').trim(),
            facilities: facilities
        };

        // 调用API
        if (venueId) {
            await put(`/api/admin/venues/${venueId}`, data);
            alert('更新成功');
        } else {
            await post('/api/admin/venues', data);
            alert('创建成功');
        }

        // 返回列表页
        window.location.href = 'admin-venues.html';
    } catch (error) {
        console.error('保存失败:', error);
        alert('保存失败: ' + error.msg);
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = originalText;
    }
}
