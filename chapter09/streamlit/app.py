import os
import time
from datetime import datetime

import requests
import streamlit as st
from pinotdb import connect

import pandas as pd
import plotly.graph_objects as go


DELIVERY_SERVICE_API = "http://pizzashop:8080"
PINOT_BROKER = os.getenv("PINOT_BROKER", "pinot-broker")
PINOT_PORT = os.getenv("PINOT_BROKER_PORT", "8099")


def path_to_image_html(path):
    return f'<img src="{path}" width="60">'


@st.cache
def convert_df(input_df: pd.DataFrame):
    return input_df.to_html(escape=False, formatters={"image": path_to_image_html})


st.set_page_config(layout="wide")
st.title("All About That Dough Dashboard")

now = datetime.now()
dt_string = now.strftime("%d %B %Y %H:%M:%S")
st.write(f"Last update: {dt_string}")

conn = connect(host="pinot-broker", port=8099, path="/query/sql", scheme="http")
# conn = connect(host="pinot-controller", port=9000, path="/query/sql", scheme="http")
curs = conn.cursor()

pinot_available = False
try:
    curs.execute("select * from orders where ts > ago('PT2M')")
    if not curs.description:
        st.warning("Connected to Apache Pinot, but no orders imported", icon="⚠️")
    pinot_available = True if curs.description else False
except Exception as exc:
    st.warning(f"Exception: {exc}")


