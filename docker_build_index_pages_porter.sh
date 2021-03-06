# build docker
DOCKER_PATH="./Dockerfile_index_pages_porter"

echo "Trying to build docker image from:" $DOCKER_PATH;

if [ -f "$DOCKER_PATH" ]; then
  echo "file exits"
  sudo docker build -t iainmackie/galago-index-pages-porter:v1 -f $DOCKER_PATH .
  # sudo docker run -it \
  # 	-v $PWD/shared/data/:/home/shared/data \
  # 	-v $PWD/shared/index/:/home/shared/index \
  # 	-e INPUT=/home/shared/data/pages/unprocessedAllButBenchmark.Y2.cbor \
  #   -e INDEX=/home/shared/index/galago-index \
  #   -e GALAGOJOBDIR=home/shared/index/galago-tmp-pages \
  # 	iainmackie/galago-index-pages

else
  echo "Error - path to file not found:" $DOCKER_PATH;
  exit 0;

fi