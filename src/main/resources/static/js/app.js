// Функция для очистки топика
async function clearTopic() {
    const recreateBtn = document.getElementById('recreateTopicBtn');
    const topicSpinner = document.getElementById('topicSpinner');
    const confirmBtn = document.querySelector('.modal-button.confirm');
    const cancelBtn = document.querySelector('.modal-button.cancel');

    try {
        // Блокируем кнопки и показываем спиннер
        recreateBtn.disabled = true;
        confirmBtn.disabled = true;
        cancelBtn.disabled = true;
        topicSpinner.style.display = 'block';

        const response = await fetch('/api/kafka/topic/clear', {
            method: 'DELETE'
        });
        
        const data = await response.json();
        
        if (response.ok && data.success) {
            hideConfirmModal();
            // Обновляем список топиков через 3 секунды без показа ошибок
            setTimeout(() => {
                loadExistingTopics();
                // Разблокируем кнопки и скрываем спиннер
                recreateBtn.disabled = false;
                confirmBtn.disabled = false;
                cancelBtn.disabled = false;
                topicSpinner.style.display = 'none';
            }, 3000);
        } else {
            hideConfirmModal();
            showMessage(data.error || 'Ошибка при пересоздании топика');
            // Разблокируем кнопки и скрываем спиннер при ошибке
            recreateBtn.disabled = false;
            confirmBtn.disabled = false;
            cancelBtn.disabled = false;
            topicSpinner.style.display = 'none';
        }
    } catch (error) {
        console.error('Ошибка при пересоздании топика:', error);
        hideConfirmModal();
        showMessage('Ошибка при пересоздании топика: ' + error.message);
        // Разблокируем кнопки и скрываем спиннер при ошибке
        recreateBtn.disabled = false;
        confirmBtn.disabled = false;
        cancelBtn.disabled = false;
        topicSpinner.style.display = 'none';
    }
}

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

// Функция для показа модального окна подтверждения
function showConfirmModal() {
    document.getElementById('confirmModal').style.display = 'flex';
}

// Функция для закрытия модального окна подтверждения
function hideConfirmModal() {
    document.getElementById('confirmModal').style.display = 'none';
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
    } else {
        document.getElementById('continuousSelectedFileName').textContent = '';
        document.getElementById('startContinuousBtn').disabled = true;
        document.getElementById('clearContinuousFileBtn').style.display = 'none';
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
    });
});

function clearContinuousFile() {
    selectedContinuousFile = null;
    document.getElementById('continuousFileInput').value = '';
    document.getElementById('continuousSelectedFileName').textContent = '';
    document.getElementById('startContinuousBtn').disabled = true;
    document.getElementById('clearContinuousFileBtn').style.display = 'none';
    
    // Сбрасываем значения полей скорости
    document.getElementById('targetSpeed').value = '1000';
    document.getElementById('targetDataSpeed').value = '1';
    
    // Устанавливаем радио-кнопку "По количеству сообщений" активной
    document.querySelector('input[name="speedType"][value="messages"]').checked = true;
    document.getElementById('targetSpeed').disabled = false;
    document.getElementById('targetDataSpeed').disabled = true;
}

