# Patient monitoring

## Introduction

In the design exercise we asked you to define topics and partitions for a remote patient monitoring system. Everyone of you may have created different topics and partitions for this exercise.

We'll just suggest one solution and have you implement a small portion of the problem.

## The Concerns

The authors of this course built such a system some years back. We can (of course) not give you this code, but we wanted to highlight some of the concerns that we had to design for.

Here are some of these concerns:

- The site we designed was multi-tenant (several hospitals using the same system). We had to make sure that the data from one patient is sent to the correct hospital/doctor.
- The data had very different shape and frequency based on the kind of device (e.g., a weight scale is quite different from an EKG)
- Nurses want to see data from a subset of patients

## Making it simple

We have limited time for this course, but we really want to give you a feeling for how you would take advantage of a tool like Kafka to implement a solution for this.

Let's make it simple and assume that we work on behalf of a single hospital. Let's also further simplify by assuming that we only keep track of a couple of types of devices.

Let's say we track:

- Weight scales
- Blood pressure monitor

What is interesting here is perhaps that while we may step on the weight scale once per day, we may have a blood pressure monitor that produce events once every 10 seconds.

Let's also give you some other metrics:

- We expect to monitor up to 100,000 patients
- We intent to build a UI for nurses where each nurse monitors about 1,000 patients each

  - From this follows that at any time we would have about 100 nurses monitoring the patents

Let's also assume some very simple rules that decide upon actions:

- When the blood pressure moves from normal to high, we want the nurse assigned to be alerted. We assume that a move to values above 150/100 should trigger the event for the nurse.

Let's also assume that each of the devices sends out a ping once per hour to say that they are healthy. We would also want to ensure that we monitor the devices and alert the service technicians if one of the devices does not call home.

## Suggested topics

Here are some suggested topics:

- Device installation

  - We will create one topic to manage device installations
  - Messages sent on this topic:

    - Device installed
    - Device uninstalled

- Patient observation

  - We will use this topic to send message from the scale and the blood pressure monitor. We can expect that we will see many more kinds of messages, but we want to use a single topic with polymorphic messages. The main reason for this is to ensure that we get the messages in order for a single patient no matter which device sent the message.
  - Message sent on this topic

    - Device event

      - Multiple shapes, but a common header
      - Header

        - Device id
        - Observation time
        - Message type

      - Body

        - Weight measurement

          - Weight in kilograms

        - Blood pressure

          - Pulse
          - Systolic pressure
          - Diastolic pressure

- Device heartbeat

  - This message would be a simple message that simply say "I'm on-line and working".

## What about partitions?

Since we're running a single node cluster in this exercise, we will not bother to implement the partitions. BTW, if you do want to play with a larger cluster, the docker image for Kafka actually supports that. You can simply tell `docker-compose` to scale out the Kafka cluster to more nodes (for this, run `docker-compose scale kafka=X`, where X is the number of nodes you want to run).

Partitions would be used primarily for controlling throughput.

We can imagine that the patient observations may require significant throughput. Let's say we start introducing devices that produce 100's of messages per second.

Ideally, we should try to process all events from a patient in order, however, that can turn out to be tricky in this case as each device may be streaming out events independently. Perhaps the best we can do is to ensure that the events from a single device are processed in order.

A typical design then would be:

- Measure the number of message that each Kafka node can process per second (e.g., say we use 10,000)
- Measure the number of messages we expect on the topic per second (e.g., say 100,000)
- Devide the numbers for expected messages and processing ability and we get at least 10 partitions. We probably want to have more than that, so perhaps we settle on 50 partitions.
- Since we want to make sure we process a single device stream in order, let's use the device id as the source for the partition key.

## Serialization / De-serialization

The serialization and de-serialization would depend somewhat of what we can control (the devices may have their own protocol and in the world of IoT, we often have to support many protocols. Common protocols are ASN.1, MQTT, JSON, XML, etc.).

Let's assume for our simple example that you can use JSON.

## Processor

We probably want at least two processors for this scenario

### Device monitor

This process is simple. It will simply read the heartbeats for each device.

The behavior would be something like this:

- If it has not heard from the device in 2 hours, it would produce a device-warning message.
- If it has not heard from the device in 3 hours, it would produce a device-failure message.
- If it hears from a device that is in a warning/error state, it produces a device-back-online message.

### Patient monitor

This processor listens to the device events and builds up a current picture of the patient vitals.

The processor would have to be supported by a database where we map the devices to individual patients.

Whenever the patient vitals change, it pushes an event to a new patient topic.

## Create the topics

Create the following topics:

- device-heartbeat
- device-events
- patient-vitals
- patient-alarm
- device-status

## Create the processors

### Heartbeat monitor

Create a processor that listens to the heartbeats and if you don't hear from a device within a specified period, then push an event to the device-status topic.

### Patient monitor

Create a processor that listens to the device events and if you see a message where the blood pressure is above a given limit, you push an event to the patient-alarm topic.
