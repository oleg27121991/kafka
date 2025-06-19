// Константы для DOM-элементов
const DOM = {
    elements: {
        kafkaBootstrapServers: document.getElementById('kafkaBootstrapServers'),
        kafkaTopic: document.getElementById('kafkaTopic'),
        kafkaUsername: document.getElementById('kafkaUsername'),
        kafkaPassword: document.getElementById('kafkaPassword'),
        continuousFileInput: document.getElementById('continuousFileInput'),
        continuousSelectedFileName: document.getElementById('continuousSelectedFileName'),
        startContinuousBtn: document.getElementById('startContinuousBtn'),
        stopContinuousBtn: document.getElementById('stopContinuousBtn'),
        clearContinuousFileBtn: document.getElementById('clearContinuousFileBtn'),
        targetSpeed: document.getElementById('targetSpeed'),
        targetDataSpeed: document.getElementById('targetDataSpeed'),
        continuousModal: document.getElementById('continuousModal'),
        minimizedContinuousStatus: document.getElementById('minimizedContinuousStatus'),
        metricsHistoryList: document.getElementById('metricsHistoryList'),
        historyItemTemplate: document.getElementById('historyItemTemplate'),
        selectedFileName: document.getElementById('selectedFileName'),
        startBtn: document.getElementById('startBtn'),
        clearFileBtn: document.getElementById('clearFileBtn'),
        togglePasswordButton: document.querySelector('.toggle-password i')
    },
    buttons: {
        saveKafkaConfig: document.getElementById('saveKafkaConfig'),
        testKafkaConnection: document.getElementById('testKafkaConnection'),
        refreshTopics: document.querySelector('.refresh-topics')
    }
};

// Утилитные функции
const utils = {
    formatNumber: (num, decimals = 2) => num ? num.toFixed(decimals) : '0.00',
    formatDataSpeed: (num) => num ? num.toFixed(4) : '0.0000',
    updateElementText: (id, text) => {
        const element = document.getElementById(id);
        if (element) element.textContent = text;
    },
    disableButtons: (buttonIds) => {
        buttonIds.forEach(id => {
            const button = document.getElementById(id);
            if (button) button.disabled = true;
        });
    },
    enableButtons: (buttonIds) => {
        buttonIds.forEach(id => {
            const button = document.getElementById(id);
            if (button) button.disabled = false;
        });
    },
    getKafkaConfig: () => ({
        bootstrapServers: DOM.elements.kafkaBootstrapServers?.value,
        topic: DOM.elements.kafkaTopic?.value,
        username: DOM.elements.kafkaUsername?.value,
        password: DOM.elements.kafkaPassword?.value
    }),
    getApiUrl: (path) => {
        const baseUrl = window.location.origin;
        return `${baseUrl}${path}`;
    }
};

// Функция для показа модального окна с сообщением
function showMessage(message, type = 'info') {
    let messageContainer = document.getElementById('messageContainer');
    
    // Если контейнер не существует, создаем его
    if (!messageContainer) {
        messageContainer = document.createElement('div');
        messageContainer.id = 'messageContainer';
        messageContainer.className = 'message';
        document.body.appendChild(messageContainer);
    }

    messageContainer.textContent = message;
    messageContainer.className = 'message ' + type;
    messageContainer.style.display = 'block';

    // Скрываем сообщение через 5 секунд
    setTimeout(() => {
        if (messageContainer) {
            messageContainer.style.display = 'none';
        }
    }, 5000);
}

// Функция для закрытия модального окна с сообщением
function hideMessageModal() {
    document.getElementById('messageModal').style.display = 'none';
}

// Функции для работы с непрерывной записью
let statusUpdateInterval;
let selectedContinuousFile = null;
let dataSpeedHistory = []; // Массив для хранения истории скоростей передачи
let messagesHistory = []; // Массив для хранения истории количества сообщений

document.getElementById('continuousFileInput').addEventListener('change', function(e) {
    selectedContinuousFile = e.target.files[0];
    if (selectedContinuousFile) {
        document.getElementById('continuousSelectedFileName').textContent = selectedContinuousFile.name;
        document.getElementById('startContinuousBtn').disabled = false;
        document.getElementById('clearContinuousFileBtn').style.display = 'inline-block';
        saveContinuousState(); // Сохраняем состояние при выборе файла
    } else {
        document.getElementById('continuousSelectedFileName').textContent = '';
        document.getElementById('startContinuousBtn').disabled = true;
        document.getElementById('clearContinuousFileBtn').style.display = 'none';
        saveContinuousState(); // Сохраняем состояние при очистке файла
    }
});

// Обработчики для радио-кнопок
document.querySelectorAll('input[name="speedType"]').forEach(radio => {
    radio.addEventListener('change', function() {
        const targetSpeedInput = document.getElementById('targetSpeed');
        const targetDataSpeedInput = document.getElementById('targetDataSpeed');
        
        if (this.value === 'messages') {
            targetSpeedInput.disabled = false;
            targetDataSpeedInput.disabled = true;
        } else {
            targetSpeedInput.disabled = true;
            targetDataSpeedInput.disabled = false;
        }
        saveContinuousState(); // Сохраняем состояние при изменении типа скорости
    });
});