async function startContinuousWriting() {
    if (!selectedContinuousFile) {
        showMessage('Пожалуйста, выберите файл для записи');
        return;
    }

    // Сразу показываем модальное окно и меняем состояние кнопок
    showContinuousModal();
    document.getElementById('startContinuousBtn').style.display = 'none';
    document.getElementById('stopContinuousBtn').style.display = 'inline-block';
    document.getElementById('stopContinuousBtn').disabled = false;

    // Запускаем обновление статуса до отправки запроса
    startStatusUpdates();

    const formData = new FormData();
    formData.append('file', selectedContinuousFile);
    
    // Добавляем настройки скорости в зависимости от выбранного типа
    const speedType = document.querySelector('input[name="speedType"]:checked').value;
    if (speedType === 'messages') {
        formData.append('targetSpeed', document.getElementById('targetSpeed').value);
        formData.append('targetDataSpeed', '0'); // Отключаем контроль по скорости передачи
    } else {
        formData.append('targetSpeed', '0'); // Отключаем контроль по количеству сообщений
        formData.append('targetDataSpeed', document.getElementById('targetDataSpeed').value);
    }

    try {
        const response = await fetch('/api/writer/start', {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            throw new Error('Ошибка при запуске записи');
        }

        // Делаем первый запрос метрик после успешного старта
        await updateContinuousStatus();
    } catch (error) {
        console.error('Ошибка:', error);
        showMessage('Ошибка при запуске записи: ' + error.message);
        // В случае ошибки возвращаем кнопки в исходное состояние
        document.getElementById('startContinuousBtn').style.display = 'inline-block';
        document.getElementById('stopContinuousBtn').style.display = 'none';
        document.getElementById('startContinuousBtn').disabled = false;
        document.getElementById('stopContinuousBtn').disabled = true;
        hideContinuousModal();
        stopStatusUpdates();
    }
}

// Функции для работы с модальными окнами
function showCalculatingMetricsModal() {
    let modal = document.getElementById('calculatingMetricsModal');
    if (!modal) {
        // Создаем модальное окно, если его нет
        modal = document.createElement('div');
        modal.id = 'calculatingMetricsModal';
        modal.className = 'modal';
        modal.innerHTML = `
            <div class="modal-content">
                <h3>Расчет метрик</h3>
                <div class="calculating-metrics">
                    <div class="spinner"></div>
                    <p>Пожалуйста, подождите, идет расчет метрик...</p>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
    }
    modal.style.display = 'flex';
}

function hideCalculatingMetricsModal() {
    const modal = document.getElementById('calculatingMetricsModal');
    if (modal) {
        modal.style.display = 'none';
    }
}

async function stopContinuousWriting() {
    try {
        showCalculatingMetricsModal();
        
        // Сразу меняем состояние кнопок
        document.getElementById('startContinuousBtn').style.display = 'inline-block';
        document.getElementById('stopContinuousBtn').style.display = 'none';
        
        const response = await fetch('/api/writer/stop', {
            method: 'POST'
        });

        if (!response.ok) {
            throw new Error('Ошибка при остановке записи');
        }

        const metrics = await response.json();
        console.log('Получены метрики:', metrics);
        
        // Добавляем текущие значения в историю для расчета средних
        if (metrics.currentDataSpeed) {
            dataSpeedHistory.push(metrics.currentDataSpeed);
        }
        if (metrics.currentSpeed) {
            messagesHistory.push(metrics.currentSpeed);
        }
        
        // Рассчитываем средние значения на основе собранной истории
        if (dataSpeedHistory.length > 0) {
            metrics.avgDataSpeed = dataSpeedHistory.reduce((sum, speed) => sum + speed, 0) / dataSpeedHistory.length;
        }
            
        if (messagesHistory.length > 0) {
            metrics.avgSpeed = messagesHistory.reduce((sum, speed) => sum + speed, 0) / messagesHistory.length;
        }
        
        // Добавляем запись в историю с рассчитанными средними значениями
        addToMetricsHistory(metrics);
        
        // Очищаем историю после добавления записи
        dataSpeedHistory = [];
        messagesHistory = [];
        
        updateMetrics(metrics);
        stopStatusUpdates();
        hideContinuousModal();
        
        // Скрываем модальное окно подсчета метрик
        hideCalculatingMetricsModal();
    } catch (error) {
        console.error('Ошибка:', error);
        showMessage('Ошибка при остановке записи: ' + error.message);
        hideCalculatingMetricsModal();
        
        // В случае ошибки все равно возвращаем кнопки в исходное состояние
        document.getElementById('startContinuousBtn').style.display = 'inline-block';
        document.getElementById('stopContinuousBtn').style.display = 'none';
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
    const modal = document.getElementById('continuousModal');
    modal.style.display = 'block';
}

function hideContinuousModal() {
    document.getElementById('continuousModal').style.display = 'none';
    document.getElementById('minimizedContinuousStatus').style.display = 'none';
}

function toggleMinimizeModal() {
    const modal = document.getElementById('continuousModal');
    const minimizedStatus = document.getElementById('minimizedContinuousStatus');
    
    if (modal.style.display === 'block') {
        // Сворачиваем
        modal.style.display = 'none';
        minimizedStatus.style.display = 'block';
    } else {
        // Разворачиваем
        modal.style.display = 'block';
        minimizedStatus.style.display = 'none';
    }
}

// Глобальные переменные для графиков
let speedChart = null;
let dataSpeedChart = null;
let metricsHistory = [];

// Инициализация графиков
function initializeCharts() {
    // График скорости обработки
    const speedCtx = document.getElementById('speedChart').getContext('2d');
    speedChart = new Chart(speedCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'Скорость обработки (сообщений/сек)',
                data: [],
                borderColor: 'rgb(75, 192, 192)',
                tension: 0.1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: true
                }
            }
        }
    });

    // График скорости передачи данных
    const dataSpeedCtx = document.getElementById('dataSpeedChart').getContext('2d');
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
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: true
                }
            }
        }
    });
}

// Обновление графиков
function updateCharts(metrics) {
    const timestamp = new Date().toLocaleTimeString();
    
    // Обновляем график скорости обработки
    speedChart.data.labels.push(timestamp);
    speedChart.data.datasets[0].data.push(metrics.currentSpeed || 0);
    
    // Ограничиваем количество точек на графике
    if (speedChart.data.labels.length > 20) {
        speedChart.data.labels.shift();
        speedChart.data.datasets[0].data.shift();
    }
    
    speedChart.update();

    // Обновляем график скорости передачи данных
    dataSpeedChart.data.labels.push(timestamp);
    dataSpeedChart.data.datasets[0].data.push(metrics.currentDataSpeed || 0);
    
    // Ограничиваем количество точек на графике
    if (dataSpeedChart.data.labels.length > 20) {
        dataSpeedChart.data.labels.shift();
        dataSpeedChart.data.datasets[0].data.shift();
    }
    
    dataSpeedChart.update();
}

// Добавление записи в историю
function addToMetricsHistory(metrics) {
    const historyEntry = {
        date: new Date().toLocaleString(),
        servers: document.getElementById('kafkaBootstrapServers').value,
        topic: document.getElementById('kafkaTopic').value,
        fileName: metrics.fileName || '-',
        processingTime: (metrics.totalProcessingTimeMs / 1000).toFixed(2),
        avgSpeed: metrics.avgSpeed ? metrics.avgSpeed.toFixed(2) : '0.00',
        avgDataSpeed: metrics.avgDataSpeed ? metrics.avgDataSpeed.toFixed(4) : '0.0000',
        messagesSent: metrics.messagesSent || 0,
        fileSize: metrics.fileSizeBytes ? (metrics.fileSizeBytes / (1024 * 1024)).toFixed(2) : '0.00'
    };

    metricsHistory.unshift(historyEntry);
    
    // Ограничиваем историю последними 10 записями
    if (metricsHistory.length > 10) {
        metricsHistory.pop();
    }

    updateMetricsHistoryList();
}

// Обновление списка истории
function updateMetricsHistoryList() {
    const historyList = document.getElementById('metricsHistoryList');
    const template = document.getElementById('historyItemTemplate');
    
    historyList.innerHTML = '';

    metricsHistory.forEach(entry => {
        const clone = template.content.cloneNode(true);
        
        // Заполняем основные данные
        clone.querySelector('.history-date').textContent = entry.date;
        clone.querySelector('.history-servers').textContent = entry.servers;
        clone.querySelector('.history-topic').textContent = entry.topic;
        
        // Заполняем детали
        clone.querySelector('.history-file').textContent = entry.fileName;
        clone.querySelector('.history-time').textContent = `${entry.processingTime} сек`;
        clone.querySelector('.history-speed').textContent = `${entry.avgSpeed} сообщений/сек`;
        clone.querySelector('.history-data-speed').textContent = `${entry.avgDataSpeed} МБ/сек`;
        clone.querySelector('.history-messages').textContent = entry.messagesSent.toLocaleString();
        clone.querySelector('.history-file-size').textContent = `${entry.fileSize} MB`;
        
        // Добавляем обработчик для раскрытия/скрытия деталей
        const header = clone.querySelector('.history-item-header');
        const details = clone.querySelector('.history-item-details');
        const toggleBtn = clone.querySelector('.toggle-details');
        
        header.addEventListener('click', () => {
            details.classList.toggle('active');
            toggleBtn.classList.toggle('active');
        });
        
        historyList.appendChild(clone);
    });
}

// Обновляем функцию updateMetrics
function updateMetrics(metrics) {
    const totalTime = metrics.totalProcessingTimeMs ? (metrics.totalProcessingTimeMs / 1000).toFixed(2) : '0.00';
    const currentSpeed = metrics.currentSpeed ? metrics.currentSpeed.toFixed(2) : '0.00';
    const currentDataSpeed = metrics.currentDataSpeed ? metrics.currentDataSpeed.toFixed(4) : '0.0000';
    const avgSpeed = metrics.avgSpeed ? metrics.avgSpeed.toFixed(2) : '0.00';
    const avgDataSpeed = metrics.avgDataSpeed ? metrics.avgDataSpeed.toFixed(4) : '0.0000';
    const totalLines = metrics.totalLinesProcessed || 0;
    const linesWritten = metrics.linesWrittenToKafka || 0;
    const messagesSent = metrics.messagesSent || 0;
    const bytesSent = metrics.bytesSent || 0;
    const fileSize = metrics.fileSizeBytes || 0;
    const activeThreads = metrics.activeThreads || 0;
    const isRunning = metrics.isRunning;
    const isStopping = metrics.isStopping;

    // Функция для безопасного обновления текста элемента
    const updateElementText = (id, text) => {
        const element = document.getElementById(id);
        if (element) {
            element.textContent = text;
        }
    };

    // Обновляем текущие метрики
    updateElementText('totalTime', totalTime);
    updateElementText('currentSpeed', currentSpeed);
    updateElementText('currentDataSpeed', currentDataSpeed);
    updateElementText('kafkaLines', linesWritten.toLocaleString());

    // Обновляем статус в модальном окне
    updateElementText('continuousStatus', isRunning ? 'Активна' : (isStopping ? 'Останавливается...' : 'Остановлена'));
    updateElementText('continuousFileName', metrics.fileName || '-');
    updateElementText('continuousMessagesSent', messagesSent.toLocaleString());
    updateElementText('continuousSpeed', currentSpeed);
    updateElementText('continuousDataSpeed', currentDataSpeed);

    // Обновляем статус в свернутом окне
    updateElementText('minimizedSpeed', currentSpeed);
    updateElementText('minimizedDataSpeed', currentDataSpeed);

    // Управление состоянием кнопок и элементов управления
    const stopButton = document.getElementById('stopContinuousBtn');
    const startButton = document.getElementById('startContinuousBtn');
    const clearButton = document.getElementById('clearContinuousFileBtn');
    const fileInput = document.getElementById('continuousFileInput');
    const speedInput = document.getElementById('targetSpeed');
    const dataSpeedInput = document.getElementById('targetDataSpeed');
    const topicInput = document.getElementById('kafkaTopic');
    const kafkaServersInput = document.getElementById('kafkaBootstrapServers');
    const continuousModal = document.getElementById('continuousModal');

    if (isStopping) {
        if (stopButton) stopButton.disabled = true;
        if (startButton) startButton.disabled = true;
        if (clearButton) clearButton.disabled = true;
        if (fileInput) fileInput.disabled = true;
        if (speedInput) speedInput.disabled = true;
        if (dataSpeedInput) dataSpeedInput.disabled = true;
        if (topicInput) topicInput.disabled = true;
        if (kafkaServersInput) kafkaServersInput.disabled = true;
        
        // Показываем лоадер и сообщение об остановке
        if (continuousModal) {
            const modalTitle = continuousModal.querySelector('h3');
            const modalBody = continuousModal.querySelector('.continuous-stats');
            const modalFooter = continuousModal.querySelector('.modal-buttons');
            
            if (modalTitle) {
                modalTitle.innerHTML = '<div class="d-flex align-items-center"><div class="spinner-border spinner-border-sm text-primary me-2" role="status"></div>Остановка процесса...</div>';
            }
            if (modalBody) modalBody.style.opacity = '0.5';
            if (modalFooter) modalFooter.style.display = 'flex';
        }
    } else if (isRunning) {
        if (stopButton) stopButton.disabled = false;
        if (startButton) startButton.disabled = true;
        if (clearButton) clearButton.disabled = true;
        if (fileInput) fileInput.disabled = true;
        if (speedInput) speedInput.disabled = true;
        if (dataSpeedInput) dataSpeedInput.disabled = true;
        if (topicInput) topicInput.disabled = true;
        if (kafkaServersInput) kafkaServersInput.disabled = true;
        
        // Восстанавливаем нормальный вид модального окна
        if (continuousModal) {
            const modalTitle = continuousModal.querySelector('h3');
            const modalBody = continuousModal.querySelector('.continuous-stats');
            const modalFooter = continuousModal.querySelector('.modal-buttons');
            
            if (modalTitle) modalTitle.textContent = 'Статус записи';
            if (modalBody) modalBody.style.opacity = '1';
            if (modalFooter) modalFooter.style.display = 'flex';
        }
    } else {
        if (stopButton) stopButton.disabled = true;
        if (startButton) startButton.disabled = false;
        if (clearButton) clearButton.disabled = false;
        if (fileInput) fileInput.disabled = false;
        if (speedInput) speedInput.disabled = false;
        if (dataSpeedInput) dataSpeedInput.disabled = false;
        if (topicInput) topicInput.disabled = false;
        if (kafkaServersInput) kafkaServersInput.disabled = false;
        
        // Восстанавливаем нормальный вид модального окна
        if (continuousModal) {
            const modalTitle = continuousModal.querySelector('h3');
            const modalBody = continuousModal.querySelector('.continuous-stats');
            const modalFooter = continuousModal.querySelector('.modal-buttons');
            
            if (modalTitle) modalTitle.textContent = 'Статус записи';
            if (modalBody) modalBody.style.opacity = '1';
            if (modalFooter) modalFooter.style.display = 'flex';
        }
    }
}

// Функции для работы с настройками Kafka
async function saveKafkaConfig() {
    const bootstrapServers = document.getElementById('kafkaBootstrapServers')?.value;
    const topic = document.getElementById('kafkaTopic')?.value;
    const username = document.getElementById('kafkaUsername')?.value;
    const password = document.getElementById('kafkaPassword')?.value;

    if (!bootstrapServers) {
        showMessage('Введите адрес сервера Kafka');
        return;
    }

    try {
        disableButtons();
        
        const config = {
            bootstrapServers,
            topic: topic || '',  // Убедимся, что топик не null
            username,
            password
        };

        console.log('Сохраняем конфигурацию:', config);

        const response = await fetch('/api/kafka/config', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(config)
        });

        const data = await response.json();
        console.log('Ответ сервера:', data);
        
        if (data.success) {
            showMessage('Настройки успешно сохранены');
            // Обновляем список топиков только после успешного сохранения конфигурации
            await loadExistingTopics();
            
            // Восстанавливаем выбранный топик после обновления списка
            if (topic && topicChoices) {
                topicChoices.setChoiceByValue(topic);
            }
        } else {
            showMessage(data.message || 'Ошибка при сохранении настроек', 'error');
        }
    } catch (error) {
        console.error('Ошибка при сохранении настроек:', error);
        showMessage('Ошибка при сохранении настроек: ' + error.message, 'error');
    } finally {
        enableButtons();
    }
}

// Функция для блокировки кнопок
function disableButtons() {
    const buttons = [
        'testKafkaConnection',
        'saveKafkaConfig',
        'startContinuousBtn',
        'stopContinuousBtn'
    ];
    
    buttons.forEach(buttonId => {
        const button = document.getElementById(buttonId);
        if (button) {
            button.disabled = true;
        }
    });
}

// Функция для разблокировки кнопок
function enableButtons() {
    const buttons = [
        'testKafkaConnection',
        'saveKafkaConfig',
        'startContinuousBtn',
        'stopContinuousBtn'
    ];
    
    buttons.forEach(buttonId => {
        const button = document.getElementById(buttonId);
        if (button) {
            button.disabled = false;
        }
    });
}

async function testKafkaConnection() {
    const bootstrapServers = document.getElementById('kafkaBootstrapServers')?.value;
    const topic = document.getElementById('kafkaTopic')?.value;
    const username = document.getElementById('kafkaUsername')?.value;
    const password = document.getElementById('kafkaPassword')?.value;

    console.log('Значения из формы:', {
        bootstrapServers,
        topic,
        username,
        password
    });

    if (!bootstrapServers || bootstrapServers.trim() === '') {
        showMessage('Введите адрес сервера Kafka', 'error');
        return;
    }

    try {
        // Блокируем кнопки перед проверкой
        disableButtons();
        showMessage('Проверка подключения...', 'info');

        const requestBody = {
            bootstrapServers: bootstrapServers.trim(),
            topic: topic ? topic.trim() : null,
            username: username ? username.trim() : null,
            password: password ? password.trim() : null
        };

        console.log('Отправляем запрос с данными:', requestBody);

        const response = await fetch('/api/kafka/check-connection', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(requestBody)
        });

        const result = await response.json();
        console.log('Получен ответ:', result);
        
        if (result.success) {
            showMessage(result.message, 'success');
            // Сохраняем конфигурацию только если подключение успешно
            await saveKafkaConfig();
        } else {
            showMessage(result.message || 'Ошибка при проверке подключения', 'error');
        }
    } catch (error) {
        console.error('Ошибка при проверке подключения:', error);
        showMessage('Ошибка при проверке подключения: ' + error.message, 'error');
    } finally {
        // Разблокируем кнопки после завершения проверки
        enableButtons();
    }
}

// Загрузка текущих настроек при старте
async function loadKafkaConfig() {
    try {
        const response = await fetch('/api/kafka/config');
        if (!response.ok) {
            throw new Error('Ошибка при загрузке настроек');
        }

        const config = await response.json();
        
        document.getElementById('kafkaBootstrapServers').value = config.bootstrapServers || '';
        document.getElementById('kafkaTopic').value = config.topic || '';
        document.getElementById('kafkaUsername').value = config.username || '';
        document.getElementById('kafkaPassword').value = config.password || '';
        
        // Загружаем список существующих топиков только если есть bootstrap servers
        if (config.bootstrapServers) {
            await loadExistingTopics();
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
    }
}

// Инициализация при загрузке страницы
function initializeApp() {
    console.log('Инициализация приложения...');
    
    // Инициализация Choices.js
    initializeChoices();
    
    // Загрузка конфигурации
    loadKafkaConfig();
    
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
    const refreshButton = document.querySelector('.refresh-topics');
    if (!refreshButton) {
        console.error('Кнопка обновления топиков не найдена');
        return;
    }
    
    refreshButton.classList.add('rotating');
    
    try {
        const response = await fetch('/api/kafka/topics');
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

        // Очищаем текущие опции
        const topicSelect = document.getElementById('kafkaTopic');
        if (!topicSelect) {
            console.error('Элемент выбора топика не найден');
            return;
        }
        
        topicSelect.innerHTML = '';
        
        // Добавляем пустую опцию
        const emptyOption = document.createElement('option');
        emptyOption.value = '';
        emptyOption.textContent = 'Выберите топик';
        topicSelect.appendChild(emptyOption);
        
        // Добавляем топики, если они есть
        if (Array.isArray(topics) && topics.length > 0) {
            topics.forEach(topic => {
                const option = document.createElement('option');
                option.value = topic;
                option.textContent = topic;
                topicSelect.appendChild(option);
            });
        }

        // Обновляем Choices.js
        if (topicChoices) {
            topicChoices.destroy();
        }
        initializeChoices();
    } catch (error) {
        console.error('Ошибка при загрузке топиков:', error);
        showMessage('Ошибка при загрузке топиков: ' + error.message, 'error');
    } finally {
        refreshButton.classList.remove('rotating');
    }
}

async function checkKafkaConnection() {
    const bootstrapServers = document.getElementById('kafkaBootstrapServers').value;
    const topic = document.getElementById('kafkaTopic').value;

    if (!bootstrapServers) {
        showMessage('Пожалуйста, укажите адрес bootstrap servers', 'error');
        return;
    }

    try {
        const response = await fetch('/api/kafka/check-connection', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                bootstrapServers: bootstrapServers,
                topic: topic || null
            })
        });

        if (!response.ok) {
            throw new Error('Ошибка при проверке подключения: ' + response.statusText);
        }

        const result = await response.json();
        
        if (result.success) {
            showMessage(result.message, 'success');
            // Обновляем конфигурацию после успешной проверки
            await saveKafkaConfig();
            // Автоматически обновляем список топиков
            await loadExistingTopics();
        } else {
            showMessage(result.message || 'Ошибка при проверке подключения', 'error');
        }
    } catch (error) {
        console.error('Ошибка:', error);
        showMessage('Ошибка при проверке подключения: ' + error.message, 'error');
    }
}

function togglePassword() {
    const passwordInput = document.getElementById('kafkaPassword');
    const toggleButton = document.querySelector('.toggle-password i');
    
    if (passwordInput.type === 'password') {
        passwordInput.type = 'text';
        toggleButton.classList.remove('fa-eye');
        toggleButton.classList.add('fa-eye-slash');
    } else {
        passwordInput.type = 'password';
        toggleButton.classList.remove('fa-eye-slash');
        toggleButton.classList.add('fa-eye');
    }
} 