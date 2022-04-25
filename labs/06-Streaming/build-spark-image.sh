set -ex

rm -rf bitnami-docker-spark-master master.zip
docker run --rm -it -v $PWD:/pwd -w /pwd bash wget https://github.com/bitnami/bitnami-docker-spark/archive/refs/heads/master.zip
docker run --rm -it -v $PWD:/pwd -w /pwd bash unzip master.zip
cat bitnami-docker-spark-master/3/debian-10/Dockerfile | docker run --rm -i -v $PWD:/pwd -w /pwd bash sed -E -e 's/"java"\s+".*"\s+--checksum\s+[0-9a-f]+$/"java" "11.0.12-0" --checksum 14c1274e93b1135d4d1b82ad7985fc2fa7f5d0689b6da18e0c94da37407cd4ea/' > bitnami-docker-spark-master/3/debian-10/java11.Dockerfile
docker build -t bitnami/spark:3-java11 -f bitnami-docker-spark-master/3/debian-10/java11.Dockerfile bitnami-docker-spark-master/3/debian-10
