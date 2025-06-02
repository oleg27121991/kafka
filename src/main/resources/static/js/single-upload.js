// Константы для DOM-элементов
const SINGLE_UPLOAD_DOM = {
    elements: {}
};

// Инициализация DOM элементов
function initializeDOMElements() {
    const elements = {
        fileInput: document.getElementById('fileInput'),
        selectedFileName: document.getElementById('selectedFileName'),
        startBtn: document.getElementById('startBtn'),
        processingSpinner: document.getElementById('processingSpinner'),
        logLevelPattern: document.getElementById('logLevelPattern'),
        batchSize: document.getElementById('batchSize'),
        totalLines: document.getElementById('totalLines'),
        processedLines: document.getElementById('processedLines'),
        processingSpeed: document.getElementById('processingSpeed'),
        progressBar: document.getElementById('progressBar'),
        partitionsStats: document.getElementById('partitionsStats'),
        clearFileBtn: document.getElementById('clearFileBtn'),
        kafkaBootstrapServers: document.getElementById('kafkaBootstrapServers'),
        kafkaTopic: document.getElementById('kafkaTopic'),
        kafkaUsername: document.getElementById('kafkaUsername'),
        kafkaPassword: document.getElementById('kafkaPassword')
    };

    // Проверяем наличие всех элементов
    for (const [key, element] of Object.entries(elements)) {
        if (!element) {
            console.error(`Элемент ${key} не найден на странице`);
        }
    }

    SINGLE_UPLOAD_DOM.elements = elements;
}

// Инициализируем элементы при загрузке страницы
document.addEventListener('DOMContentLoaded', initializeDOMElements);