if pinot_available:
    # Data from `pizzashop` web app
    response = requests.get(f"{DELIVERY_SERVICE_API}/orders/overview2").json()
    st.write(response)

    # Data from Apache Pinot
    metric_1, metric_2, metric_3 = st.columns(3)

    result_1 = curs.execute(
        """
        select
          count(*) filter(where ts > ago('PT1M')) as events1Min,
          count(*) filter(where ts <= ago('PT1M') and ts > ago('PT2M')) as events1Min2Min,
          sum("price") filter(where ts > ago('PT1M')) as total1Min,
          sum("price") filter(where ts <= ago('PT1M') and ts > ago('PT2M')) as total1Min2Min
        from orders
        where ts > ago('PT2M')
        limit 1
    """
    )

    num_orders_1_min = 0
    num_orders_2_min = 0
    revenue_1_min = 0
    revenue_2_min = 0
    for row in result_1:
        num_orders_1_min = row[0]
        num_orders_2_min = row[1]
        revenue_1_min = row[2]
        revenue_2_min = row[3]

    with metric_1:
        st.metric(
            label="# of Orders",
            value=f"{num_orders_1_min:,}",
            delta=f"{num_orders_1_min - num_orders_2_min:,}",
        )

    with metric_2:
        st.metric(
            label="Revenue in ₹",
            value=f"{revenue_1_min:,.2f}",
            delta=f"{revenue_1_min - revenue_2_min:,.2f}",
        )

    avg_revenue_1_min = revenue_1_min / num_orders_1_min if num_orders_1_min != 0 else 0
    avg_revenue_2_min = revenue_2_min / num_orders_2_min if num_orders_2_min != 0 else 0

    with metric_3:
        metric_3.metric(
            label="Average order value in ₹",
            value=f"{avg_revenue_1_min:,.2f}",
            delta=f"{avg_revenue_1_min - avg_revenue_2_min:,.2f}",
        )

    plot_1, plot_2, plot_3 = st.columns(3)

    result_orders = curs.execute(
        """
        select
          $ts$MINUTE as minute,
          count(*) as count,
          sum(price) as revenue,
          (sum(price) / count(*)) as avg_order_value
        from orders
        where ts > ago('PT1H')
        group by minute
        order by minute desc
        limit 60  -- TODO: test limit 70 to see if `ts > ago('PT1H')` is working
    """
    )
    result_orders_list = result_orders.fetchall()
    df_orders = pd.DataFrame(
        result_orders_list,
        columns=["timestamp", "count", "revenue", "avg_order_value"],
    )
    df_orders["timestamp"] = pd.to_datetime(df_orders["timestamp"].str.replace('"', ""))

    fig_1 = go.FigureWidget(
        data=[
            go.Scatter(
                x=df_orders["timestamp"],
                y=df_orders["count"],
                mode="lines",
                line={"dash": "solid", "color": "green"},
            )
        ]
    )
    fig_1.update_layout(
        showlegend=False,
        title="Orders per minute",
        margin={"l": 0, "r": 0, "t": 40, "b": 0},
    )
    fig_1.update_yaxes(range=[0, df_orders["count"].max() * 1.1])
    plot_1.plotly_chart(fig_1, use_container_width=True)

    fig_2 = go.FigureWidget(
        data=[
            go.Scatter(
                x=df_orders["timestamp"],
                y=df_orders["revenue"],
                mode="lines",
                line={"dash": "solid", "color": "blue"},
            )
        ]
    )
    fig_2.update_layout(
        showlegend=False,
        title="Revenue in ₹ per minute",
        margin={"l": 0, "r": 0, "t": 40, "b": 0},
    )
    fig_2.update_yaxes(range=[0, df_orders["revenue"].max() * 1.1])
    plot_2.plotly_chart(fig_2, use_container_width=True)

    fig_3 = go.FigureWidget(
        data=[
            go.Scatter(
                x=df_orders["timestamp"],
                y=df_orders["avg_order_value"],
                mode="lines",
                line={"dash": "solid", "color": "red"},
            )
        ]
    )
    fig_3.update_layout(
        showlegend=False,
        title="Average order value in ₹ per minute",
        margin={"l": 0, "r": 0, "t": 40, "b": 0},
    )
    fig_3.update_yaxes(range=[0, df_orders["avg_order_value"].max() * 1.1])
    plot_3.plotly_chart(fig_3, use_container_width=True)

    ###
    # Popular orders & categories tables
    ###

    response = requests.get(f"{DELIVERY_SERVICE_API}/orders/popular").json()
    popular_table_1, popular_table_2 = st.columns(2)

    with popular_table_1:
        st.subheader("Most popular items")
        popular_items = response["items"]
        df = pd.DataFrame(popular_items)
        df["quantityPerOrder"] = df["quantity"] / df["orders"]
        html = convert_df(df.sort_index())
        st.markdown(html, unsafe_allow_html=True)

    with popular_table_2:
        st.subheader("Most popular categories")
        popular_categories = response["categories"]
        df = pd.DataFrame(popular_categories)
        df["quantityPerOrder"] = df["quantity"] / df["orders"]
        html = convert_df(df.sort_index())
        st.markdown(html, unsafe_allow_html=True)

    ###
    # Order statuses table
    ###

    response = requests.get(f"{DELIVERY_SERVICE_API}/orders/statuses").json()
    status_table_cols = [
        "status",
        "min",
        "percentile50",
        "avg",
        "percentile75",
        "percentile90",
        "percentile99",
        "max",
    ]
    status_ordering = [
        "PLACED_ORDER",
        "ORDER_CONFIRMED",
        "BEING_PREPARED",
        "BEING_COOKED",
    ]
    st.subheader("Order statuses overview")
    df = pd.DataFrame(response)[status_table_cols]
    df["status"] = pd.Categorical(df["status"], status_ordering)
    df = df.sort_values(by="status")
    html = convert_df(df)
    st.markdown(html, unsafe_allow_html=True)

    ###
    # Stuck orders table
    ###

    response = requests.get(
        f"{DELIVERY_SERVICE_API}/orders/stuck/BEING_COOKED/60000"
    ).json()
    st.subheader("Orders delayed while BEING_COOKED")
    stuck_orders_cols = ["id", "price", "ts", "timeInStatus"]
    df = pd.DataFrame(response)[stuck_orders_cols].head(5)
    html = convert_df(df)
    st.markdown(html, unsafe_allow_html=True)

    ###
    # Refresh options
    ###

    number = 2
    if "sleep_time" not in st.session_state:
        st.session_state.sleep_time = number

    if "auto_refresh" not in st.session_state:
        st.session_state.auto_refresh = True

    refresh_widget_1, _, _, _, _, _ = st.columns(6)

    with refresh_widget_1:
        auto_refresh = st.checkbox("Auto Refresh?", st.session_state.auto_refresh)

        if auto_refresh:
            number = st.number_input(
                "Refresh rate in seconds", value=st.session_state.sleep_time
            )
            st.session_state.sleep_time = number

    if auto_refresh:
        time.sleep(number)
        st.experimental_rerun()


######################
# Docker Build & Run #
######################
# docker build -f Dockerfile -t dashboard .
# docker run --network="host" dashboard
