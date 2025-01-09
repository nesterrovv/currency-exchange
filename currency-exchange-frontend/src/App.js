import React, { useState, useEffect } from 'react';
import { LineChart, Line, XAxis, YAxis, Tooltip, Legend } from 'recharts';

function App() {
  const [currencyData, setCurrencyData] = useState([]);
  const [selectedCurrency, setSelectedCurrency] = useState('USD');
  const [filteredData, setFilteredData] = useState([]);
  const [orderBook, setOrderBook] = useState({ bids: [], asks: [] });

  // Поля для формы заявок
  const [orderSide, setOrderSide] = useState('BUY');
  const [orderCurrency, setOrderCurrency] = useState('USD');
  const [orderVolume, setOrderVolume] = useState(1);

  // SSE: курсы валют
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
      }
    };
    return () => {
      eventSource.close();
    };
  }, []);

  // Фильтруем данные для графика по выбранной валюте
  useEffect(() => {
    const dataForCurrency = currencyData
      .filter((d) => d.currency === selectedCurrency)
      .map((d) => ({
        ...d,
        timestamp: new Date(d.timestamp).toLocaleTimeString(), // Читаемый формат времени
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
    fetch('http://localhost:8080/api/order', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    })
      .then(() => {
        console.log('Order sent:', payload);
      })
      .catch((err) => {
        console.error('Error:', err);
      });
  };

  return (
    <div style={{ margin: '20px' }}>
      <h1>Курсы валют (реактивно)</h1>

      {/* Переключатель валют */}
      <div>
        <button onClick={() => setSelectedCurrency('USD')}>USD</button>
        <button onClick={() => setSelectedCurrency('EUR')}>EUR</button>
        <button onClick={() => setSelectedCurrency('CNY')}>CNY</button>
      </div>

      {/* График курсов валют */}
      <LineChart width={800} height={400} data={filteredData}>
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
          isAnimationActive={false}
        />
      </LineChart>

      {/* Форма отправки заявки */}
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
        />
        <button onClick={handleOrderSubmit}>Отправить</button>
      </div>

      {/* Стакан */}
      <h2>Биржевой стакан</h2>
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
