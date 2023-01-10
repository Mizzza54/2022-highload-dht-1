# Отчет по третьей задаче

Тестировалась конфигурация только с двумя нодами.
В качестве алгоритма распределения данных использовалось consistent hashing, которое вырождается в
`Math.abs(hashCode(obj) % N)`, т.к. топология сети не меняется.

## Нагрузочное тестирование

Стрельба проводилась только по одной из нод для упрощения интерпретации результатов.

Тестировались две версии - одна использовала `java.net.http.HttpClient` для передачи внутренних запросов,
а другая - `one.nio.http.HttpClient` (`JavaHttpClient` и `OneNioHttpClient` соответственно).

Как и в прошлой задаче, тестирование производилось 6 потоками с 128 соединениями для простоты сравнения с предыдущей
задачей.

### PUT

#### `one.nio.http.HttpClient`

Сначала была стрельба небольшим числом запросов (10000 RPS),
чтобы найти референсные значения для низкой нагрузки:

```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   814.38us  377.90us   3.10ms   64.42%
    Req/Sec     1.75k   423.66     2.33k    79.10%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%  814.00us
 75.000%    1.09ms
 90.000%    1.30ms
 99.000%    1.71ms
 99.900%    2.02ms
 99.990%    2.34ms
 99.999%    2.91ms
100.000%    3.10ms

Requests/sec:   9959.16
```

То же самое для 100000 RPS: 

```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.74s   569.02ms   2.69s    59.06%
    Req/Sec    13.26k   414.15    13.63k    70.27%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    1.78s 
 75.000%    2.23s 
 90.000%    2.50s 
 99.000%    2.66s 
 99.900%    2.68s 
 99.990%    2.69s 
 99.999%    2.69s 
100.000%    2.70s 

Requests/sec:  85492.47
```

Нагрузку не держим и latency очень сильно выросла.
Попробуем 50000 RPS:

```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.03ms  489.42us  12.22ms   69.22%
    Req/Sec     8.76k   608.72    13.00k    81.12%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    1.01ms
 75.000%    1.34ms
 90.000%    1.62ms
 99.000%    2.12ms
 99.900%    4.68ms
 99.990%    6.95ms
 99.999%    8.14ms
100.000%   12.23ms

Requests/sec:  49812.62
```

Latency относительно малой нагрузки выросла, но не слишком сильно.
Запросы успевают обрабатываться.
Попробуем 70000 RPS:

```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     4.08ms   13.04ms 109.18ms   94.99%
    Req/Sec    12.29k     1.80k   23.70k    89.88%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    1.32ms
 75.000%    1.80ms
 90.000%    2.40ms
 99.000%   83.97ms
 99.900%  105.60ms
 99.990%  108.29ms
 99.999%  108.99ms
100.000%  109.25ms

Requests/sec:  69697.14
```

Последние перцентили latency выросли уже слишком сильно.
Поэтому будем считать 50000 RPS стабильной нагрузкой.

#### `java.net.http.HttpClient` 

Все примерно то же самое.

10000 RPS:
```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.97ms  456.94us   3.30ms   64.23%
    Req/Sec     1.75k   414.73     2.33k    79.04%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    0.96ms
 75.000%    1.29ms
 90.000%    1.60ms
 99.000%    1.98ms
 99.900%    2.35ms
 99.990%    3.13ms
 99.999%    3.28ms
100.000%    3.31ms

Requests/sec:   9956.73
```

С 50000 RPS не справляемся - теряем запросы и latency неприемлемый:
```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     8.26s     3.89s   14.93s    57.89%
    Req/Sec     6.11k   106.45     6.31k    66.67%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    8.33s 
 75.000%   11.59s 
 90.000%   13.66s 
 99.000%   14.79s 
 99.900%   14.90s 
 99.990%   14.92s 
 99.999%   14.93s 
100.000%   14.93s

Requests/sec:  37536.98
```

