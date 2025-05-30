// Функция для получения информации о топике
async function getTopicInfo(showError = true) {
    try {
        // Проверяем наличие настроек
        const configResponse = await fetch('/api/kafka/config');
        const config = await configResponse.json();
        
        if (!config.bootstrapServers) {
            if (showError) {
                showMessage('Сначала настройте подключение к Kafka');
            }
            return;
        }
        
        const response = await fetch('/api/kafka/topic/info');
        const data = await response.json();
        
        if (data.error) {
            if (showError) {
                showMessage(data.error);
            }
            return;
        }
        
        document.getElementById('topicName').textContent = data.topicName;
        document.getElementById('partitionCount').textContent = data.partitionCount;
        document.getElementById('totalMessages').textContent = data.totalMessages;
    } catch (error) {
        if (showError) {
            showMessage('Ошибка при получении информации о топике');
        }
    }
}

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
            // Обновляем информацию о топике через 3 секунды без показа ошибок
            setTimeout(() => {
                getTopicInfo(false);
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

        document.getElementById('startContinuousBtn').style.display = 'none';
        document.getElementById('stopContinuousBtn').style.display = 'inline-block';
        showContinuousModal();
        startStatusUpdates();
    } catch (error) {
        console.error('Ошибка:', error);
        showMessage('Ошибка при запуске записи: ' + error.message);
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
        const response = await fetch('/api/writer/stop', {
            method: 'POST'
        });

        if (!response.ok) {
            throw new Error('Ошибка при остановке записи');
        }

        const metrics = await response.json();
        console.log('Получены метрики:', metrics);
        
        // Рассчитываем средние значения на основе собранной истории
        const avgDataSpeed = dataSpeedHistory.length > 0 
            ? dataSpeedHistory.reduce((sum, speed) => sum + speed, 0) / dataSpeedHistory.length 
            : 0;
            
        const avgMessagesSpeed = messagesHistory.length > 0
            ? messagesHistory.reduce((sum, speed) => sum + speed, 0) / messagesHistory.length
            : 0;
        
        // Добавляем средние значения в метрики
        metrics.avgDataSpeed = avgDataSpeed;
        metrics.avgMessagesSpeed = avgMessagesSpeed;
        metrics.avgSpeed = avgMessagesSpeed; // Добавляем среднюю скорость сообщений
        
        updateMetrics(metrics);
        
        // Очищаем историю
        dataSpeedHistory = [];
        messagesHistory = [];

        // Не скрываем модальное окно сразу, оно закроется после полной остановки
        const startButton = document.getElementById('startContinuousBtn');
        const stopButton = document.getElementById('stopContinuousBtn');
        const clearButton = document.getElementById('clearContinuousFileBtn');
        
        if (startButton) startButton.style.display = 'inline-block';
        if (stopButton) stopButton.style.display = 'none';
        if (clearButton) clearButton.style.display = 'inline-block';
        
        stopStatusUpdates();
        getTopicInfo();
        
        // Скрываем модальное окно подсчета метрик
        hideCalculatingMetricsModal();
    } catch (error) {
        console.error('Ошибка:', error);
        showMessage('Ошибка при остановке записи: ' + error.message);
        hideCalculatingMetricsModal();
    }
}

function startStatusUpdates() {
    statusUpdateInterval = setInterval(updateContinuousStatus, 1000);
}

function stopStatusUpdates() {
    if (statusUpdateInterval) {
        clearInterval(statusUpdateInterval);
    }
}

async function updateContinuousStatus() {
    try {
        const response = await fetch('/api/writer/metrics');
        if (!response.ok) {
            throw new Error('Ошибка при получении статуса');
        }

        const status = await response.json();
        
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
            stopStatusUpdates();
            hideContinuousModal();
            document.getElementById('minimizedContinuousStatus').style.display = 'none';
        }
    } catch (error) {
        console.error('Ошибка при обновлении статуса:', error);
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

// Обновление метрик
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

    // Обновляем метрики в модальном окне
    updateElementText('continuousStatus', isRunning ? 'Активна' : (isStopping ? 'Останавливается...' : 'Остановлена'));
    updateElementText('continuousFileName', metrics.fileName || '-');
    updateElementText('continuousMessagesSent', messagesSent.toLocaleString());
    updateElementText('continuousSpeed', currentSpeed);
    updateElementText('continuousDataSpeed', currentDataSpeed);

    // Обновляем метрики в свернутом окне
    updateElementText('minimizedSpeed', currentSpeed);
    updateElementText('minimizedDataSpeed', currentDataSpeed);

    // Обновляем метрики в основном окне
    updateElementText('totalTime', totalTime);
    updateElementText('avgSpeed', avgSpeed);
    updateElementText('avgDataSpeed', avgDataSpeed);
    updateElementText('kafkaLines', linesWritten.toLocaleString());
    updateElementText('fileSize', (fileSize / (1024 * 1024)).toFixed(2));

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
    const config = {
        bootstrapServers: document.getElementById('kafkaBootstrapServers')?.value || '',
        topic: document.getElementById('kafkaTopic')?.value || null,
        username: document.getElementById('kafkaUsername')?.value || '',
        password: document.getElementById('kafkaPassword')?.value || ''
    };

    try {
        const response = await fetch('/api/kafka/config', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(config)
        });

        if (!response.ok) {
            throw new Error('Ошибка при сохранении настроек');
        }

        showMessage('Настройки успешно сохранены', 'success');
        
        // Обновляем информацию о топике после сохранения настроек
        if (config.topic) {
            setTimeout(() => {
                getTopicInfo();
            }, 1000);
        }
    } catch (error) {
        console.error('Ошибка:', error);
        showMessage('Ошибка при сохранении настроек: ' + error.message, 'error');
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

    if (!bootstrapServers) {
        showMessage('Введите адрес сервера Kafka', 'error');
        return;
    }

    try {
        // Блокируем кнопки перед проверкой
        disableButtons();
        showMessage('Проверка подключения...', 'info');

        const response = await fetch('/api/kafka/check-connection', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                bootstrapServers,
                topic,
                username,
                password
            })
        });

        const result = await response.json();
        
        if (result.success) {
            showMessage(result.message, 'success');
            // Обновляем список топиков
            await loadExistingTopics();
            // Обновляем конфигурацию
            await saveKafkaConfig();
        } else {
            showMessage(result.message, 'error');
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
            // Обновляем информацию о топике, если он выбран
            if (topic) {
                await getTopicInfo();
            }
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