// Обновляем обработчики для полей ввода скорости
DOM.elements.targetSpeed.addEventListener('change', saveContinuousState);
DOM.elements.targetDataSpeed.addEventListener('change', saveContinuousState);

function clearContinuousFile() {
    selectedContinuousFile = null;
    DOM.elements.continuousFileInput.value = '';
    DOM.elements.continuousSelectedFileName.textContent = '';
    DOM.elements.startContinuousBtn.disabled = true;
    DOM.elements.clearContinuousFileBtn.style.display = 'none';
    
    DOM.elements.targetSpeed.value = '1000';
    DOM.elements.targetDataSpeed.value = '1';
    
    document.querySelector('input[name="speedType"][value="messages"]').checked = true;
    DOM.elements.targetSpeed.disabled = false;
    DOM.elements.targetDataSpeed.disabled = true;
    
    saveContinuousState(); // Сохраняем состояние при очистке
}

async function startContinuousWriting() {
    if (!selectedContinuousFile) {
        showMessage('Пожалуйста, выберите файл для записи');
        return;
    }

    showContinuousModal();
    DOM.elements.startContinuousBtn.style.display = 'none';
    DOM.elements.stopContinuousBtn.style.display = 'inline-block';
    DOM.elements.stopContinuousBtn.disabled = false;

    startStatusUpdates();

    const formData = new FormData();
    formData.append('file', selectedContinuousFile);
    
    const speedType = document.querySelector('input[name="speedType"]:checked').value;
    if (speedType === 'messages') {
        formData.append('targetSpeed', DOM.elements.targetSpeed.value);
        formData.append('targetDataSpeed', '0');
    } else {
        formData.append('targetSpeed', '0');
        formData.append('targetDataSpeed', DOM.elements.targetDataSpeed.value);
    }

    try {
        const response = await fetch(utils.getApiUrl('/api/writer/start'), {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            throw new Error('Ошибка при запуске записи');
        }

        await updateContinuousStatus();
    } catch (error) {
        console.error('Ошибка:', error);
        showMessage('Ошибка при запуске записи: ' + error.message);
        DOM.elements.startContinuousBtn.style.display = 'inline-block';
        DOM.elements.stopContinuousBtn.style.display = 'none';
        DOM.elements.startContinuousBtn.disabled = false;
        DOM.elements.stopContinuousBtn.disabled = true;
        hideContinuousModal();
        stopStatusUpdates();
    }
}

// Функции для работы с модальными окнами
function showCalculatingMetricsModal() {
    const modal = document.getElementById('calculatingMetricsModal');
    const modalTitle = modal.querySelector('.modal-title');
    
    modalTitle.innerHTML = '<div class="d-flex align-items-center"><div class="spinner-border spinner-border-sm text-primary me-2" role="status"></div>Остановка процесса...</div>';
    modal.style.display = 'block';
}

function hideCalculatingMetricsModal() {
    const modal = document.getElementById('calculatingMetricsModal');
    modal.style.display = 'none';
}

async function stopContinuousWriting() {
    try {
        const calculatingMetricsModal = document.getElementById('calculatingMetricsModal');
        if (calculatingMetricsModal) {
            calculatingMetricsModal.style.display = 'block';
        }
        
        // Обновляем UI перед отправкой запроса
        if (DOM.elements.startContinuousBtn) {
            DOM.elements.startContinuousBtn.style.display = 'inline-block';
            DOM.elements.startContinuousBtn.disabled = true;
        }
        if (DOM.elements.stopContinuousBtn) {
            DOM.elements.stopContinuousBtn.style.display = 'none';
            DOM.elements.stopContinuousBtn.disabled = true;
        }
        
        const response = await fetch(utils.getApiUrl('/api/writer/stop'), {
            method: 'POST'
        });

        if (!response.ok) {
            throw new Error('Ошибка при остановке записи');
        }

        const metrics = await response.json();
        console.log('Получены метрики:', metrics);
        
        if (metrics.currentDataSpeed) {
            dataSpeedHistory.push(metrics.currentDataSpeed);
        }
        if (metrics.currentSpeed) {
            messagesHistory.push(metrics.currentSpeed);
        }
        
        if (dataSpeedHistory.length > 0) {
            metrics.avgDataSpeed = dataSpeedHistory.reduce((sum, speed) => sum + speed, 0) / dataSpeedHistory.length;
        }
            
        if (messagesHistory.length > 0) {
            metrics.avgSpeed = messagesHistory.reduce((sum, speed) => sum + speed, 0) / messagesHistory.length;
        }
        
        addToMetricsHistory(metrics);
        
        // Скрываем модальные окна
        if (calculatingMetricsModal) {
            calculatingMetricsModal.style.display = 'none';
        }
        if (DOM.elements.continuousModal) {
            DOM.elements.continuousModal.style.display = 'none';
        }
        if (DOM.elements.minimizedContinuousStatus) {
            DOM.elements.minimizedContinuousStatus.style.display = 'none';
        }
        
        stopStatusUpdates();
        
        // Разблокируем кнопки после остановки
        if (DOM.elements.startContinuousBtn) {
            DOM.elements.startContinuousBtn.disabled = false;
        }
        if (DOM.elements.stopContinuousBtn) {
            DOM.elements.stopContinuousBtn.disabled = true;
        }
        
        showMessage('Запись остановлена', 'success');
    } catch (error) {
        console.error('Ошибка при остановке записи:', error);
        showMessage('Ошибка при остановке записи: ' + error.message, 'error');
        
        // В случае ошибки также скрываем модальные окна и разблокируем кнопки
        if (calculatingMetricsModal) {
            calculatingMetricsModal.style.display = 'none';
        }
        if (DOM.elements.continuousModal) {
            DOM.elements.continuousModal.style.display = 'none';
        }
        if (DOM.elements.minimizedContinuousStatus) {
            DOM.elements.minimizedContinuousStatus.style.display = 'none';
        }
        
        if (DOM.elements.startContinuousBtn) {
            DOM.elements.startContinuousBtn.style.display = 'inline-block';
            DOM.elements.startContinuousBtn.disabled = false;
        }
        if (DOM.elements.stopContinuousBtn) {
            DOM.elements.stopContinuousBtn.style.display = 'none';
            DOM.elements.stopContinuousBtn.disabled = true;
        }
    }
}

function startStatusUpdates() {
    // Очищаем предыдущий интервал, если он существует
    if (statusUpdateInterval) {
        clearInterval(statusUpdateInterval);
    }
    
    // Запускаем новый интервал
    statusUpdateInterval = setInterval(async () => {
        try {
            await updateContinuousStatus();
        } catch (error) {
            console.error('Ошибка в интервале обновления статуса:', error);
        }
    }, 1000);
    
    console.log('Запущен интервал обновления статуса');
}

function stopStatusUpdates() {
    if (statusUpdateInterval) {
        clearInterval(statusUpdateInterval);
    }
}

async function updateContinuousStatus() {
    try {
        console.log('Запрос метрик...');
        const response = await fetch('/api/writer/metrics');
        
        if (!response.ok) {
            throw new Error('Ошибка при получении статуса');
        }

        const status = await response.json();
        console.log('Получен статус:', status);
        
        // Сохраняем текущие значения в историю
        if (status.isRunning) {
            if (status.currentDataSpeed) {
                dataSpeedHistory.push(status.currentDataSpeed);
            }
            if (status.currentSpeed) {
                messagesHistory.push(status.currentSpeed);
            }
        }
        
        updateMetrics(status);

        if (!status.isRunning) {
            console.log('Процесс остановлен, останавливаем обновление статуса');
            stopStatusUpdates();
            hideContinuousModal();
            document.getElementById('minimizedContinuousStatus').style.display = 'none';
            
            // Разблокируем кнопки при остановке
            document.getElementById('startContinuousBtn').style.display = 'inline-block';
            document.getElementById('stopContinuousBtn').style.display = 'none';
            document.getElementById('startContinuousBtn').disabled = false;
            document.getElementById('stopContinuousBtn').disabled = true;
        }
    } catch (error) {
        console.error('Ошибка при обновлении статуса:', error);
        // В случае ошибки также разблокируем кнопки
        document.getElementById('startContinuousBtn').style.display = 'inline-block';
        document.getElementById('stopContinuousBtn').style.display = 'none';
        document.getElementById('startContinuousBtn').disabled = false;
        document.getElementById('stopContinuousBtn').disabled = true;
    }
}

// Функции для работы с модальными окнами
function showContinuousModal() {
    DOM.elements.continuousModal.style.display = 'block';
}

function hideContinuousModal() {
    if (DOM.elements.continuousModal) {
        DOM.elements.continuousModal.style.display = 'none';
    }
    if (DOM.elements.minimizedContinuousStatus) {
        DOM.elements.minimizedContinuousStatus.style.display = 'none';
    }
    
    // Разблокируем кнопки при остановке
    if (DOM.elements.startContinuousBtn) {
        DOM.elements.startContinuousBtn.style.display = 'inline-block';
        DOM.elements.startContinuousBtn.disabled = false;
    }
    if (DOM.elements.stopContinuousBtn) {
        DOM.elements.stopContinuousBtn.style.display = 'none';
        DOM.elements.stopContinuousBtn.disabled = true;
    }
}

function toggleMinimizeModal() {
    if (DOM.elements.continuousModal.style.display === 'block') {
        DOM.elements.continuousModal.style.display = 'none';
        DOM.elements.minimizedContinuousStatus.style.display = 'block';
    } else {
        DOM.elements.continuousModal.style.display = 'block';
        DOM.elements.minimizedContinuousStatus.style.display = 'none';
    }
}

// Глобальные переменные для графиков
let speedChart = null;
let dataSpeedChart = null;
let metricsHistory = [];

// Оптимизированные функции для работы с графиками и метриками
function initializeCharts() {
    const speedCtx = document.getElementById('speedChart').getContext('2d');
    const dataSpeedCtx = document.getElementById('dataSpeedChart').getContext('2d');

    speedChart = new Chart(speedCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'Скорость (сообщений/сек)',
                data: [],
                borderColor: 'rgb(75, 192, 192)',
                tension: 0.1
            }]
        },
        options: {
            responsive: true,
            scales: {
                y: {
                    beginAtZero: true
                }
            }
        }
    });

    dataSpeedChart = new Chart(dataSpeedCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'Скорость передачи (МБ/сек)',
                data: [],
                borderColor: 'rgb(153, 102, 255)',
                tension: 0.1
            }]
        },
        options: {
            responsive: true,
            scales: {
                y: {
                    beginAtZero: true
                }
            }
        }
    });
}

