// Обработчик для переключения вкладок
document.addEventListener('DOMContentLoaded', function() {
    console.log('Navigation script loaded'); // Отладочное сообщение

    const DOM = {
        elements: {
            sidebar: document.querySelector('.sidebar'),
            mainContent: document.querySelector('.main-content'),
            sidebarToggle: document.querySelector('.sidebar-toggle'),
            menuItems: document.querySelectorAll('.menu-item'),
            tabContents: document.querySelectorAll('.tab-content')
        }
    };

    // Функция для переключения вкладок
    function switchTab(tabId) {
        // Сначала скрываем все вкладки
        DOM.elements.tabContents.forEach(content => {
            content.style.display = 'none';
            content.classList.remove('active');
        });

        // Затем показываем нужную вкладку
        const tabContent = document.getElementById(tabId);
        if (tabContent) {
            tabContent.style.display = 'block';
            tabContent.classList.add('active');
        }

        // Обновляем активный пункт меню
        DOM.elements.menuItems.forEach(item => {
            item.classList.remove('active');
            if (item.getAttribute('data-tab') === tabId) {
                item.classList.add('active');
            }
        });

        // Обновляем хэш в URL
        window.location.hash = tabId;
    }

    function initializeNavigation() {
        // Обработчики для пунктов меню
        DOM.elements.menuItems.forEach(item => {
            item.addEventListener('click', function(e) {
                e.preventDefault();
                const tabId = this.getAttribute('data-tab');
                switchTab(tabId);
            });
        });

        // Обработчик для кнопки сворачивания меню
        DOM.elements.sidebarToggle.addEventListener('click', function() {
            console.log('Sidebar toggle clicked'); // Отладочное сообщение
            DOM.elements.sidebar.classList.toggle('collapsed');
            DOM.elements.mainContent.classList.toggle('expanded');
            
            const icon = this.querySelector('i');
            icon.classList.toggle('fa-chevron-left');
            icon.classList.toggle('fa-chevron-right');
        });

        // Инициализация начальной вкладки
        const hash = window.location.hash.slice(1);
        if (hash && document.getElementById(hash)) {
            switchTab(hash);
        } else {
            const firstTab = document.querySelector('.menu-item');
            if (firstTab) {
                const tabId = firstTab.getAttribute('data-tab');
                switchTab(tabId);
            }
        }
    }

    initializeNavigation();

    // Обработчик изменения хэша в URL
    window.addEventListener('hashchange', function() {
        const hash = window.location.hash.slice(1);
        console.log('Hash changed to:', hash); // Отладочное сообщение
        if (hash && document.getElementById(hash)) {
            switchTab(hash);
        }
    });
}); 