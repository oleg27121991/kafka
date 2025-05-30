// Обработчик для переключения вкладок
document.addEventListener('DOMContentLoaded', function() {
    console.log('Navigation script loaded'); // Отладочное сообщение

    // Функция для переключения вкладок
    function switchTab(tabId) {
        console.log('Switching to tab:', tabId); // Отладочное сообщение

        // Убираем активный класс у всех пунктов меню
        document.querySelectorAll('.menu-item').forEach(mi => {
            mi.classList.remove('active');
        });

        // Добавляем активный класс текущему пункту
        const menuItem = document.querySelector(`[data-tab="${tabId}"]`);
        if (menuItem) {
            menuItem.classList.add('active');
        }

        // Скрываем все вкладки
        document.querySelectorAll('.tab-content').forEach(tab => {
            tab.classList.remove('active');
        });

        // Показываем выбранную вкладку
        const tabContent = document.getElementById(tabId);
        if (tabContent) {
            tabContent.classList.add('active');
        }
    }

    // Обработчик для меню
    const menuItems = document.querySelectorAll('.menu-item');
    menuItems.forEach(item => {
        item.addEventListener('click', function(e) {
            e.preventDefault();
            const tabId = this.getAttribute('data-tab');
            console.log('Menu item clicked:', tabId); // Отладочное сообщение
            if (tabId) {
                switchTab(tabId);
                // Обновляем URL без перезагрузки страницы
                history.pushState(null, null, `#${tabId}`);
            }
        });
    });

    // Обработчик для бургер-меню
    const sidebarToggle = document.querySelector('.sidebar-toggle');
    const sidebar = document.querySelector('.sidebar');
    const mainContent = document.querySelector('.main-content');

    if (sidebarToggle && sidebar && mainContent) {
        console.log('Sidebar elements found'); // Отладочное сообщение
        sidebarToggle.addEventListener('click', function() {
            console.log('Sidebar toggle clicked'); // Отладочное сообщение
            sidebar.classList.toggle('collapsed');
            mainContent.classList.toggle('expanded');
            // Поворачиваем иконку
            const icon = this.querySelector('i');
            if (icon) {
                icon.classList.toggle('fa-chevron-left');
                icon.classList.toggle('fa-chevron-right');
            }
        });
    } else {
        console.log('Some sidebar elements not found:', { // Отладочное сообщение
            sidebarToggle: !!sidebarToggle,
            sidebar: !!sidebar,
            mainContent: !!mainContent
        });
    }

    // Обработка хэша в URL при загрузке страницы
    const hash = window.location.hash.slice(1);
    if (hash) {
        console.log('Initial hash found:', hash); // Отладочное сообщение
        switchTab(hash);
    } else {
        // Если хэша нет, активируем первую вкладку
        const firstTab = document.querySelector('.menu-item');
        if (firstTab) {
            const tabId = firstTab.getAttribute('data-tab');
            if (tabId) {
                console.log('Activating first tab:', tabId); // Отладочное сообщение
                switchTab(tabId);
            }
        }
    }

    // Обработчик изменения хэша в URL
    window.addEventListener('hashchange', function() {
        const hash = window.location.hash.slice(1);
        console.log('Hash changed to:', hash); // Отладочное сообщение
        if (hash) {
            switchTab(hash);
        }
    });
}); 