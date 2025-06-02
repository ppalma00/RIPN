# RIPN
Java implementation of the RIPN framework

Cyber-Physical Systems require advanced modeling frameworks to handle their hybrid nature, real-time adaptability, and environmental interactions. The paper linked below introduces the Reactive Interpreted Petri Net (RIPN) approach, a novel extension of Petri nets that integrates a centralized knowledge repository, the BeliefStore, and supports the incorporation of Teleo-Reactive programs. RIPNs enable the modeling of both discrete and durative actions, providing a unified framework that combines formal analytical rigor with system adaptability. We propose a systematic method to integrate Teleo-Reactive programs into RIPNs, enabling seamless, goal-driven modeling and analysis together with the advantages of a goal-oriented approach. This framework supports rigorous analysis of properties such as safety, deadlock-freedom, and resource optimization. 

The implementation has been done in Java. You can run either a Petri net or a Teleo-Reactive program, or both, sharing a BeliefStore of knowledge. There is a main file for each of the three scenarios. You can change the frequency of the interpreter loop, the files for the output of the log. When there are events to be managed by the Petri net a Java Swing interface is offered to send events to the interpreter. The same for the Teleo-Reactive part regarding the sending of percepts to the BeliefStore.

see https://www.techrxiv.org/users/917165/articles/1289915-reactive-interpreted-petri-nets-a-unified-framework-for-dynamic-cyber-physical-system-modeling-2025

We are still working on improving the code to minimize errors.

Dependencies: 
org.mvel2
https://download.eclipse.org/oomph/simrel-orbit/nightly/N202306221213/archive/download.eclipse.org/oomph/simrel-orbit/nightly/N202306221213/index/org.mvel2_2.4.15.Final.html