function updateCharts(metrics) {
    const timestamp = new Date().toLocaleTimeString();
    
    speedChart.data.labels.push(timestamp);
    speedChart.data.datasets[0].data.push(metrics.currentSpeed);
    
    if (speedChart.data.labels.length > 20) {
        speedChart.data.labels.shift();
        speedChart.data.datasets[0].data.shift();
    }
    
    dataSpeedChart.data.labels.push(timestamp);
    dataSpeedChart.data.datasets[0].data.push(metrics.currentDataSpeed);
    
    if (dataSpeedChart.data.labels.length > 20) {
        dataSpeedChart.data.labels.shift();
        dataSpeedChart.data.datasets[0].data.shift();
    }
    
    speedChart.update();
    dataSpeedChart.update();
}

function addToMetricsHistory(metrics) {
    const historyContainer = document.getElementById('metricsHistory');
    const template = document.getElementById('metricsHistoryTemplate');
    
    if (!historyContainer || !template) {
        console.error('Не найдены необходимые элементы для отображения истории метрик');
        return;
    }

    const historyItem = template.content.cloneNode(true);
    
    // Устанавливаем время
    const now = new Date();
    historyItem.querySelector('.history-item-time').textContent = 
        now.toLocaleString('ru-RU', { 
            year: 'numeric', 
            month: '2-digit', 
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });

    // Устанавливаем информацию о топике и сервере
    const kafkaConfig = utils.getKafkaConfig();
    historyItem.querySelector('.history-item-topic').textContent = `Топик: ${kafkaConfig.topic || 'не выбран'}`;
    historyItem.querySelector('.history-item-server').textContent = `Сервер: ${kafkaConfig.bootstrapServers || 'не указан'}`;

    // Заполняем метрики
    const totalTime = metrics.totalProcessingTimeMs ? (metrics.totalProcessingTimeMs / 1000).toFixed(2) : '0.00';
    const totalData = metrics.bytesSent ? (metrics.bytesSent / (1024 * 1024)).toFixed(2) : '0.00';
    
    historyItem.querySelector('.total-time').textContent = totalTime;
    historyItem.querySelector('.total-messages').textContent = utils.formatNumber(metrics.messagesSent || 0);
    historyItem.querySelector('.total-data').textContent = totalData;
    historyItem.querySelector('.avg-speed').textContent = utils.formatNumber(metrics.avgSpeed || 0);
    historyItem.querySelector('.avg-data-speed').textContent = utils.formatNumber(metrics.avgDataSpeed || 0);

    // Добавляем элемент в начало списка
    historyContainer.insertBefore(historyItem, historyContainer.firstChild);
    
    // Сохраняем историю в localStorage
    saveMetricsHistory();
}

