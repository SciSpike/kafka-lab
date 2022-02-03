RC=${RC:-.gitignorerc}

curl -sL https://www.gitignore.io/api/$(head -1 $RC) >.gitignore
tail -n +2 $RC >>.gitignore
