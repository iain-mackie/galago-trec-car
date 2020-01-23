# build docker
DOCKER_PATH="./Dockerfile_index"

echo "Trying to build docker image from:" $DOCKER_PATH;

if [ -f "$DOCKER_PATH" ]; then
  echo "file exits"
  sudo docker build -t iainmackie/galago-index -f $DOCKER_PATH .
  sudo docker run -it \
  	-v $PWD/shared/data/:/home/shared/data \
  	-v $PWD/shared/index/:/home/shared/index \
  	-e INPUT=/home/shared/data/paragraphs/dedup.articles-paragraphs.cbor \
    -e INDEX=/home/shared/index/galago-index \
  	iainmackie/galago-index

else
  echo "Error - path to file not found:" $DOCKER_PATH;
  exit 0;

fi