// Оптимизированная функция updateMetrics
function updateMetrics(metrics) {
    const {
        totalProcessingTimeMs,
        currentSpeed,
        currentDataSpeed,
        avgSpeed,
        avgDataSpeed,
        totalLinesProcessed,
        linesWrittenToKafka,
        messagesSent,
        bytesSent,
        fileSizeBytes,
        activeThreads,
        isRunning,
        isStopping,
        fileName
    } = metrics;

    const formattedMetrics = {
        totalTime: utils.formatNumber(totalProcessingTimeMs / 1000),
        currentSpeed: utils.formatNumber(currentSpeed),
        currentDataSpeed: utils.formatDataSpeed(currentDataSpeed),
        avgSpeed: utils.formatNumber(avgSpeed),
        avgDataSpeed: utils.formatDataSpeed(avgDataSpeed),
        totalLines: totalLinesProcessed || 0,
        linesWritten: linesWrittenToKafka || 0,
        messagesSent: messagesSent || 0,
        bytesSent: bytesSent || 0,
        fileSize: fileSizeBytes || 0,
        activeThreads: activeThreads || 0
    };

    // Обновляем текущие метрики
    Object.entries({
        totalTime: formattedMetrics.totalTime,
        currentSpeed: formattedMetrics.currentSpeed,
        currentDataSpeed: formattedMetrics.currentDataSpeed,
        kafkaLines: formattedMetrics.linesWritten.toLocaleString()
    }).forEach(([id, value]) => utils.updateElementText(id, value));

    // Обновляем статус в модальном окне
    utils.updateElementText('continuousStatus', isRunning ? 'Активна' : (isStopping ? 'Останавливается...' : 'Остановлена'));
    utils.updateElementText('continuousFileName', fileName || '-');
    utils.updateElementText('continuousMessagesSent', formattedMetrics.messagesSent.toLocaleString());
    utils.updateElementText('continuousSpeed', formattedMetrics.currentSpeed);
    utils.updateElementText('continuousDataSpeed', formattedMetrics.currentDataSpeed);

    // Обновляем статус в свернутом окне
    utils.updateElementText('minimizedSpeed', formattedMetrics.currentSpeed);
    utils.updateElementText('minimizedDataSpeed', formattedMetrics.currentDataSpeed);

    updateUIState(isRunning, isStopping);
}

