# Starting Kafka with Docker

## Prerequisites

- Docker
- Docker Compose

The easiest way to install Docker and Docker Compose is to install Docker Desktop (for Mac and Windows). 
You can also easily install Docker and Docker Compose on Linux.

### Windows installation

Follow the instructions [here](https://docs.docker.com/desktop/install/windows-install/).

### Mac installation

Follow the instructions [here](https://docs.docker.com/desktop/install/mac-install/).

### Linux installation

Follow the instructions [here](https://docs.docker.com/desktop/install/linux-install/).

## Running the Docker Compose file

In this directory ($LAB_ROOT/docker) you will find a file called `docker-compose.yml`.

To start the Kafka cluster, run the following command in the terminal:

```bash
docker compose up
```
You can leave the terminal open while the Kafka cluster is running.

To shutdown the Kafka cluster, run the following command in the terminal:

```bash
docker compose down
```

## Troubleshooting

If you have issues running the docker compose file, you can try the following:

- Make sure Docker Desktop is running.
- Make sure you have the latest version of Docker Desktop.
- Make sure you have at least 4GB of RAM allocated to Docker.

### Volume corruption

If you experience volume corruption, you can try the following:

1. Find the name of your volume (typicall `docker_kafka_data`)
2. Delete the volume using the following command: `docker volume rm <volume_name>`
3. Run `docker compose up` again.