С 40000 RPS уже лучше, но все еще не справляемся:
```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.27s     1.12s    4.19s    61.02%
    Req/Sec     6.15k   172.67     6.35k    80.14%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    2.30s 
 75.000%    3.23s 
 90.000%    3.83s 
 99.000%    4.14s 
 99.900%    4.18s 
 99.990%    4.19s 
 99.999%    4.20s 
100.000%    4.20s

Requests/sec:  37083.87
```

С 30000 RPS справляемся:
```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.14ms  817.26us  22.48ms   89.01%
    Req/Sec     5.27k   351.75     7.55k    70.70%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    1.07ms
 75.000%    1.46ms
 90.000%    1.80ms
 99.000%    2.76ms
 99.900%   11.64ms
 99.990%   17.89ms
 99.999%   21.14ms
100.000%   22.50ms

Requests/sec:  29975.21
```

### GET

Перед стрельбой сервис был нагружен 1.2 Гб данных.
В каждой ноде была приблизительно половина данных, т.е. данные распределились равномерно, как и должно быть. 

#### `one.nio.http.HttpClient`

10000 RPS - с нагрузкой справляемся:
```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   840.28us  396.80us   4.42ms   64.87%
    Req/Sec     1.74k   413.75     2.33k    79.42%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%  833.00us
 75.000%    1.13ms
 90.000%    1.36ms
 99.000%    1.72ms
 99.900%    2.24ms
 99.990%    3.87ms
 99.999%    4.33ms
100.000%    4.42ms

Requests/sec:   9967.85
```

50000 RPS - с нагрузкой справляемся:
```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.98ms  456.03us   7.56ms   66.56%
    Req/Sec     8.76k   636.26    13.67k    83.16%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    0.98ms
 75.000%    1.31ms
 90.000%    1.54ms
 99.000%    1.91ms
 99.900%    3.86ms
 99.990%    6.70ms
 99.999%    7.32ms
100.000%    7.57ms

Requests/sec:  49825.38
```

70000 RPS - с нагрузкой технически справляемся, но последние перцентили latency уже высоки. 
```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.40ms    2.82ms  77.06ms   98.70%
    Req/Sec    12.30k     0.97k   27.80k    74.39%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    1.13ms
 75.000%    1.54ms
 90.000%    1.93ms
 99.000%    5.54ms
 99.900%   46.24ms
 99.990%   69.69ms
 99.999%   74.37ms
100.000%   77.12ms

Requests/sec:  69910.71
```

Будем считать 50000 RPS стабильной нагрузкой.

#### `java.net.http.HttpClient`

10000 RPS - с нагрузкой справляемся:
```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.96ms  586.68us  15.82ms   80.19%
    Req/Sec     1.74k   428.22     4.33k    78.95%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    0.94ms
 75.000%    1.26ms
 90.000%    1.53ms
 99.000%    1.98ms
 99.900%    9.29ms
 99.990%   13.76ms
 99.999%   15.10ms
100.000%   15.82ms

Requests/sec:   9959.79
```

50000 RPS - с нагрузкой не справляемся (20.8% запросов не выполняется, очень высокая latency):
```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     6.07s     3.40s   12.18s    58.73%
    Req/Sec     6.41k   336.57     7.95k    93.57%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    6.00s 
 75.000%    8.93s 
 90.000%   10.89s 
 99.000%   12.03s 
 99.900%   12.14s 
 99.990%   12.17s 
 99.999%   12.18s 
100.000%   12.19s

Requests/sec:  39575.45
```

40000 RPS - с нагрузкой не справляемся - запросы выполняются, но высокая latency:
```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   413.04ms  333.74ms 871.42ms   41.99%
    Req/Sec     6.91k   452.12     8.78k    70.67%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%  308.99ms
 75.000%  791.55ms
 90.000%  827.39ms
 99.000%  857.09ms
 99.900%  865.79ms
 99.990%  870.40ms
 99.999%  870.91ms
100.000%  871.93ms

Requests/sec:  39396.37
```

