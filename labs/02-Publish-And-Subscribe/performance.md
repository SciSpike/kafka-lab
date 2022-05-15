# [OPTIONAL] Performance

## Introduction

When you are consuming and producing messages to Kafka, you can adjust a set of paramters that may have a dramatic impact on performance.

In this lab, we'll play around with some of these parameters. 
However, it is imporant to note that performance is not everything. 
As we progress in this course, we'll be discussing the various tradeoffs that these paramters offers.

Howevrer, let's just focus on performance here.


## Timing the Runs

If you are running on Linux or Mac, you can simply preceed the command with the keyword `time`.

E.g.:

```shell
$ time java -jar target/MY.jar
```

You should see some timing paramters at end of the run. E.g.:

```sh
2.81s user 0.31s system 42% cpu 7.279 total
```

On Windows, things gets a bit more tricky. 

If you can run in PowerShell, , you can run your command using `Measure-Command`. E.g.:

```powershell
PS> Meassure-Command { java -jar target/MY.jar}
```

If not, here is a a batch file that you can use: 

```bat
@echo off
@setlocal

set start=%time%

:: Runs your command
cmd /c %*

set end=%time%
set options="tokens=1-4 delims=:.,"
for /f %options% %%a in ("%start%") do set start_h=%%a&set /a start_m=100%%b %% 100&set /a start_s=100%%c %% 100&set /a start_ms=100%%d %% 100
for /f %options% %%a in ("%end%") do set end_h=%%a&set /a end_m=100%%b %% 100&set /a end_s=100%%c %% 100&set /a end_ms=100%%d %% 100

set /a hours=%end_h%-%start_h%
set /a mins=%end_m%-%start_m%
set /a secs=%end_s%-%start_s%
set /a ms=%end_ms%-%start_ms%
if %ms% lss 0 set /a secs = %secs% - 1 & set /a ms = 100%ms%
if %secs% lss 0 set /a mins = %mins% - 1 & set /a secs = 60%secs%
if %mins% lss 0 set /a hours = %hours% - 1 & set /a mins = 60%mins%
if %hours% lss 0 set /a hours = 24%hours%
if 1%ms% lss 100 set ms=0%ms%

:: Mission accomplished
set /a totalsecs = %hours%*3600 + %mins%*60 + %secs%
echo command took %hours%:%mins%:%secs%.%ms% (%totalsecs%.%ms%s total)
```

Let's say you name the file above `timing.bat`. 
You can now run your command as follows: 

```sh
> timing java -jar target/My.jar
```

## The Flush Command

In the producer, we explicitly flush the messages every 100'th message. 

A few quesstions:

* What is the effect of commenting out this flush command? 
* Did you get a better performance? If so, why?

## The Producer and Consumer Properties

In both the producer lab and the consumer lab we have extenralized some of the Kafka properties.
There are a large number of possible paramters that can be set.
Some of these paramters have dramatic effect on performance.

### Read up on some selected producer paramters

You can read about the producer paramters here: https://docs.confluent.io/platform/current/installation/configuration/producer-configs.html

Read up on the following properties for the producer:

* `buffer.memory`
* `batch.size`
* `linger.ms`

Try to play with some of these paramters and measure the effect. 

### Read up on some selected consumer paramters

You can read about the consumer paramters here: https://docs.confluent.io/platform/current/installation/configuration/consumer-configs.html

Read up on the following properties for the consumer:

* `fetch.min.bytes`
* `max.partition.fetch.bytes`
* `fetch.max.bytes`
* `max.poll.records`
* `receive.buffer.bytes`

Play with some of these paramters and measure the effect
