import json
import math
import os
import random
import time
import uuid
from datetime import datetime, timezone
from typing import Any, Dict

from kafka import KafkaProducer
from mysql.connector import connect, Error


# Config
ORDER_INTERVAL = 100
MYSQL_HOST = os.getenv("MYSQL_SERVER", "localhost")
MYSQL_PORT = os.getenv("MYSQL_PORT", "3306")
MYSQL_USER = os.getenv("MYSQL_USER", "mysqluser")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "mysqlpw")
KAFKA_HOST = os.getenv("KAFKA_BROKER_HOSTNAME", "localhost")
KAFKA_PORT = os.getenv("KAFKA_BROKER_PORT", "29092")
KAFKA_HOST_PORT = f"{KAFKA_HOST}:{KAFKA_PORT}"

MAGNITUDE_FACTOR = 2
CYCLES_PER_HOUR = 12
NON_ZERO_BUMP = 0.5


def create_new_order(users, product_prices) -> Dict[str, Any]:
    number_of_items = random.randint(1, 10)
    items = []
    for _ in range(0, number_of_items):
        product_price = random.choice(product_prices)
        purchase_quantity = random.randint(1, 5)
        items.append(
            {
                "productId": str(product_price[0]),
                "quantity": purchase_quantity,
                "price": product_price[1],
            }
        )

    user_ids = list(users.keys())
    user_id = random.choice(user_ids)
    prices = [item["quantity"] * item["price"] for item in items]
    total_price = round(math.fsum(prices), 2)

    return {
        "id": str(uuid.uuid4()),
        "createdAt": datetime.now(tz=timezone.utc).isoformat(),
        "userId": user_id,
        "price": total_price,
        "items": items,
        "deliveryLat": str(users[user_id][0]),
        "deliveryLon": str(users[user_id][1]),
    }


def main() -> None:
    """Run the script"""
    print(f"Kafka Broker: {KAFKA_HOST_PORT}")

    producer = KafkaProducer(
        bootstrap_servers=KAFKA_HOST_PORT,
        api_version=(7, 1, 0),
        value_serializer=lambda m: json.dumps(m).encode("utf-8"),
    )

    mysql_connect_kwargs = {
        "host": MYSQL_HOST,
        "port": MYSQL_PORT,
        "user": MYSQL_USER,
        "password": MYSQL_PASSWORD,
    }
    try:
        with connect(**mysql_connect_kwargs) as conn, conn.cursor() as curs:
            # print("Getting products for the `products` topic...")
            # curs.execute(
            #     """
            #     SELECT id, name, description, category, price, image
            #     FROM pizzashop.products;
            #     """
            # )
            # products = [
            #     {
            #         "id": str(row[0]),
            #         "name": row[1],
            #         "description": row[2],
            #         "category": row[3],
            #         "price": row[4],
            #         "image": row[5],
            #     }
            #     for row in curs
            # ]
            # for product in products:
            #     print(product["id"])
            #     producer.send(
            #         "products", key=product["id"].encode("utf-8"), value=product
            #     )
            # producer.flush()

            print("Getting users for the `users` topic...")
            curs.execute("SELECT id, lat, lon FROM pizzashop.users;")
            users = {row[0]: (row[1], row[2]) for row in curs}

            print("Getting product IDs and prices tuples...")
            curs.execute("SELECT id, price FROM pizzashop.products;")
            product_prices = [(row[0], row[1]) for row in curs]
            print(product_prices)

    except Error as err:
        users = {}
        product_prices = []
        print(err)

    events_processed = 0
    while True:
        order = create_new_order(users=users, product_prices=product_prices)
        producer.send("orders", key=order["id"].encode("utf-8"), value=order)

        events_processed += 1
        if events_processed % 100 == 0:
            print(
                f"{str(datetime.now(tz=timezone.utc))} Flushing after {events_processed} events"
            )
            producer.flush()

        # Steady stream
        sleep_time = (
            random.randint(int(ORDER_INTERVAL / 5), ORDER_INTERVAL) / ORDER_INTERVAL
        )
        time.sleep(sleep_time)

        # # Cyclical stream: https://www.desmos.com/calculator/zriwubbg5h
        # now_min = datetime.datetime.now().minute
        # sleep_time = (
        #     MAGNITUDE_FACTOR * math.cos(CYCLES_PER_HOUR * math.pi * now_min / 30)
        #     + MAGNITUDE_FACTOR
        #     + NON_ZERO_BUMP
        #     + random.uniform(-NON_ZERO_BUMP, NON_ZERO_BUMP)  # jitter
        # )
        # time.sleep(sleep_time)


if __name__ == "__main__":
    time.sleep(10)
    main()
