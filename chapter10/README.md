# Chapter 5

## Up & Running

```shell
cd chapter05
```

```shell
docker compose build
```

```shell
docker compose up
```

Give it about 1-2 minutes for everything to fully spin up.


## Query Kafka Topics

### Topic `orders`

```shell
kcat -b localhost:29092 -t orders -C -c1 | jq '.'
```

### Topic `products`

```shell
kcat -b localhost:29092 -t products -C -c 1 | jq '.'
```

```shell
kcat -b localhost:29092 -t products -C -o 80 -c 1 | jq '.'
```


## Query Apache Pinot Table

### Query via the browser

Open a browser to http://localhost:9000. That should open a web UI to query Apache Pinot.

### Query via Pinot broker API

```shell
curl -H "Content-Type: application/json" -X POST \
   -d '{"sql":"select count(*) as count from orders"}' \
   http://localhost:8099/query/sql 2>/dev/null | jq '.resultTable.rows[0][0]'
```

```shell
curl -H "Content-Type: application/json" -X POST \
   -d '{"sql":"select * from orders limit 10"}' \
   http://localhost:8099/query/sql 2>/dev/null | jq '.resultTable'
```


### Query via Quarkus `pizzashop` app

```shell
while true; do; curl http://localhost:8080/orders/overview2 2>/dev/null | jq '.'; sleep 1; done;
```

Or open a browser to http://localhost:8080/orders/overview2
