/**
 * 淘票票客户端 - 收藏相关功能
 * 文件：client-favorites.js
 * API路径前缀：/api/client/favorites
 */

// API基础路径
const FAVORITES_BASE_URL = '/api/client/favorites';

// 全局变量
var favoritesList = [];

/**
 * 页面初始化
 */
document.addEventListener('DOMContentLoaded', function() {
    console.log('client-favorites.js 初始化');
    updateUserInfo();
    loadFavorites();
});

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
 * 加载收藏列表
 */
function loadFavorites() {
    console.log('加载收藏列表');

    var grid = document.getElementById('favoritesGrid');
    var loadingState = document.getElementById('loadingState');
    var emptyState = document.getElementById('emptyState');

    if (!grid) return;

    // 显示加载状态
    if (loadingState) {
        loadingState.style.display = 'flex';
    }
    if (emptyState) {
        emptyState.style.display = 'none';
    }

    // 调用API获取收藏列表
    clientGet(FAVORITES_BASE_URL)
        .then(function(result) {
            console.log('收藏列表响应:', result);

            // 隐藏加载状态
            if (loadingState) {
                loadingState.style.display = 'none';
            }

            // 获取收藏列表
            favoritesList = result.list || result.data || [];
            console.log('收藏数量:', favoritesList.length);

            // 渲染收藏列表
            renderFavorites(favoritesList);
        })
        .catch(function(error) {
            console.error('加载收藏失败:', error);

            if (loadingState) {
                loadingState.style.display = 'none';
            }

            // 显示空状态
            if (emptyState) {
                emptyState.style.display = 'block';
                var emptyText = emptyState.querySelector('.empty-state-text');
                if (emptyText) {
                    emptyText.textContent = '加载失败: ' + error.message;
                }
            }
        });
}

/**
 * 渲染收藏列表
 */
function renderFavorites(favorites) {
    console.log('渲染收藏列表, 数量:', favorites.length);

    var grid = document.getElementById('favoritesGrid');
    if (!grid) return;

    // 清空现有的收藏卡片（保留状态元素）
    var cards = grid.querySelectorAll('.event-card');
    for (var i = 0; i < cards.length; i++) {
        cards[i].remove();
    }

    var emptyState = document.getElementById('emptyState');

    // 如果没有收藏，显示空状态
    if (favorites.length === 0) {
        if (emptyState) {
            emptyState.style.display = 'block';
            var emptyText = emptyState.querySelector('.empty-state-text');
            if (emptyText) {
                emptyText.textContent = '还没有收藏任何演出';
            }
        }
        return;
    }

    // 隐藏空状态
    if (emptyState) {
        emptyState.style.display = 'none';
    }

    // 渲染每个收藏项
    for (var i = 0; i < favorites.length; i++) {
        var favorite = favorites[i];
        var card = createFavoriteCard(favorite);
        grid.appendChild(card);
    }
}

/**
 * 创建收藏卡片
 */
function createFavoriteCard(favorite) {
    var event = favorite.event || favorite;
    var priceRange = getPriceRangeFromEvent(event);
    var typeText = getTypeText(event.type);
    var gradient = getTypeGradient(event.type);

    var card = document.createElement('div');
    card.className = 'event-card';
    card.dataset.eventId = event.id;
    card.dataset.favoriteId = favorite.id;

    // 使用封面图（如果有）或渐变色
    var coverStyle = event.coverImage
        ? 'background-image: url(' + event.coverImage + '); background-size: cover; background-position: center;'
        : 'background: ' + gradient;

    card.innerHTML =
        '<div class="event-cover" style="' + coverStyle + '; position: relative;">' +
            '<button class="fav-btn active" onclick="toggleFavorite(event, ' + event.id + ', ' + (favorite.id || 'null') + ')" title="取消收藏">❤️</button>' +
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

    // 点击卡片跳转到详情页
    card.addEventListener('click', function(e) {
        // 如果点击的是收藏按钮，不跳转
        if (e.target.closest('.fav-btn')) {
            return;
        }
        window.location.href = 'event-detail.html?id=' + event.id;
    });

    return card;
}

/**
 * 切换收藏状态
 */
function toggleFavorite(event, eventId, favoriteId) {
    event.preventDefault();
    event.stopPropagation();

    var btn = event.currentTarget;

    // 取消收藏
    if (favoriteId) {
        if (!confirm('确定要取消收藏吗？')) {
            return;
        }

        // 调用取消收藏API
        clientPost(FAVORITES_BASE_URL + '/' + favoriteId + '/cancel', {})
            .then(function() {
                console.log('取消收藏成功');
                // 重新加载列表
                loadFavorites();
            })
            .catch(function(error) {
                console.error('取消收藏失败:', error);
                alert('取消收藏失败: ' + error.message);
            });
    } else {
        // 添加收藏（如果需要）
        console.log('添加收藏, eventId:', eventId);
        addFavorite(eventId);
    }
}

/**
 * 添加收藏
 */
function addFavorite(eventId) {
    clientPost(FAVORITES_BASE_URL, { eventId: eventId })
        .then(function() {
            console.log('添加收藏成功');
            loadFavorites();
        })
        .catch(function(error) {
            console.error('添加收藏失败:', error);
            alert('添加收藏失败: ' + error.message);
        });
}

/**
 * 从演出对象获取价格区间
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

console.log('client-favorites.js 文件已加载');
