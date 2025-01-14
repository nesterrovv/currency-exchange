import React, { useState, useEffect } from 'react';
import { LineChart, Line, XAxis, YAxis, Tooltip, Legend } from 'recharts';
import { toast, ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

function App() {
  // Состояния
  const [currencyData, setCurrencyData] = useState([]);
  const [selectedCurrency, setSelectedCurrency] = useState('USD');
  const [filteredData, setFilteredData] = useState([]);
  const [orderBook, setOrderBook] = useState({ bids: [], asks: [] });
  const [orderbookTimeSeries, setOrderbookTimeSeries] = useState([]);

  // Поля формы заявок
  const [orderSide, setOrderSide] = useState('BUY');
  const [orderCurrency, setOrderCurrency] = useState('USD');
  const [orderVolume, setOrderVolume] = useState(1);
  const [userPrice, setUserPrice] = useState(''); // строка, чтобы обрабатывать ввод

  // SSE: курсы
  useEffect(() => {
    const eventSource = new EventSource('http://localhost:8080/api/currency');
    eventSource.onmessage = (e) => {
      if (e.data) {
        const dataObj = JSON.parse(e.data);
        setCurrencyData((prev) => [...prev, dataObj]);
      }
    };
    return () => {
      eventSource.close();
    };
  }, []);

  // SSE: стакан
  useEffect(() => {
    const eventSource = new EventSource('http://localhost:8080/api/orderbook');
    eventSource.onmessage = (e) => {
      if (e.data) {
        const dataObj = JSON.parse(e.data);
        setOrderBook(dataObj);

        // Определяем bestBid / bestAsk
        let bestBid = 0;
        if (dataObj.bids && dataObj.bids.length > 0) {
          bestBid = Math.max(...dataObj.bids.map(b => b.price));
        }
        let bestAsk = 0;
        if (dataObj.asks && dataObj.asks.length > 0) {
          bestAsk = Math.min(...dataObj.asks.map(a => a.price));
        }

        // Формируем точку для графика
        const now = new Date();
        const newPoint = {
          timestamp: now.toLocaleTimeString(),
          bestBid,
          bestAsk
        };
        setOrderbookTimeSeries((prev) => [...prev, newPoint]);
      }
    };
    return () => {
      eventSource.close();
    };
  }, []);

  // SSE: уведомления
  useEffect(() => {
    const eventSource = new EventSource('http://localhost:8080/api/notification');
    eventSource.onmessage = (event) => {
      if(event.data){
        const notification = JSON.parse(event.data);
        if (notification.percentage === 9999) {
          // Крупная сделка
          const message = `Крупная сделка по ${notification.currentCurrency} - Цена: ${notification.currentPrice.toFixed(2)}`;
          toast.warning(message, { position: 'top-right', autoClose: 5000 });
        } else {
          // Резкое изменение цены
          const message = `${notification.currentCurrency}: Цена ${notification.currentPrice.toFixed(2)} (Изменение: ${notification.percentage.toFixed(2)}%)`;
          toast.info(message, { position: 'top-right', autoClose: 5000 });
        }
      }
    };
    return () => {
      eventSource.close();
    };
  }, []);

  // Фильтруем данные графика для выбранной валюты
  useEffect(() => {
    const dataForCurrency = currencyData
        .filter((d) => d.currency === selectedCurrency)
        .map((d) => ({
          ...d,
          timestamp: new Date(d.timestamp).toLocaleTimeString(),
        }));
    setFilteredData(dataForCurrency);
  }, [selectedCurrency, currencyData]);

  // Обработчик отправки заявки
  const handleOrderSubmit = () => {
    const payload = {
      side: orderSide,
      currency: orderCurrency,
      volume: parseFloat(orderVolume),
    };

    // Если пользователь указал цену
    if (userPrice.trim() !== '') {
      payload.userPrice = parseFloat(userPrice);
    } else {
      payload.userPrice = null;
    }

    fetch('http://localhost:8080/api/order', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
        .then(res => res.json())
        .then(updatedOrderBook => {
          console.log('Updated OrderBook:', updatedOrderBook);
        })
        .catch(err => console.error('Error:', err));
  };

  // Рендер
  return (
      <div style={{ margin: '20px' }}>
        <ToastContainer />
        <h1>Курсы валют (реактивно)</h1>

        <div>
          <button onClick={() => setSelectedCurrency('USD')}>USD</button>
          <button onClick={() => setSelectedCurrency('EUR')}>EUR</button>
          <button onClick={() => setSelectedCurrency('CNY')}>CNY</button>
        </div>

        <h2>График курса: {selectedCurrency}</h2>
        <LineChart width={800} height={300} data={filteredData}>
          <XAxis dataKey="timestamp" />
          <YAxis />
          <Tooltip />
          <Legend />
          <Line
              type="monotone"
              dataKey="price"
              stroke="#8884d8"
              strokeWidth={2}
              dot={false}
              name="Price"
              isAnimationActive={false}
          />
        </LineChart>

        <h2>График лучшего Bid/Ask</h2>
        <LineChart width={800} height={300} data={orderbookTimeSeries}>
          <XAxis dataKey="timestamp" />
          <YAxis />
          <Tooltip />
          <Legend />
          <Line
              type="monotone"
              dataKey="bestBid"
              stroke="#82ca9d"
              strokeWidth={2}
              dot={false}
              name="Best Bid"
              isAnimationActive={false}
          />
          <Line
              type="monotone"
              dataKey="bestAsk"
              stroke="#ff7300"
              strokeWidth={2}
              dot={false}
              name="Best Ask"
              isAnimationActive={false}
          />
        </LineChart>

        <h2>Добавить заявку</h2>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <select value={orderSide} onChange={(e) => setOrderSide(e.target.value)}>
            <option value="BUY">BUY</option>
            <option value="SELL">SELL</option>
          </select>
          <select value={orderCurrency} onChange={(e) => setOrderCurrency(e.target.value)}>
            <option value="USD">USD</option>
            <option value="EUR">EUR</option>
            <option value="CNY">CNY</option>
          </select>
          <input
              type="number"
              value={orderVolume}
              onChange={(e) => setOrderVolume(e.target.value)}
              min="1"
              step="1"
              style={{ width: '80px' }}
              placeholder="Volume"
          />
          <input
              type="number"
              value={userPrice}
              onChange={(e) => setUserPrice(e.target.value)}
              placeholder="Price (optional)"
              style={{ width: '120px' }}
          />
          <button onClick={handleOrderSubmit}>Отправить</button>
        </div>

        <h2>Биржевой стакан (последний снимок)</h2>
        <div style={{ display: 'flex', gap: '30px' }}>
          <div>
            <h3>Bids</h3>
            <ul>
              {orderBook.bids &&
                  orderBook.bids.map((bid, i) => (
                      <li key={i}>
                        Price: {bid.price.toFixed(2)}, Vol: {bid.volume.toFixed(2)}
                      </li>
                  ))}
            </ul>
          </div>
          <div>
            <h3>Asks</h3>
            <ul>
              {orderBook.asks &&
                  orderBook.asks.map((ask, i) => (
                      <li key={i}>
                        Price: {ask.price.toFixed(2)}, Vol: {ask.volume.toFixed(2)}
                      </li>
                  ))}
            </ul>
          </div>
        </div>
      </div>
  );
}

export default App;
