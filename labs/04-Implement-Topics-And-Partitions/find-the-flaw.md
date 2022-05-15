# Something is Wrong...

## Introduction

The lab with the device monitors have some serious logical flaws. 

Of course, we introduced these deliberately to illustrate the value of some of the next chapters, but for now, let's see if you can find the flaws.

## Process 

When building a messaging application on top of Kafka, you have to think through any possible failure scenarios. 

Here are some of the questions you should __always__ ask. 

1. What happens if the producing client goes down and there are messages in the buffer that has not been sent to Kafka?
2. What happens if a consumer goes down and comes back up again?
3. What happens if a consumer goes down and its partition is transferred to another consumer?

Think through the logical consequences of the above errors. 
Will our algorithm always produce the right result?

If not, how would you fix it?