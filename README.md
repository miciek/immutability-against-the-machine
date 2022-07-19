# Immutability Against The Machine

This repository contains the code example used in my talk: "Immutability Against The Machine". The code is a simplified version of the example from the ["Grokking Functional Programming"](https://michalplachta.com/book).

All the resources, slides, and links for this talk can be found [here](https://michalplachta.com/talks/#immutability-against-the-machine).

### Abstract

Immutability has taken over the software world. Programmers are using immutable values to make their products more maintainable. They are able to focus on higher-level architectural problems instead of hard-to-debug accidental mutations.

However, the software we write often needs to run on a real machine. It needs state, it needs to do many things at once using multiple threads, and it needs to acquire some resources like sockets or files. Moreover, it needs to clean after itself in any condition, even when things go awry, making sure it never leaks memory or resources. We may be tempted to say that solving these concerns requires some old-school mutations. But it doesn’t!

In this talk we will show a real-world application that uses state, multiple threads, and resources. We will use real data from an external Wikidata service, make sure we conform to the API limits, implement a cache, and make sure we release all unneeded connections along the way. Most importantly, all this is going to be modelled as immutable values!	
