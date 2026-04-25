/**
 * 淘票票客户端 - 首页逻辑
 * 文件：client-index.js
 */

console.log('client-index.js 文件已加载');

// 全局变量
var currentPage = 1;
var pageSize = 20;
var totalPages = 0;
var currentFilter = {
    category: 'all',
    time: 'all',
    sort: 'hot'
};

// 确保 DOM 加载完成后执行
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}

function init() {
    console.log('init() 函数执行');
    updateUserInfo();
    initFilterButtons();
    loadEventList();
}

/**
 * 更新用户信息显示
 */
function updateUserInfo() {
    var userInfoDiv = document.querySelector('.user-info');
    if (!userInfoDiv) return;

    var user = getCurrentUser();
    if (user) {
        var avatar = document.createElement('div');
        avatar.style.cssText = 'width: 32px; height: 32px; border-radius: 50%; background: #e3f2fd; display: flex; align-items: center; justify-content: center;';
        avatar.textContent = user.nickname ? user.nickname.charAt(0) : '用';

        var nameSpan = document.createElement('span');
        nameSpan.textContent = user.nickname || '用户';

        userInfoDiv.innerHTML = '';
        userInfoDiv.appendChild(avatar);
        userInfoDiv.appendChild(nameSpan);
    } else {
        userInfoDiv.innerHTML = '<a href="login.html" class="btn btn-outline btn-small">登录</a>';
    }
}

/**
 * 初始化筛选按钮
 */
function initFilterButtons() {
    console.log('initFilterButtons() 函数执行');
    var buttons = document.querySelectorAll('.filter-btn');
    console.log('找到的按钮数量:', buttons.length);

    for (var i = 0; i < buttons.length; i++) {
        buttons[i].addEventListener('click', function(e) {
            e.preventDefault();
            console.log('按钮被点击:', this.textContent);

            // 获取父容器和标签
            var parent = this.parentElement;
            var label = parent.previousElementSibling;

            if (!label || !label.classList.contains('filter-label')) {
                console.log('未找到 filter-label');
                return;
            }

            var filterType = label.textContent;
            console.log('筛选类型:', filterType);

            // 移除同组其他按钮的 active 状态
            var siblings = parent.querySelectorAll('.filter-btn');
            for (var j = 0; j < siblings.length; j++) {
                siblings[j].classList.remove('active');
            }
            this.classList.add('active');

            // 更新筛选条件
            if (filterType === '分类') {
                currentFilter.category = this.getAttribute('data-category');
            } else if (filterType === '时间') {
                currentFilter.time = this.getAttribute('data-time');
            } else if (filterType === '排序') {
                currentFilter.sort = this.getAttribute('data-sort');
            }

            console.log('更新后的筛选条件:', currentFilter);

            // 重新加载数据
            currentPage = 1;
            loadEventList();
        });
    }
}

/**
 * 加载演出列表
 */