// Функция для обновления состояния UI
function updateUIState(isRunning, isStopping) {
    const elements = {
        stopButton: DOM.elements.stopContinuousBtn,
        startButton: DOM.elements.startContinuousBtn,
        clearButton: DOM.elements.clearContinuousFileBtn,
        fileInput: DOM.elements.continuousFileInput,
        speedInput: DOM.elements.targetSpeed,
        dataSpeedInput: DOM.elements.targetDataSpeed,
        topicInput: DOM.elements.kafkaTopic,
        kafkaServersInput: DOM.elements.kafkaBootstrapServers,
        continuousModal: DOM.elements.continuousModal
    };

    const modalElements = elements.continuousModal ? {
        title: elements.continuousModal.querySelector('h3'),
        body: elements.continuousModal.querySelector('.continuous-stats'),
        footer: elements.continuousModal.querySelector('.modal-buttons')
    } : null;

    if (isStopping) {
        Object.values(elements).forEach(el => el && (el.disabled = true));
        if (modalElements) {
            modalElements.title.innerHTML = '<div class="d-flex align-items-center"><div class="spinner-border spinner-border-sm text-primary me-2" role="status"></div>Остановка процесса...</div>';
            modalElements.body.style.opacity = '0.5';
        }
    } else if (isRunning) {
        elements.stopButton && (elements.stopButton.disabled = false);
        elements.startButton && (elements.startButton.disabled = true);
        elements.clearButton && (elements.clearButton.disabled = true);
        elements.fileInput && (elements.fileInput.disabled = true);
        elements.speedInput && (elements.speedInput.disabled = true);
        elements.dataSpeedInput && (elements.dataSpeedInput.disabled = true);
        elements.topicInput && (elements.topicInput.disabled = true);
        elements.kafkaServersInput && (elements.kafkaServersInput.disabled = true);
        
        if (modalElements) {
            modalElements.title.textContent = 'Статус записи';
            modalElements.body.style.opacity = '1';
        }
    } else {
        elements.stopButton && (elements.stopButton.disabled = true);
        elements.startButton && (elements.startButton.disabled = false);
        elements.clearButton && (elements.clearButton.disabled = false);
        elements.fileInput && (elements.fileInput.disabled = false);
        elements.speedInput && (elements.speedInput.disabled = false);
        elements.dataSpeedInput && (elements.dataSpeedInput.disabled = false);
        elements.topicInput && (elements.topicInput.disabled = false);
        elements.kafkaServersInput && (elements.kafkaServersInput.disabled = false);
        
        if (modalElements) {
            modalElements.title.textContent = 'Статус записи';
            modalElements.body.style.opacity = '1';
        }
    }
}

// Оптимизированные функции для работы с файлами
function handleFileSelect(event) {
    const file = event.target.files[0];
    if (!file) return;

    selectedFile = file;
    DOM.elements.selectedFileName.textContent = file.name;
    DOM.elements.startBtn.disabled = false;
    DOM.elements.clearFileBtn.style.display = 'inline-block';
}

function clearFile() {
    selectedFile = null;
    DOM.elements.fileInput.value = '';
    DOM.elements.selectedFileName.textContent = '';
    DOM.elements.startBtn.disabled = true;
    DOM.elements.clearFileBtn.style.display = 'none';
}

function handleContinuousFileSelect(event) {
    const file = event.target.files[0];
    if (!file) return;

    selectedContinuousFile = file;
    DOM.elements.continuousSelectedFileName.textContent = file.name;
    DOM.elements.startContinuousBtn.disabled = false;
    DOM.elements.clearContinuousFileBtn.style.display = 'inline-block';
}

// Оптимизированные функции для работы с Kafka
async function saveKafkaConfig() {
    const config = utils.getKafkaConfig();

    if (!config.bootstrapServers) {
        showMessage('Введите адрес сервера Kafka');
        return;
    }

    try {
        utils.disableButtons(['testKafkaConnection', 'saveKafkaConfig', 'startContinuousBtn', 'stopContinuousBtn']);
        
        console.log('Сохраняем конфигурацию:', config);

        const response = await fetch('/api/kafka/config', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });

        const data = await response.json();
        console.log('Ответ сервера:', data);
        
        if (data.success) {
            showMessage('Настройки успешно сохранены');
            await loadExistingTopics();
            
            if (config.topic && topicChoices) {
                topicChoices.setChoiceByValue(config.topic);
            }
        } else {
            showMessage(data.message || 'Ошибка при сохранении настроек', 'error');
        }
    } catch (error) {
        console.error('Ошибка при сохранении настроек:', error);
        showMessage('Ошибка при сохранении настроек: ' + error.message, 'error');
    } finally {
        utils.enableButtons(['testKafkaConnection', 'saveKafkaConfig', 'startContinuousBtn', 'stopContinuousBtn']);
    }
}

