# Branch-and-Cut-Algorithm-for-a-Dial-a-Ride-Problem
This is the implementation of the paper "A Branch-and-Cut Algorithm for the Dial-a-Ride Problem" from Jean-Francois Cordeau. The paper can be found [here](https://pdfs.semanticscholar.org/a047/2611e636eb8d7f4225affb9980a9cd3c2791.pdf).

**The algorithm is implemented using the CPLEX JAVA API.**

The main part of the implementation is in located in [model.java](https://github.com/grthor/Branch-and-Cut-for-a-DARP/blob/master/src/logic/model.java). This file contains all the constraints. The classes [truck.java](https://github.com/grthor/Branch-and-Cut-for-a-DARP/blob/master/src/logic/Truck.java) and [node.java](https://github.com/grthor/Branch-and-Cut-for-a-DARP/blob/master/src/logic/Node.java) contain the code for representing a vehicle transporting persons and a the code representing a node.
