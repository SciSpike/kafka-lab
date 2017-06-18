# Designing Topics and Partitions

## Introduction

In this exercise we will introduce you to a real world problem.

Some years back, the authors of this course was asked to design a system to track out-patients.

Out-patients is a term used for patients that were at one point admitted to a hospital, but for one reason or another have been moved back to their home but are still being monitored by the hospital.

In this case, we want to build a system that collects information from a set of devices installed in the home.

The devices could be:
- Blood pressure monitors
- Weight scales
- Electrocardiogram monitors
- Pacemaker data collectors
- Thermometers
- Pulse oximeter
- Etc.

The devices stream data to an edge server.
The edge servers contains software from the device manufacturers and produces events representing the values of the monitors.

We want to build some software that processes the data in various ways.
We are not quite sure what downstream processors may be interested in, but some know consumers of these events are:

- A nurse dashboard where a nurse may be remotely monitoring a set of patients
- A knowledge system that can react to patterns of events from each patient and alert doctors or other interested parties that a worrisome pattern has emerged
- A billing system that keeps track of the time a patient has been monitored and the devices used in the patient's home
- A system for service technicians to predict failure of devices for various device types

Our goal is to design a Kafka cluster that can handle the events being passed between the edge devices and deliver them to the various systems.

## Exercise

### Question 1: Is Kafka a good solution for this use-case?

Spend some time discussing if Kafka is a good solution for this use case.
Is there other possible solutions?

### Question 2: What topics?

How do we select which topics we would use for this use case.
What topics do you suggest we create?

### Question 3: Which partitions?

What would be the factors that you would use to design the partitions?
Suggest some partitions for your topics and justify why you selected these partitions.