30000 RPS - с нагрузкой справляемся:
```
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.12ms  708.15us  22.90ms   83.03%
    Req/Sec     5.02k    26.30     5.24k    78.05%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    1.07ms
 75.000%    1.45ms
 90.000%    1.78ms
 99.000%    2.43ms
 99.900%    9.10ms
 99.990%   20.32ms
 99.999%   22.06ms
100.000%   22.91ms

Requests/sec:  29895.19
```

Будем считать 30000 RPS стабильной нагрузкой.

Увеличение числа потоков в http клиенте не улучшает ситуацию и для java слиента, и для one-nio клинета -
latency остается в пределах +- 10% для всех перцентилей.
Т.к. wrk2 работает на 6 потоков, нода, по которой идет стрельба, отправляет запросы на другую ноду в 3 потока.
Поэтому больше потоков в клиенте не дает прироста в производительности.

### Промежуточные результаты (нагрузочное тестирование)

По сравнению с реализацией без шардирования мы теряем в RPS и latency.
Latency падает из-за того, что нам нужно ходить в другие ноды, а ходить по сети - относительно долго,
т.к. запросы у нас легкие.
RPS падает, т.к. мы тратим ресурсы на передачу запросов другим нодам и на свои запросы остается меньше ресурсов.

Однако этот проигрыш не значит, что шардирование бесполезно - мы теперь можем хранить данные,
которые не помещаются на одну ноду, а также увеличиваем отказоустойчивость - если одна нода упадет,
то по крайней мере часть данных будет доступна.
Кроме того, с добавлением репликации мы можем понизить число походов в другие ноды, что может повысить
производительность.

Реализация на `java.net.http.HttpClient` не справляется с той нагрузкой, с которой справляется `one.nio.http.HttpClient`
(50k RPS).
Почему - узнаем в следующем разделе отчета.

## Профилирование

Heatmap'ы находятся в папке heatmaps.
Те, что с префиксом `19234` - нода, по которой идет стрельба, дальше я буду называть ее первой.

В каждой секции первый скриншот соответствует первой ноде, второй - второй ноде. 

### PUT

#### CPU

##### `one.nio.http.HttpClient`

![19234](images/19234_cpu_onenio_put.png)

