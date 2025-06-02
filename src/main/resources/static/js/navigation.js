// Обработчик для переключения вкладок
document.addEventListener('DOMContentLoaded', function() {
    console.log('Navigation script loaded'); // Отладочное сообщение

    const DOM = {
        elements: {
            sidebar: document.querySelector('.sidebar'),
            mainContent: document.querySelector('.main-content'),
            sidebarToggle: document.querySelector('.sidebar-toggle'),
            menuItems: document.querySelectorAll('.menu-item')
        }
    };

    // Функция для переключения вкладок
    function switchTab(tabId) {
        console.log('Switching to tab:', tabId); // Отладочное сообщение

        const menuItem = document.querySelector(`[data-tab="${tabId}"]`);
        const tabContent = document.getElementById(tabId);
        
        if (!menuItem || !tabContent) return;
        
        DOM.elements.menuItems.forEach(item => {
            item.classList.remove('active');
        });
        
        document.querySelectorAll('.tab-content').forEach(content => {
            content.classList.remove('active');
        });
        
        menuItem.classList.add('active');
        tabContent.classList.add('active');
        
        window.location.hash = tabId;
    }

    function initializeNavigation() {
        DOM.elements.menuItems.forEach(item => {
            item.addEventListener('click', function() {
                const tabId = this.getAttribute('data-tab');
                switchTab(tabId);
            });
        });
        
        DOM.elements.sidebarToggle.addEventListener('click', function() {
            console.log('Sidebar toggle clicked'); // Отладочное сообщение
            DOM.elements.sidebar.classList.toggle('collapsed');
            DOM.elements.mainContent.classList.toggle('expanded');
            
            const icon = this.querySelector('i');
            icon.classList.toggle('fa-chevron-left');
            icon.classList.toggle('fa-chevron-right');
        });
        
        const hash = window.location.hash.slice(1);
        if (hash) {
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
        if (hash) {
            switchTab(hash);
        }
    });
}); 