// Утилитные функции
const singleUploadUtils = {
    formatNumber: (num, decimals = 2) => num ? num.toFixed(decimals) : '0.00',
    formatBytes: (bytes) => {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    },
    showMessage: (message, type = 'info') => {
        const modal = document.getElementById('messageModal');
        const messageText = document.getElementById('modalMessage');
        messageText.textContent = message;
        messageText.className = type;
        modal.style.display = 'block';
    },
    hideMessage: () => {
        document.getElementById('messageModal').style.display = 'none';
    },
    updateMetrics: (metrics) => {
        if (SINGLE_UPLOAD_DOM.elements.totalLines) {
            SINGLE_UPLOAD_DOM.elements.totalLines.textContent = metrics.totalLines.toLocaleString();
        }
        if (SINGLE_UPLOAD_DOM.elements.processedLines) {
            SINGLE_UPLOAD_DOM.elements.processedLines.textContent = metrics.processedLines.toLocaleString();
        }
        if (SINGLE_UPLOAD_DOM.elements.processingSpeed) {
            SINGLE_UPLOAD_DOM.elements.processingSpeed.textContent = `${singleUploadUtils.formatNumber(metrics.speed)} строк/сек`;
        }
        if (SINGLE_UPLOAD_DOM.elements.progressBar) {
            SINGLE_UPLOAD_DOM.elements.progressBar.style.width = `${metrics.progress}%`;
        }
        
        // Обновляем статистику по партициям
        if (SINGLE_UPLOAD_DOM.elements.partitionsStats) {
            const tbody = SINGLE_UPLOAD_DOM.elements.partitionsStats;
            tbody.innerHTML = '';
            
            Object.entries(metrics.partitions).forEach(([partition, stats]) => {
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td>${partition}</td>
                    <td>${stats.lines.toLocaleString()}</td>
                    <td>${singleUploadUtils.formatBytes(stats.bytes)}</td>
                `;
                tbody.appendChild(row);
            });
        }
    }
};

let selectedFile = null;
let processingInterval = null;

// Обработчики событий
function initializeEventListeners() {
    if (SINGLE_UPLOAD_DOM.elements.fileInput) {
        SINGLE_UPLOAD_DOM.elements.fileInput.addEventListener('change', handleFileSelect);
    }
    if (SINGLE_UPLOAD_DOM.elements.clearFileBtn) {
        SINGLE_UPLOAD_DOM.elements.clearFileBtn.addEventListener('click', clearFile);
    }
    if (SINGLE_UPLOAD_DOM.elements.startBtn) {
        SINGLE_UPLOAD_DOM.elements.startBtn.addEventListener('click', startProcessing);
    }
}

// Инициализируем обработчики событий
document.addEventListener('DOMContentLoaded', initializeEventListeners);

// Функции для работы с файлами
function handleFileSelect(event) {
    console.log('handleFileSelect вызван');
    const file = event.target.files[0];
    console.log('Выбранный файл:', file);
    if (!file) {
        console.log('Файл не выбран');
        return;
    }

    selectedFile = file;
    console.log('Имя файла:', file.name);
    console.log('Размер файла:', file.size);
    console.log('Тип файла:', file.type);

    if (SINGLE_UPLOAD_DOM.elements.selectedFileName) {
        SINGLE_UPLOAD_DOM.elements.selectedFileName.textContent = file.name;
        console.log('Имя файла установлено в DOM');
    }
    if (SINGLE_UPLOAD_DOM.elements.startBtn) {
        SINGLE_UPLOAD_DOM.elements.startBtn.disabled = false;
        console.log('Кнопка старта активирована');
    }
    if (SINGLE_UPLOAD_DOM.elements.clearFileBtn) {
        SINGLE_UPLOAD_DOM.elements.clearFileBtn.style.display = 'inline-block';
        console.log('Кнопка очистки показана');
    }
}

function clearFile() {
    selectedFile = null;
    if (SINGLE_UPLOAD_DOM.elements.fileInput) {
        SINGLE_UPLOAD_DOM.elements.fileInput.value = '';
    }
    if (SINGLE_UPLOAD_DOM.elements.selectedFileName) {
        SINGLE_UPLOAD_DOM.elements.selectedFileName.textContent = '';
    }
    if (SINGLE_UPLOAD_DOM.elements.startBtn) {
        SINGLE_UPLOAD_DOM.elements.startBtn.disabled = true;
    }
    if (SINGLE_UPLOAD_DOM.elements.clearFileBtn) {
        SINGLE_UPLOAD_DOM.elements.clearFileBtn.style.display = 'none';
    }
}

// Функции для обработки файла
async function startProcessing() {
    console.log('startProcessing вызван');
    if (!selectedFile) {
        console.log('Файл не выбран');
        singleUploadUtils.showMessage('Пожалуйста, выберите файл для загрузки', 'error');
        return;
    }

    // Проверяем настройки подключения к Kafka
    const bootstrapServers = document.getElementById('kafkaBootstrapServers')?.value;
    const topic = document.getElementById('kafkaTopic')?.value;

    if (!bootstrapServers) {
        console.log('Не указаны Bootstrap Servers');
        singleUploadUtils.showMessage('Пожалуйста, укажите Bootstrap Servers в настройках подключения', 'error');
        return;
    }

    if (!topic) {
        console.log('Не выбран топик');
        singleUploadUtils.showMessage('Пожалуйста, выберите топик в настройках подключения', 'error');
        return;
    }

    console.log('Подготовка данных для отправки');
    const formData = new FormData();
    formData.append('file', selectedFile);
    formData.append('bootstrapServers', bootstrapServers);
    formData.append('topic', topic);
    
    // Добавляем опциональные параметры
    const username = document.getElementById('kafkaUsername')?.value;
    const password = document.getElementById('kafkaPassword')?.value;
    
    if (username) {
        formData.append('username', username);
    }
    if (password) {
        formData.append('password', password);
    }

    if (SINGLE_UPLOAD_DOM.elements.logLevelPattern) {
        formData.append('logLevelPattern', SINGLE_UPLOAD_DOM.elements.logLevelPattern.value);
        console.log('Шаблон логирования:', SINGLE_UPLOAD_DOM.elements.logLevelPattern.value);
    }
    if (SINGLE_UPLOAD_DOM.elements.batchSize) {
        formData.append('batchSize', SINGLE_UPLOAD_DOM.elements.batchSize.value);
        console.log('Размер батча:', SINGLE_UPLOAD_DOM.elements.batchSize.value);
    }

    // Блокируем элементы управления
    console.log('Блокировка элементов управления');
    if (SINGLE_UPLOAD_DOM.elements.fileInput) {
        SINGLE_UPLOAD_DOM.elements.fileInput.disabled = true;
    }
    if (SINGLE_UPLOAD_DOM.elements.clearFileBtn) {
        SINGLE_UPLOAD_DOM.elements.clearFileBtn.disabled = true;
    }
    if (SINGLE_UPLOAD_DOM.elements.startBtn) {
        SINGLE_UPLOAD_DOM.elements.startBtn.disabled = true;
    }
    if (SINGLE_UPLOAD_DOM.elements.processingSpinner) {
        SINGLE_UPLOAD_DOM.elements.processingSpinner.classList.remove('d-none');
    }

    try {
        console.log('Отправка файла на сервер');
        // Отправляем файл на сервер для обработки
        const response = await fetch('/api/upload/process', {
            method: 'POST',
            body: formData
        });

        console.log('Получен ответ от сервера:', response.status);
        if (!response.ok) {
            const errorData = await response.json().catch(() => null);
            throw new Error(errorData?.message || 'Ошибка при обработке файла');
        }

        const result = await response.json();
        console.log('Результат обработки:', result);
        
        if (result.success) {
            console.log('Запуск обновления метрик для processId:', result.processId);
            // Запускаем обновление метрик
            startMetricsUpdates(result.processId);
        } else {
            throw new Error(result.message || 'Ошибка при обработке файла');
        }
    } catch (error) {
        console.error('Ошибка при обработке файла:', error);
        singleUploadUtils.showMessage(error.message, 'error');
        resetUI();
    }
}

function startMetricsUpdates(processId) {
    console.log('startMetricsUpdates вызван для processId:', processId);
    if (processingInterval) {
        console.log('Очистка предыдущего интервала');
        clearInterval(processingInterval);
    }

    processingInterval = setInterval(async () => {
        try {
            console.log('Запрос метрик для processId:', processId);
            const response = await fetch(`/api/upload/metrics/${processId}`);
            if (!response.ok) {
                throw new Error('Ошибка при получении метрик');
            }

            const metrics = await response.json();
            console.log('Получены метрики:', metrics);
            singleUploadUtils.updateMetrics(metrics);

            if (metrics.status === 'completed') {
                console.log('Обработка завершена');
                clearInterval(processingInterval);
                singleUploadUtils.showMessage('Файл успешно обработан и загружен в Kafka', 'success');
                resetUI();
            } else if (metrics.status === 'failed') {
                console.log('Обработка завершилась с ошибкой:', metrics.error);
                clearInterval(processingInterval);
                singleUploadUtils.showMessage('Ошибка при обработке файла: ' + metrics.error, 'error');
                resetUI();
            }
        } catch (error) {
            console.error('Ошибка при обновлении метрик:', error);
            clearInterval(processingInterval);
            singleUploadUtils.showMessage('Ошибка при обновлении метрик: ' + error.message, 'error');
            resetUI();
        }
    }, 1000);
}

function resetUI() {
    if (SINGLE_UPLOAD_DOM.elements.fileInput) {
        SINGLE_UPLOAD_DOM.elements.fileInput.disabled = false;
    }
    if (SINGLE_UPLOAD_DOM.elements.clearFileBtn) {
        SINGLE_UPLOAD_DOM.elements.clearFileBtn.disabled = false;
    }
    if (SINGLE_UPLOAD_DOM.elements.startBtn) {
        SINGLE_UPLOAD_DOM.elements.startBtn.disabled = false;
    }
    if (SINGLE_UPLOAD_DOM.elements.processingSpinner) {
        SINGLE_UPLOAD_DOM.elements.processingSpinner.classList.add('d-none');
    }
    
    if (processingInterval) {
        clearInterval(processingInterval);
        processingInterval = null;
    }
}

// Закрытие модального окна
document.querySelector('.close')?.addEventListener('click', singleUploadUtils.hideMessage);
window.addEventListener('click', (event) => {
    const modal = document.getElementById('messageModal');
    if (event.target === modal) {
        singleUploadUtils.hideMessage();
    }
}); 