FROM python:3.9.9-bullseye

WORKDIR /workdir

COPY pyproject.toml .

ENV VIRTUAL_ENV=/opt/venv
RUN python3 -m venv $VIRTUAL_ENV
ENV PATH="${VIRTUAL_ENV}/BIN:${PATH}"

RUN pip install --upgrade pip
RUN pip install --no-cache-dir '.'

COPY . .

ENTRYPOINT ["python", "multiseeder.py"]