async function testKafkaConnection() {
    const config = utils.getKafkaConfig();

    if (!config.bootstrapServers || config.bootstrapServers.trim() === '') {
        showMessage('Введите адрес сервера Kafka', 'error');
        return;
    }

    try {
        utils.disableButtons(['testKafkaConnection', 'saveKafkaConfig', 'startContinuousBtn', 'stopContinuousBtn']);
        showMessage('Проверка подключения...', 'info');

        const requestBody = {
            bootstrapServers: config.bootstrapServers.trim(),
            topic: config.topic ? config.topic.trim() : null,
            username: config.username ? config.username.trim() : null,
            password: config.password ? config.password.trim() : null
        };

        console.log('Отправляем запрос с данными:', {
            ...requestBody,
            password: requestBody.password ? '***' : null
        });

        const response = await fetch(utils.getApiUrl('/api/kafka/check-connection'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody)
        });

        if (!response.ok) {
            throw new Error('Ошибка при проверке подключения: ' + response.statusText);
        }

        const result = await response.json();
        console.log('Получен ответ:', result);
        
        if (result.success) {
            showMessage(result.message, 'success');
            // Автоматически обновляем список топиков
            await loadExistingTopics();
        } else {
            showMessage(result.message || 'Ошибка при проверке подключения', 'error');
        }
    } catch (error) {
        console.error('Ошибка при проверке подключения:', error);
        showMessage('Ошибка при проверке подключения: ' + error.message, 'error');
    } finally {
        utils.enableButtons(['testKafkaConnection', 'saveKafkaConfig', 'startContinuousBtn', 'stopContinuousBtn']);
    }
}

async function loadKafkaConfig() {
    try {
        const response = await fetch('/api/kafka/config');
        if (!response.ok) {
            throw new Error('Ошибка при загрузке настроек');
        }

        const config = await response.json();
        
        DOM.elements.kafkaBootstrapServers.value = config.bootstrapServers || '';
        DOM.elements.kafkaTopic.value = config.topic || '';
        DOM.elements.kafkaUsername.value = config.username || '';
        DOM.elements.kafkaPassword.value = config.password || '';
        
        if (config.bootstrapServers) {
            await loadExistingTopics();
            // После загрузки топиков восстанавливаем сохраненный топик
            loadTopicState();
        }
    } catch (error) {
        console.error('Ошибка при загрузке настроек:', error);
        showMessage('Ошибка при загрузке настроек: ' + error.message);
    }
}

let topicChoices;

function initializeChoices() {
    const topicSelect = document.getElementById('kafkaTopic');
    if (topicSelect) {
        topicChoices = new Choices(topicSelect, {
            searchEnabled: false,
            itemSelectText: '',
            shouldSort: false,
            position: 'bottom',
            classNames: {
                containerOuter: 'choices',
                containerInner: 'choices__inner',
                input: 'choices__input',
                inputCloned: 'choices__input--cloned',
                list: 'choices__list',
                listItems: 'choices__list--multiple',
                listSingle: 'choices__list--single',
                listDropdown: 'choices__list--dropdown',
                item: 'choices__item',
                itemSelectable: 'choices__item--selectable',
                itemDisabled: 'choices__item--disabled',
                itemOption: 'choices__item--choice',
                group: 'choices__group',
                groupHeading: 'choices__heading',
                button: 'choices__button',
                activeState: 'is-active',
                focusState: 'is-focused',
                openState: 'is-open',
                disabledState: 'is-disabled',
                highlightedState: 'is-highlighted',
                selectedState: 'is-selected'
            }
        });

        // Добавляем обработчик изменения значения
        topicSelect.addEventListener('change', function() {
            saveTopicState();
        });
    }
}

// Функции для работы с историей метрик
function saveMetricsHistory() {
    const historyContainer = document.getElementById('metricsHistory');
    if (!historyContainer) return;
    
    const historyItems = Array.from(historyContainer.children).map(item => ({
        time: item.querySelector('.history-item-time').textContent,
        topic: item.querySelector('.history-item-topic').textContent,
        server: item.querySelector('.history-item-server').textContent,
        totalTime: item.querySelector('.total-time').textContent,
        totalMessages: item.querySelector('.total-messages').textContent,
        totalData: item.querySelector('.total-data').textContent,
        avgSpeed: item.querySelector('.avg-speed').textContent,
        avgDataSpeed: item.querySelector('.avg-data-speed').textContent
    }));
    
    localStorage.setItem('metricsHistory', JSON.stringify(historyItems));
}