function loadEventList() {
    console.log('loadEventList() 执行, 筛选:', currentFilter);

    var loadingState = document.getElementById('loadingState');
    var emptyState = document.getElementById('emptyState');

    // 显示加载状态
    if (loadingState) {
        loadingState.style.display = 'flex';
    }
    if (emptyState) {
        emptyState.style.display = 'none';
    }

    // 构建请求参数
    var params = {
        page: currentPage,
        pageSize: pageSize
    };

    // 添加筛选条件
    if (currentFilter.category !== 'all') {
        params.type = currentFilter.category;
    }

    if (currentFilter.sort === 'new') {
        params.sortBy = 'createTime';
        params.sortOrder = 'desc';
    } else if (currentFilter.sort === 'soonest') {
        params.sortBy = 'eventStartDate';
        params.sortOrder = 'asc';
    }

    // 只显示在售状态的演出
    params.status = 'on_sale';

    console.log('请求参数:', params);

    // 使用封装好的API函数
    if (typeof getEventList === 'function') {
        getEventList(params)
            .then(function(result) {
                console.log('API响应:', result);

                // 隐藏加载状态
                if (loadingState) {
                    loadingState.style.display = 'none';
                }

                var eventList = result.list || result.data || [];
                totalPages = Math.ceil((result.total || 0) / pageSize);

                console.log('演出数量:', eventList.length);

                // 无论是否为空，都调用renderEvents来清空旧数据
                renderEvents(eventList);

                // 如果数据为空，显示空状态提示
                if (eventList.length === 0) {
                    if (emptyState) {
                        emptyState.style.display = 'block';
                    }
                    return;
                }
            })
            .catch(function(error) {
                console.error('请求失败:', error);

                if (loadingState) {
                    loadingState.style.display = 'none';
                }
                if (emptyState) {
                    emptyState.style.display = 'block';
                    var emptyText = emptyState.querySelector('.empty-state-text');
                    if (emptyText) {
                        emptyText.textContent = '加载失败: ' + error.message;
                    }
                }
            });
    } else {
        // 如果API函数未加载，使用原生fetch
        var queryString = Object.keys(params).map(function(key) {
            return key + '=' + encodeURIComponent(params[key]);
        }).join('&');

        var url = '/api/client/events?' + queryString;
        console.log('请求 URL:', url);

        fetch(url)
            .then(function(response) {
                console.log('响应状态:', response.status);
                if (!response.ok) {
                    throw new Error('HTTP ' + response.status);
                }
                return response.json();
            })
            .then(function(data) {
                console.log('响应数据:', data);

                // 隐藏加载状态
                if (loadingState) {
                    loadingState.style.display = 'none';
                }

                // 检查返回码
                if (data.code !== 200) {
                    throw new Error(data.msg || '请求失败');
                }

                var eventList = data.data.list || [];
                totalPages = Math.ceil((data.data.total || 0) / pageSize);

                console.log('演出数量:', eventList.length);

                // 无论是否为空，都调用renderEvents来清空旧数据
                renderEvents(eventList);

                // 如果数据为空，显示空状态提示
                if (eventList.length === 0) {
                    if (emptyState) {
                        emptyState.style.display = 'block';
                    }
                    return;
                }
            })
            .catch(function(error) {
                console.error('请求失败:', error);

                if (loadingState) {
                    loadingState.style.display = 'none';
                }
                if (emptyState) {
                    emptyState.style.display = 'block';
                    var emptyText = emptyState.querySelector('.empty-state-text');
                    if (emptyText) {
                        emptyText.textContent = '加载失败: ' + error.message;
                    }
                }
            });
    }
}

/**
 * 渲染演出列表
 */
function renderEvents(events) {
    console.log('renderEvents() 执行, 数量:', events.length);

    var eventGrid = document.getElementById('eventGrid');

    // 获取现有的状态元素
    var loadingState = document.getElementById('loadingState');
    var emptyState = document.getElementById('emptyState');

    // 移除所有旧的演出卡片（保留状态元素）
    var cards = eventGrid.querySelectorAll('.event-card');
    for (var i = 0; i < cards.length; i++) {
        cards[i].remove();
    }

    // 如果状态元素不存在，创建它们
    if (!loadingState) {
        loadingState = document.createElement('div');
        loadingState.className = 'loading-state';
        loadingState.id = 'loadingState';
        loadingState.style.display = 'none';
        loadingState.innerHTML = '<div class="loading-spinner"></div><div class="loading-text">加载中...</div>';
        eventGrid.appendChild(loadingState);
    }

    if (!emptyState) {
        emptyState = document.createElement('div');
        emptyState.className = 'empty-state';
        emptyState.id = 'emptyState';
        emptyState.style.display = 'none';
        emptyState.innerHTML = '<div class="empty-state-icon">🎭</div><div class="empty-state-text">暂无演出数据</div>';
        eventGrid.appendChild(emptyState);
    }

    // 直接将演出卡片添加到 eventGrid（grid 布局会自动排列）
    for (var i = 0; i < events.length; i++) {
        var event = events[i];
        var card = createEventCard(event);
        eventGrid.appendChild(card);
    }
}

