## Aliases (optional)

Because we use docker and docker-compose, the commands to run the kafka CLI can be tedious to type.

We have all of the commands listed in the exercise below, so you can simply copy and paste, but as you get more
advanced, you may want to experiment with the CLI.

One way to make this simpler is to alias your commands. When we run the Kafka commands in the running docker container,
we reach into the container and run a command.

This means that all of our commands are preceded with the following prefix:
`docker-compose exec kafka`

You may want to alias these commands. In Linux and Mac, you can simply create aliases in your terminal.

```
alias kt='docker-compose exec kafka kafka-topics.sh --bootstrap-server :9092'
alias kp='docker-compose exec kafka kafka-console-producer.sh --bootstrap-server :9092'
alias kc='docker-compose exec kafka kafka-console-consumer.sh --bootstrap-server :9092'
```

If you use `bash` and want these aliases to always be available, paste the above into your `~/.bash_profile`
or `~/.bashrc` file.

When you start new shells, you can now simply run:

```bash
$ kt {OPTIONS} command
```

To update your current shell (so that you don't have to close your terminal and start a new one), run:

```bash
$ source ~/.bash_profile
```

Another alternative is to run a bash shell inside the Docker container:

```bash
$ docker-compose exec kafka /usr/bin/env bash

bash-4.3#
```

You are now running inside the container and all the commands should work (and autocomplete).