function loadMetricsHistory() {
    const historyContainer = document.getElementById('metricsHistory');
    const template = document.getElementById('metricsHistoryTemplate');
    
    if (!historyContainer || !template) return;
    
    const savedHistory = localStorage.getItem('metricsHistory');
    if (!savedHistory) return;
    
    try {
        const historyItems = JSON.parse(savedHistory);
        historyContainer.innerHTML = ''; // Очищаем контейнер
        
        historyItems.forEach(item => {
            const historyItem = template.content.cloneNode(true);
            
            historyItem.querySelector('.history-item-time').textContent = item.time;
            historyItem.querySelector('.history-item-topic').textContent = item.topic;
            historyItem.querySelector('.history-item-server').textContent = item.server;
            historyItem.querySelector('.total-time').textContent = item.totalTime;
            historyItem.querySelector('.total-messages').textContent = item.totalMessages;
            historyItem.querySelector('.total-data').textContent = item.totalData;
            historyItem.querySelector('.avg-speed').textContent = item.avgSpeed;
            historyItem.querySelector('.avg-data-speed').textContent = item.avgDataSpeed;
            
            historyContainer.appendChild(historyItem);
        });
    } catch (error) {
        console.error('Ошибка при загрузке истории метрик:', error);
    }
}

// Функции для работы с состоянием непрерывной записи
function saveContinuousState() {
    const state = {
        targetSpeed: DOM.elements.targetSpeed.value,
        targetDataSpeed: DOM.elements.targetDataSpeed.value,
        speedType: document.querySelector('input[name="speedType"]:checked').value,
        fileName: DOM.elements.continuousSelectedFileName.textContent
    };
    localStorage.setItem('continuousState', JSON.stringify(state));
}

function loadContinuousState() {
    const savedState = localStorage.getItem('continuousState');
    if (!savedState) return;
    
    try {
        const state = JSON.parse(savedState);
        
        // Восстанавливаем значения полей скорости
        DOM.elements.targetSpeed.value = state.targetSpeed || '1000';
        DOM.elements.targetDataSpeed.value = state.targetDataSpeed || '1';
        
        // Восстанавливаем состояние радио-кнопок
        const speedTypeRadio = document.querySelector(`input[name="speedType"][value="${state.speedType}"]`);
        if (speedTypeRadio) {
            speedTypeRadio.checked = true;
            // Обновляем состояние полей ввода
            if (state.speedType === 'messages') {
                DOM.elements.targetSpeed.disabled = false;
                DOM.elements.targetDataSpeed.disabled = true;
            } else {
                DOM.elements.targetSpeed.disabled = true;
                DOM.elements.targetDataSpeed.disabled = false;
            }
        }
        
        // Восстанавливаем имя файла
        if (state.fileName) {
            DOM.elements.continuousSelectedFileName.textContent = state.fileName;
            DOM.elements.startContinuousBtn.disabled = false;
            DOM.elements.clearContinuousFileBtn.style.display = 'inline-block';
        }
    } catch (error) {
        console.error('Ошибка при загрузке состояния непрерывной записи:', error);
    }
}

// Функции для работы с состоянием топика
function saveTopicState() {
    const topic = DOM.elements.kafkaTopic.value;
    if (topic) {
        localStorage.setItem('selectedTopic', topic);
    }
}

function loadTopicState() {
    const savedTopic = localStorage.getItem('selectedTopic');
    if (savedTopic && topicChoices) {
        topicChoices.setChoiceByValue(savedTopic);
    }
}

function clearLocalStorage() {
    console.log('Очистка локальных данных...');
    localStorage.removeItem('metricsHistory');
    localStorage.removeItem('continuousState');
    localStorage.removeItem('selectedTopic');
}

function initializeApp() {
    console.log('Инициализация приложения...');
    
    // Очищаем локальные данные при старте
    clearLocalStorage();
    
    // Инициализация Choices.js для селекта топиков
    initializeChoices();
    
    // Загрузка истории метрик
    loadMetricsHistory();
    
    // Загрузка состояния непрерывной записи
    loadContinuousState();
    
    // Инициализация обработчиков событий
    document.addEventListener('click', function(e) {
        if (e.target.closest('.toggle-details') || e.target.closest('.history-item-header')) {
            const historyItem = e.target.closest('.history-item');
            const content = historyItem.querySelector('.history-item-content');
            const icon = historyItem.querySelector('.toggle-details i');
            
            if (content.style.display === 'none') {
                content.style.display = 'block';
                icon.className = 'fas fa-chevron-down';
            } else {
                content.style.display = 'none';
                icon.className = 'fas fa-chevron-right';
            }
        }
    });
    
    // Добавляем обработчики событий
    const saveConfigBtn = document.getElementById('saveKafkaConfig');
    const testConnectionBtn = document.getElementById('testKafkaConnection');
    const refreshTopicsBtn = document.querySelector('.refresh-topics');
    
    if (saveConfigBtn) {
        saveConfigBtn.addEventListener('click', saveKafkaConfig);
    }
    
    if (testConnectionBtn) {
        testConnectionBtn.addEventListener('click', testKafkaConnection);
    }
    
    if (refreshTopicsBtn) {
        refreshTopicsBtn.addEventListener('click', loadExistingTopics);
    }
    
    // Добавляем обработчик изменения адреса Kafka
    const kafkaServersInput = document.getElementById('kafkaBootstrapServers');
    if (kafkaServersInput) {
        kafkaServersInput.addEventListener('change', function() {
            // Очищаем выбранный топик
            if (topicChoices) {
                topicChoices.setChoiceByValue('');
            }
            // Очищаем список топиков
            const topicSelect = document.getElementById('kafkaTopic');
            if (topicSelect) {
                topicSelect.innerHTML = '<option value="">Выберите топик</option>';
                // Обновляем Choices.js
                if (topicChoices) {
                    topicChoices.destroy();
                }
                initializeChoices();
            }
        });
    }
    
    console.log('Инициализация завершена');
}