/**
 * 创建演出卡片
 */
function createEventCard(event) {
    var priceRange = getPriceRangeFromEvent(event);
    var typeText = getTypeText(event.type);
    var gradient = getTypeGradient(event.type);

    var card = document.createElement('div');
    card.className = 'event-card';

    var handleClick = function() {
        window.location.href = 'event-detail.html?id=' + event.id;
    };

    card.onclick = handleClick;

    // 使用封面图（如果有）或渐变色
    var coverStyle = event.coverImage
        ? 'background-image: url(' + event.coverImage + '); background-size: cover; background-position: center;'
        : 'background: ' + gradient;

    card.innerHTML =
        '<div class="event-cover" style="' + coverStyle + '; height: 200px;">' +
        '</div>' +
        '<div class="event-info">' +
            '<div class="event-title">' + event.name + '</div>' +
            '<div class="event-meta">' +
                '<span>' + (event.city || '全国') + '</span>' +
                '<span>|</span>' +
                '<span>' + typeText + '</span>' +
            '</div>' +
            '<div class="event-time">' + formatDate(event.eventStartDate) + '</div>' +
            '<div class="event-price">' + priceRange + '</div>' +
        '</div>';

    return card;
}

/**
 * 从演出对象获取价格区间
 * 新API中票档信息在座位模板中
 */
function getPriceRangeFromEvent(event) {
    // 如果有座位模板信息，计算价格区间
    if (event.seatTemplates && event.seatTemplates.length > 0) {
        var allPrices = [];
        for (var i = 0; i < event.seatTemplates.length; i++) {
            var template = event.seatTemplates[i];
            if (template.areas) {
                for (var j = 0; j < template.areas.length; j++) {
                    var area = template.areas[j];
                    if (area.price) {
                        allPrices.push(area.price);
                    }
                }
            }
        }

        if (allPrices.length > 0) {
            var minPrice = Math.min.apply(null, allPrices);
            var maxPrice = Math.max.apply(null, allPrices);
            return minPrice === maxPrice ? '¥' + minPrice : '¥' + minPrice + '起';
        }
    }

    // 兼容旧数据结构（如果有ticketTiers）
    if (event.ticketTiers && event.ticketTiers.length > 0) {
        var prices = [];
        for (var i = 0; i < event.ticketTiers.length; i++) {
            if (event.ticketTiers[i].price != null) {
                prices.push(event.ticketTiers[i].price);
            }
        }
        if (prices.length > 0) {
            var min = Math.min.apply(null, prices);
            var max = Math.max.apply(null, prices);
            return min === max ? '¥' + min : '¥' + min + '起';
        }
    }

    return '价格待定';
}

/**
 * 获取类型显示文本
 */
function getTypeText(type) {
    var map = {
        'concert': '演唱会',
        'theatre': '话剧歌剧',
        'exhibition': '展览休闲',
        'sports': '体育赛事',
        'music': '音乐会',
        'kids': '儿童亲子',
        'dance': '舞蹈芭蕾'
    };
    return map[type] || type || '演出';
}

/**
 * 获取类型渐变色
 */
function getTypeGradient(type) {
    var gradients = {
        'concert': 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        'theatre': 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
        'exhibition': 'linear-gradient(135deg, #30cfd0 0%, #330867 100%)',
        'sports': 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
        'music': 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
        'kids': 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
        'dance': 'linear-gradient(135deg, #a8edea 0%, #fed6e3 100%)'
    };
    return gradients[type] || 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)';
}

/**
 * 格式化日期
 */
function formatDate(dateStr) {
    if (!dateStr) return '待定';

    var date = new Date(dateStr);
    var year = date.getFullYear();
    var month = String(date.getMonth() + 1).padStart(2, '0');
    var day = String(date.getDate()).padStart(2, '0');

    return year + '-' + month + '-' + day;
}

console.log('client-index.js 初始化完成');