По сравнению с первой задачей добавилось:
- 8% на `MyService.proxyRequest`: 6.1% на парковку в ожидании футуры и 1.8% на добавление задачи в пул. 
- 19% на лямбду в `OneNioHttpClient.proxyRequest`. Из них:
  - 2.5% на конвертацию пути запроса из обычного в internal.
    Если верить [ответу на stackoverflow](https://stackoverflow.com/questions/4285083/fastest-way-to-perform-a-lot-of-strings-replace-in-java),
    это можно улучшить, если использовать `org.apache.commons.lang.StringUtils`, т.к. эта имплементация более эффективна.
    Но добавлять лишнюю зависимость ради 2.5% не хочется.
  - Остальное - системные вызовы и ответы.
    Интересно, что на каждый запрос вызывается `setsockopt` и на это уходит 1% CPU.
    Это происходит, потому что `one.nio.http.HttpClient` выставляет timeout на каждый запрос заново.
    Это делать не обязательно, т.к. timeout устанавливается на сокет, а не на запрос и если timeout не меняется от запроса к запросу,
    то можно не делать системный вызов.

![19235](images/19235_cpu_onenio_put.png)

Профиль второй ноды похож на профиль из прошлого задания, есть только два существенных отличия:
- Больше времени уходит на парковку в очереди на обработку запросов,
  т.к. на одну ноду приходит меньше запросов, очередь чаще оказывается пустой.
- Добавились вызовы к `compaction`, т.к. значения в бд теперь больше
  (в put.lua отправляется `string.rep(value, 100)`, а не просто `value`).
  Более темные полосы на heatmap'e, т.е. более высокая нагрузка на CPU - это вызовы к `compaction`:
  ![compaction](images/compaction_selected.png)

Т.к. вторая нода принимает только internal запросы, то в ней нет валидации.
Судя по профилю, это не сэкономило нам много времени, т.к. в прошлой задаче валидация не занимала существенное время.

##### `java.net.http.HttpClient`

![19234](images/19234_cpu_javahttp_put.png)

В этом профиле мы видим одну из причин, почему реализация с `java.net.http.HttpClient` работает медленнее,
чем `one.nio.http.HttpClient`.

Добавилось следующее:
- 8% на `MyService.proxyRequest` (как и с клиентом one-nio), из них:
  - 3.65% на `sendAsync`
  - 3% на `get` футуры, т.е. park
  - 0.65% на парсинг URI - этого не хочется делать
  - 0.42% на конвертацию пути во внутренний
- 25% на внутренности http клиента: 10% на `AsyncSupply.run`
  , 9% на `SchedulableTask.run`
  , 8% на лямбду в `SocketTube`.
- 7% на синхронизацию в `ForkJoinPool`
- 6.7% на `Selector` и `SelectorManager` - это сильно больше, чем в реализации с one-nio.

Без погружения в код сложно понять, что именно происходит.
При этом погружение весьма буквальное, потому что глубина стектрейса там местами переваливает за 30.
Важно отметить, что этот код во многих ветках не упирается в системные вызовы, что и приводит к потерям в производительности -
немалую часть времени мы занимаемся неизвестно чем, в отличие от `one.nio.http.HttpClient`,
который тратит время практически только на `send`, `recv` и `setsockopt`.

Также стоит отметить, что нет вызова `setsockopt`, т.е. выставлять таймаут на каждый запрос действительно не нужно.

![19235](images/19235_cpu_javahttp_put.png)

В профиле второй ноды мы видим одно существенное отличие от профиля той же ноды с `one.nio.http.HttpClient`:
чтение из сокета занимает существенно больше времени - 15.5% против 3.7%.

Оказывается, что `java.net.http.HttpClient` отправляет пакеты немного по-другому:

![Пакет от java](images/packet_java.png)
![Пакет от one-nio](images/packet_nio.png)

Первый пакет от `java.net.http.HttpClient`, второй от `one.nio.http.HttpClient`.
Клиент java отправляет данные в отдельном пакете, хотя их можно было бы отправить вместе с заголовками,
как это делает клиент one-nio.
Таким образом, второй ноде приходится читать два пакета, а не один, что занимает больше времени.

Кроме того, java клиент пытается использовать http2 и отправляет заголовок с useragent'ом,
это тоже могло замедлить работу.

Если зумиться в `recv`, то можно заметить, что в случае с java клиентом
большая часть времени уходит на `__tcp_transmit_skb`, а при использовании one-nio клиента этого вызова нет в профиле.
Этот вызов отправляет ack пакеты.
Почему так происходит, я не понял - казалось бы, с какого клиента не пришли бы данные, ack все равно нужно отправлять.
Wireshark показал ack в обоих случаях и они были идентичными.

#### Lock

##### `one.nio.http.HttpClient`

![19234](images/19234_lock_onenio_put.png)

Добавился один лок - в очереди пула для http клиентов.

![19235](images/19235_lock_onenio_put.png)

По сравнению с предыдущей задачей lock в `one.nio.http.HttpSession` стал занимать больше времени,
т.к. ответы стали тоже больше.
В остальном изменений нет.

##### `java.net.http.HttpClient`

![19234](images/19234_lock_javahttp_put.png)

Добавилось множество локов, которые обслуживают внутренности http клиента:
`SequentialScheduler`, футура и `SelectorManager`.

![19235](images/19235_lock_javahttp_put.png)

Т.к. java клиент разделяет тело и заголовки на разные пакеты,
`HttpSession.handleParseRequest` вызывается из `processHttpBuffer` в двух разных местах, т.е. срабатывает `if` с комментарием
`// Resume consuming request body`.
Поэтому `HttpSession.processRead` приходит в лок в двух разных местах, что и видно на графике.

Доля семплов локов в `HttpSession` закономерно увеличилось вдвое по сравнению с предыдущей задачей.

#### Alloc

##### `one.nio.http.HttpClient`

![19234](images/19234_alloc_onenio_put.png)

Аллокации изменились по сравнению с предыдущей задачей, т.к. тело запроса стало больше,
поэтому аллокация в `startParsingRequestBody` увеличилась с 4% до 54%.

Также добавилась аллокация в `HttpClient.ResponseReader` - 12.5%.
Там создается новый буфер на каждый запрос, это можно не делать.

![19235](images/19235_alloc_onenio_put.png)

Аналогично, но без `HttpClient.ResponseReader`.

##### `java.net.http.HttpClient`

![19234](images/19234_alloc_javahttp_put.png)

Добавилось множество небольших аллокаций внутри http клиента.

![19235](images/19235_alloc_javahttp_put.png)

Нет изменений помимо увеличения размера запросов.

### GET

#### CPU

##### `one.nio.http.HttpClient`

![19234](images/19234_cpu_onenio_get.png)
![19235](images/19235_cpu_onenio_get.png)

Профиль такой же, как и в PUT за исключением того, что нет вызовов `compaction`.
Везде упираемся в системные вызовы, возможностей оптимизировать особо нет.

##### `java.net.http.HttpClient`

![19234](images/19234_cpu_javahttp_get.png)
![19235](images/19235_cpu_javahttp_get.png)

Профиль такой же, как и в PUT за исключением того, что нет вызовов `compaction`.

#### Lock

Все локи такие же, как и в PUT.

Для краткости отчета оставляю только ссылки на графики, т.к. в них нет ничего содержательного:
- `one.nio.http.HttpClient`
  - [первая нода](images/19234_lock_onenio_get.png)
  - [вторая нода](images/19235_lock_onenio_get.png)
- `java.net.http.HttpClient`
  - [первая нода](images/19234_lock_javahttp_get.png)
  - [вторая нода](images/19235_lock_javahttp_get.png)

#### Alloc

Все так же, как и в PUT.

Для краткости отчета оставляю только ссылки на графики, т.к. в них нет ничего содержательного:
- `one.nio.http.HttpClient`
  - [первая нода](images/19234_alloc_onenio_get.png)
  - [вторая нода](images/19235_alloc_onenio_get.png)
- `java.net.http.HttpClient`
  - [первая нода](images/19234_alloc_javahttp_get.png)
  - [вторая нода](images/19235_alloc_javahttp_get.png)

## Выводы

`one.nio.http.HttpClient` показал себя лучше, чем `java.net.http.HttpClient` во всех метриках.

Клиент java.net обладает большим количеством возможностей, например поддержкой HTTP/2,
но он написан так сложно, что он получается относительно не производительным.
Кроме того, он отправляет тело запроса отдельно от заголовков, что также бьет по перформансу.

Клиент one-nio простой: его работа упирается в системные вызовы, а не в 10 слоев абстракций.
Однако его все еще можно оптимизировать, например не выделять буфер на каждый запрос и
не выставлять таймаут сокета на каждый запрос.

Шардирование уменьшило производительность, т.к. ресурсы тратятся на перенаправление запросов в нужный шард.
Но шардирование дает другие преимущества - отказоустойчивость и возможность масштабирования.
Кроме того, часть потерянных ресурсов можно вернуть, используя протокол с меньшим оверхедом, чем HTTP.
Или по крайней мере используя простой клиент http, как клиент из one-nio.

Также надо учитывать, что тестирование происходило на одной машине, и запросы ходили по сети практически мгновенно
и надежно, чего нельзя ожидать от систем в проде.

В реальной системе также нужно использовать более сложную хеш-функцию, т.к. к `hashcode` просто подобрать коллизии.