// Ждем полной загрузки DOM
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeApp);
} else {
    initializeApp();
}

async function loadExistingTopics() {
    if (!DOM.buttons.refreshTopics) {
        console.error('Кнопка обновления топиков не найдена');
        return;
    }
    
    DOM.buttons.refreshTopics.classList.add('rotating');
    
    try {
        const response = await fetch(utils.getApiUrl('/api/kafka/topics'));
        if (!response.ok) {
            throw new Error('Ошибка при загрузке топиков');
        }

        const data = await response.json();
        console.log('Получены топики:', data);

        if (!data.success) {
            throw new Error(data.error || 'Неизвестная ошибка');
        }

        const topics = data.topics;
        console.log('Список топиков:', topics);

        if (!DOM.elements.kafkaTopic) {
            console.error('Элемент выбора топика не найден');
            return;
        }
        
        DOM.elements.kafkaTopic.innerHTML = '';
        
        const emptyOption = document.createElement('option');
        emptyOption.value = '';
        emptyOption.textContent = 'Выберите топик';
        DOM.elements.kafkaTopic.appendChild(emptyOption);
        
        if (Array.isArray(topics) && topics.length > 0) {
            topics.forEach(topic => {
                const option = document.createElement('option');
                option.value = topic;
                option.textContent = topic;
                DOM.elements.kafkaTopic.appendChild(option);
            });
        }

        if (topicChoices) {
            topicChoices.destroy();
        }
        initializeChoices();
        
        // После инициализации Choices.js восстанавливаем сохраненный топик
        loadTopicState();
    } catch (error) {
        console.error('Ошибка при загрузке топиков:', error);
        showMessage('Ошибка при загрузке топиков: ' + error.message, 'error');
    } finally {
        DOM.buttons.refreshTopics.classList.remove('rotating');
    }
}

function togglePassword() {
    const type = DOM.elements.kafkaPassword.type === 'password' ? 'text' : 'password';
    DOM.elements.kafkaPassword.type = type;
    DOM.elements.togglePasswordButton.classList.toggle('fa-eye');
    DOM.elements.togglePasswordButton.classList.toggle('fa-eye-slash');
}

// Мульти-файловый режим
let selectedMultiFiles = [];

const multiFileInput = document.getElementById('multiFileInput');
const multiSelectedFileNames = document.getElementById('multiSelectedFileNames');

multiFileInput.addEventListener('change', function(e) {
    selectedMultiFiles = Array.from(e.target.files);
    if (selectedMultiFiles.length > 0) {
        multiSelectedFileNames.textContent = selectedMultiFiles.map(f => f.name).join(', ');
        document.getElementById('startMultiContinuousBtn').disabled = false;
    } else {
        multiSelectedFileNames.textContent = '';
        document.getElementById('startMultiContinuousBtn').disabled = true;
    }
});

document.getElementById('startMultiContinuousBtn').disabled = true;

async function startMultiContinuousWriting() {
    if (!selectedMultiFiles.length) {
        showMessage('Пожалуйста, выберите файлы для мульти-записи');
        return;
    }
    const partitionRegex = document.getElementById('partitionRegex').value;
    const formData = new FormData();
    selectedMultiFiles.forEach(file => formData.append('files', file));
    const speedType = document.querySelector('input[name="speedType"]:checked').value;
    if (speedType === 'messages') {
        formData.append('targetSpeed', DOM.elements.targetSpeed.value);
        formData.append('targetDataSpeed', '0');
    } else {
        formData.append('targetSpeed', '0');
        formData.append('targetDataSpeed', DOM.elements.targetDataSpeed.value);
    }
    if (partitionRegex) {
        formData.append('partitionRegex', partitionRegex);
    }
    try {
        utils.disableButtons(['startMultiContinuousBtn', 'startContinuousBtn', 'stopContinuousBtn']);
        showContinuousModal();
        const response = await fetch(utils.getApiUrl('/api/writer/start-multi'), {
            method: 'POST',
            body: formData
        });
        if (!response.ok) {
            throw new Error('Ошибка при запуске мульти-записи');
        }
        await updateContinuousStatus();
    } catch (error) {
        showMessage('Ошибка при запуске мульти-записи: ' + error.message, 'error');
        hideContinuousModal();
    } finally {
        utils.enableButtons(['startMultiContinuousBtn', 'startContinuousBtn', 'stopContinuousBtn']);
    }
} 