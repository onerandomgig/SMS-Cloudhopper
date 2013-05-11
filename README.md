SMS-Cloudhopper
===============

A standalone java application that allows you to connect to a SMSC via SMPP using the twitter CloudHopper library. 
The application also integrates an embedded jetty web server and exposes a REST interface to submit and receive SMS messages.

Checkout this project, to connect to a SMSC and send and receive messages.

I have used Jetty as an Embedded Webserver, RESTEasy to expose a REST API to send and receive messages and the awesome Cloudhopper library to connect to the SMSC via SMPP.

All of this has been wired with Spring.


